package app.crimera.patches.xlite.timeline

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.returnVoidIfEnabled
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteToggle
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

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

@Suppress("unused")
val hideNewPostPillPatch =
    bytecodePatch(
        name = "X-Lite: Hide new-post pill",
        description = "Hides the new-post notification pill in X-Lite timelines.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        val hideNewPostPill =
            xLiteToggle(
                id = "xlite.timeline.hide_new_post_pill",
                category = Categories.TIMELINE,
                strings = settingStrings("piko_xlite_hide_new_post_pill"),
                order = 200,
                defaultValue = true,
            )

        execute {
            val matches = XLiteNewPostsPillFingerprint.matchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite new-post pill renderer, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }
            hideNewPostPill.returnVoidIfEnabled(matches.single().method, 0)
        }
    }
