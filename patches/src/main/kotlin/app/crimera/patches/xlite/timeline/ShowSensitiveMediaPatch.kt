package app.crimera.patches.xlite.timeline

import app.crimera.patches.xlite.models.resolvedXLitePostMediaModels
import app.crimera.patches.xlite.models.xLitePostMediaModelResolutionPatch
import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.injectReadWithDefault
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteToggle
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.numberOfParameterRegistersLogical
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXPECTED_MEDIA_WRITE_METHODS = 2
private const val EXPECTED_MEDIA_WRITES = 2

private data class MediaVisibilityWrite(
    val match: Match,
    val index: Int,
)

private fun isMediaVisibilityWrite(
    instruction: Instruction,
    field: FieldReference,
): Boolean =
    instruction.opcode == Opcode.IPUT_OBJECT &&
        instruction.getReference<FieldReference>()?.toString() == field.toString()

@Suppress("unused")
val xLiteShowSensitiveMediaPatch =
    bytecodePatch(
        name = "X-Lite: Show sensitive media",
        description = "Shows sensitive media without requiring confirmation in X-Lite.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)
        dependsOn(xLitePostMediaModelResolutionPatch)

        val showSensitiveMedia =
            xLiteToggle(
                id = "xlite.content.show_sensitive_media",
                category = Categories.CONTENT,
                strings = settingStrings("piko_xlite_show_sensitive_media"),
                order = 300,
                defaultValue = true,
            )

        execute {
            val mediaModels = resolvedXLitePostMediaModels()
            val contextualPostDescriptor = mediaModels.postModels.contextualPostDescriptor
            val mediaField = mediaModels.contextualMediaVisibilityResultsField
            val mediaWriteMatches =
                Fingerprint(
                    definingClass = contextualPostDescriptor,
                    filters =
                        listOf(
                            fieldAccess(
                                opcode = Opcode.IPUT_OBJECT,
                                definingClass = contextualPostDescriptor,
                                name = mediaField.name,
                                type = mediaField.type,
                            ),
                        ),
                ).scopedMatchAll()
            if (mediaWriteMatches.size != EXPECTED_MEDIA_WRITE_METHODS) {
                throw PatchException(
                    "Expected $EXPECTED_MEDIA_WRITE_METHODS X-Lite media-visibility write methods, " +
                        "found ${mediaWriteMatches.size}: " +
                        mediaWriteMatches.joinToString { it.originalMethod.toString() },
                )
            }

            val mediaWrites =
                mediaWriteMatches.flatMap { match ->
                    match.method.instructions.withIndex().filter { (_, instruction) ->
                        isMediaVisibilityWrite(instruction, mediaField)
                    }.map { (index, _) -> MediaVisibilityWrite(match, index) }
                }
            if (mediaWrites.size != EXPECTED_MEDIA_WRITES) {
                throw PatchException(
                    "Expected $EXPECTED_MEDIA_WRITES X-Lite media-visibility field writes, " +
                        "found ${mediaWrites.size}: $mediaField",
                )
            }

            mediaWrites.groupBy(MediaVisibilityWrite::match).values.forEach { writes ->
                val originalMethod = writes.first().match.method
                val originalRegisterCount =
                    originalMethod.implementation?.registerCount
                        ?: throw PatchException("X-Lite media-visibility constructor has no implementation")
                val parameterRegisterCount = originalMethod.numberOfParameterRegisters
                val indexShift = originalMethod.numberOfParameterRegistersLogical
                val method =
                    originalMethod.cloneMutable(
                        additionalRegisters = parameterRegisterCount + writes.size * 2,
                    ).also { expandedMethod ->
                        writes.first().match.classDef.methods.remove(originalMethod)
                        writes.first().match.classDef.methods.add(expandedMethod)
                    }
                writes.sortedByDescending(MediaVisibilityWrite::index).forEachIndexed { ordinal, write ->
                    val writeIndex = write.index + indexShift
                    val fieldWrite =
                        method.instructions[writeIndex] as? TwoRegisterInstruction
                            ?: throw PatchException(
                                "X-Lite media-visibility field write has no two-register layout: " +
                                    "${method.instructions[writeIndex]}",
                            )
                    val continuation = method.instructions[writeIndex]
                    // cloneMutable reserves these locals before the shifted parameter registers.
                    val settingRegister = originalRegisterCount + ordinal * 2
                    val defaultRegister = settingRegister + 1
                    val settingRead =
                        showSensitiveMedia.injectReadWithDefault(
                            method = method,
                            index = writeIndex,
                            defaultValue = true,
                            registerRange = settingRegister..defaultRegister,
                        )
                    val keepOriginalLabel = "piko_xlite_keep_media_visibility_write_$ordinal"
                    method.addInstructionsWithLabels(
                        settingRead.nextIndex,
                        """
                            if-eqz v${settingRead.register}, :$keepOriginalLabel
                            const/16 v${fieldWrite.registerA}, 0x0
                        """.trimIndent(),
                        ExternalLabel(keepOriginalLabel, continuation),
                    )
                }
            }
        }
    }
