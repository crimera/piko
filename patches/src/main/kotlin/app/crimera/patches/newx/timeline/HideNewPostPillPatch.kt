package app.crimera.patches.newx.timeline

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.returnVoidIfEnabled
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

private object NewXNewPostsPillFingerprint : Fingerprint(
    definingClass = "Lcom/x/urt/instructions/",
    parameters =
        listOf(
            "L",
            "L",
            "Landroidx/compose/ui/Modifier;",
            "Lkotlin/jvm/functions/Function0;",
            "Landroidx/compose/runtime/Composer;",
            "I",
        ),
    returnType = "V",
    filters = listOf(string("ntp")),
)

@Suppress("unused")
val hideNewPostPillPatch =
    bytecodePatch(
        name = "NewX: Hide new posts pill",
        description = "Hides the new posts pill in NewX timelines.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        val hideNewPostPill =
            newXToggle(
                id = "newx.timeline.hide_new_post_pill",
                category = Categories.TIMELINE,
                strings = settingStrings("piko_newx_hide_new_post_pill"),
                order = 200,
                defaultValue = true,
            )

        execute {
            val matches = NewXNewPostsPillFingerprint.scopedMatchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX new-post pill renderer, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }
            hideNewPostPill.returnVoidIfEnabled(matches.single().method, 0)
        }
    }
