package app.crimera.patches.instagram.misc.stories.disableStoryFlipping

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel


internal object StoryFlippingMethodFingerprint: Fingerprint(
    returnType = "V",
    definingClass = "Linstagram/features/stories/fragment/ReelViewerFragment;",
    strings = listOf("userSession"),
    parameters = listOf("Ljava/lang/Object;")
)

@Suppress("unused")
val disableStoryFlippingPatch =
    bytecodePatch(
        name = "Disable story flipping",
        description = "Disable automatic flipping/moving to next story",
    ) {
        dependsOn(settingsPatch)

        compatibleWith("com.instagram.android")

        execute {
            StoryFlippingMethodFingerprint.method.apply {
                addInstructionsWithLabels(0,"""
                    ${PREF_CALL_DESCRIPTOR}->disableStoryFlipping()Z
                    move-result v0
                    if-eqz v0, :piko
                    return-void
                """.trimIndent(),
                    ExternalLabel("piko", getInstruction(0))
                )
            }
            enableSettings("disableStoryFlipping")
        }
    }
