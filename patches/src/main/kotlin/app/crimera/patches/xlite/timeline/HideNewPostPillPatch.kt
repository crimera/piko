package app.crimera.patches.xlite.timeline

import app.crimera.patches.xlite.settings.injectBooleanRead
import app.crimera.patches.xlite.settings.toggleSetting
import app.crimera.patches.xlite.settings.xLiteSettingsContributionPatch
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.morphe.extension.xlite.api.XLiteSettings.Categories
import app.morphe.extension.xlite.api.XLiteSettings.Keys
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel

private object XLiteNewPostsPillFingerprint : Fingerprint(
    parameters =
        listOf(
            "Lcom/x/models/timelines/URTTimelineInstruction\$ShowInstructions\$TimelineShowAlert;",
            "L",
            "Landroidx/compose/ui/Modifier;",
            "Lkotlin/jvm/functions/Function0;",
            "Landroidx/compose/runtime/Composer;",
            "I",
        ),
    returnType = "V",
)

private val hideNewPostPill =
    toggleSetting(
        key = Keys.HIDE_NEW_POST_PILL,
        titleResourceName = "piko_xlite_hide_new_post_pill_title",
        summaryResourceName = "piko_xlite_hide_new_post_pill_summary",
        order = 200,
        defaultValue = true,
    )

private val hideNewPostPillSettingsPatch =
    xLiteSettingsContributionPatch {
        category(Categories.TIMELINE) {
            add(hideNewPostPill)
        }
    }

@Suppress("unused")
val hideNewPostPillPatch =
    bytecodePatch(
        name = "X-Lite: Hide new-post pill",
        description = "Hides the new-post notification pill in X-Lite timelines.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)
        dependsOn(hideNewPostPillSettingsPatch)

        execute {
            val matches = XLiteNewPostsPillFingerprint.matchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite new-post pill renderer, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }
            matches.single().method.apply {
                val originalFirstInstruction = instructions.first()
                val readInstructionCount = hideNewPostPill.injectBooleanRead(this, 0, 0)
                addInstructionsWithLabels(
                    readInstructionCount,
                    """
                        if-eqz v0, :piko_xlite_new_post_pill_continue
                        return-void
                    """.trimIndent(),
                    ExternalLabel("piko_xlite_new_post_pill_continue", originalFirstInstruction),
                )
            }
        }
    }
