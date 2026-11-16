package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.newx.models.resolvedNewXPostMediaModels
import app.crimera.patches.newx.models.newXPostMediaModelResolutionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.settings.injectReadWithDefault
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.cloneMutable
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.numberOfParameterRegisters
import app.morphe.patcher.util.numberOfParameterRegistersLogical
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val EXPECTED_WRITE_METHODS = 2

private data class GuardedPostField(
    val label: String,
    val field: FieldReference,
)

private data class FieldWrite(
    val guarded: GuardedPostField,
    val index: Int,
)

private fun isFieldWrite(
    instruction: Instruction,
    field: FieldReference,
): Boolean =
    instruction.opcode == Opcode.IPUT_OBJECT &&
        instruction.getReference<FieldReference>()?.toString() == field.toString()

/**
 * Groups one post-model method's guarded writes, requiring exactly one write per guarded field so a
 * caller can clear every gate without re-matching an already mutated method.
 */
private fun Match.guardedWrites(guardedFields: List<GuardedPostField>): List<FieldWrite> {
    val writes =
        guardedFields.map { guarded ->
            val indices =
                method.instructions.withIndex()
                    .filter { (_, instruction) -> isFieldWrite(instruction, guarded.field) }
                    .map { (index, _) -> index }
            FieldWrite(
                guarded = guarded,
                index =
                    requireExactlyOne(
                        label = "NewX ${guarded.label} write in $originalMethod",
                        candidates = indices,
                    ),
            )
        }
    return writes.sortedByDescending(FieldWrite::index)
}

@Suppress("unused")
val newXShowSensitiveMediaPatch =
    bytecodePatch(
        name = "NewX: Show sensitive media",
        description = "Shows sensitive and age-restricted media without confirmation in NewX.",
    ) {
        unsupportedOnMount()
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
            // Both server gates land on the contextual post model and suppress rendering the same
            // way: the timeline card selector and the media presenter only check for a non-null
            // field. Clearing them at their constructor writes un-gates the post for every reader,
            // so the guard is applied where the model is built rather than at each renderer.
            val guardedFields =
                listOf(
                    GuardedPostField(
                        label = "tweet interstitial",
                        field = mediaModels.contextualTweetInterstitialField,
                    ),
                    GuardedPostField(
                        label = "media-visibility results",
                        field = mediaModels.contextualMediaVisibilityResultsField,
                    ),
                )

            val writeMatches =
                Fingerprint(
                    definingClass = contextualPostDescriptor,
                    filters =
                        guardedFields.map { guarded ->
                            fieldAccess(
                                opcode = Opcode.IPUT_OBJECT,
                                definingClass = contextualPostDescriptor,
                                name = guarded.field.name,
                                type = guarded.field.type,
                            )
                        },
                ).scopedMatchAll()
            if (writeMatches.size != EXPECTED_WRITE_METHODS) {
                throw PatchException(
                    "Expected $EXPECTED_WRITE_METHODS NewX guarded post-model write methods, " +
                        "found ${writeMatches.size}: " +
                        writeMatches.joinToString { it.originalMethod.toString() },
                )
            }

            val writesByMatch =
                writeMatches.map { match -> match to match.guardedWrites(guardedFields) }
            writesByMatch.forEach { (match, writes) ->
                val originalMethod = match.method
                val originalRegisterCount =
                    originalMethod.implementation?.registerCount
                        ?: throw PatchException("NewX guarded post-model constructor has no implementation")
                val parameterRegisterCount = originalMethod.numberOfParameterRegisters
                val indexShift = originalMethod.numberOfParameterRegistersLogical
                val method =
                    originalMethod.cloneMutable(
                        additionalRegisters = parameterRegisterCount + writes.size * 2,
                    ).also { expandedMethod ->
                        match.classDef.methods.remove(originalMethod)
                        match.classDef.methods.add(expandedMethod)
                    }
                writes.forEachIndexed { ordinal, write ->
                    val writeIndex = write.index + indexShift
                    val fieldWrite =
                        method.instructions[writeIndex] as? TwoRegisterInstruction
                            ?: throw PatchException(
                                "NewX ${write.guarded.label} field write has no two-register layout: " +
                                    "${method.instructions[writeIndex]}",
                            )
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
                    // The old external label sat on the field write, which the injected read leaves
                    // directly behind the hook, so the branch skips the guard via
                    // `Target.Original`. Labels that reached the write stay there: the previous
                    // plain insertion never moved them either.
                    method.insertHook(
                        index = settingRead.nextIndex,
                        relocateBranchTargets = false,
                    ) {
                        ifEqz(settingRead.register, Target.Original)
                        constInt(fieldWrite.registerA, 0)
                    }
                }
            }
        }
    }
