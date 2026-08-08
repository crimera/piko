package app.crimera.patches.xlite.timeline

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.injectRead
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteToggle
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val CONTEXTUAL_POST_DESCRIPTOR = "Lcom/x/models/ContextualPost;"
private const val MEDIA_VISIBILITY_RESULTS_DESCRIPTOR =
    "Lcom/x/models/interstitial/MediaVisibilityResults;"

private object XLiteMediaVisibilityResultsFingerprint : Fingerprint(
    definingClass = CONTEXTUAL_POST_DESCRIPTOR,
    name = "getMediaVisibilityResults",
    parameters = emptyList(),
    returnType = MEDIA_VISIBILITY_RESULTS_DESCRIPTOR,
)

@Suppress("unused")
val xLiteShowSensitiveMediaPatch =
    bytecodePatch(
        name = "X-Lite: Show sensitive media",
        description = "Shows sensitive media without requiring confirmation in X-Lite.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        val showSensitiveMedia =
            xLiteToggle(
                id = "xlite.content.show_sensitive_media",
                category = Categories.CONTENT,
                strings = settingStrings("piko_xlite_show_sensitive_media"),
                order = 300,
                defaultValue = true,
            )

        execute {
            val matches = XLiteMediaVisibilityResultsFingerprint.matchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite media-visibility getter, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            val method = matches.single().method
            val returnInstructions =
                method.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }
            if (returnInstructions.size != 1) {
                throw PatchException(
                    "Expected one object return in X-Lite media-visibility getter, " +
                        "found ${returnInstructions.size}: $method",
                )
            }

            val returnInstruction = returnInstructions.single()
            val resultRegister =
                method.getInstruction<OneRegisterInstruction>(returnInstruction.index).registerA
            val settingRead =
                showSensitiveMedia.injectRead(
                    method = method,
                    index = returnInstruction.index,
                    excludedRegisters = listOf(resultRegister),
                )
            val keepOriginalLabel = "piko_xlite_keep_media_visibility_result"
            method.addInstructionsWithLabels(
                settingRead.nextIndex,
                """
                    if-eqz v${settingRead.register}, :$keepOriginalLabel
                    const/16 v$resultRegister, 0x0
                """.trimIndent(),
                ExternalLabel(keepOriginalLabel, returnInstruction.value),
            )
        }
    }
