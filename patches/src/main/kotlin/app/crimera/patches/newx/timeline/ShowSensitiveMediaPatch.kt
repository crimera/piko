package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.models.resolvedNewXPostMediaModels
import app.crimera.patches.newx.models.newXPostMediaModelResolutionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.injectReadWithDefault
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
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
val newXShowSensitiveMediaPatch =
    bytecodePatch(
        name = "NewX: Show sensitive media",
        description = "Shows sensitive media without requiring confirmation in NewX.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXPostMediaModelResolutionPatch)

        val showSensitiveMedia =
            newXToggle(
                id = "newx.content.show_sensitive_media",
                category = Categories.CONTENT,
                strings = settingStrings("piko_newx_show_sensitive_media"),
                order = 300,
                defaultValue = true,
            )

        execute {
            val mediaModels = resolvedNewXPostMediaModels()
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
                    "Expected $EXPECTED_MEDIA_WRITE_METHODS NewX media-visibility write methods, " +
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
                    "Expected $EXPECTED_MEDIA_WRITES NewX media-visibility field writes, " +
                        "found ${mediaWrites.size}: $mediaField",
                )
            }

            mediaWrites.groupBy(MediaVisibilityWrite::match).values.forEach { writes ->
                val originalMethod = writes.first().match.method
                val originalRegisterCount =
                    originalMethod.implementation?.registerCount
                        ?: throw PatchException("NewX media-visibility constructor has no implementation")
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
                                "NewX media-visibility field write has no two-register layout: " +
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
                    val keepOriginalLabel = "piko_newx_keep_media_visibility_write_$ordinal"
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
