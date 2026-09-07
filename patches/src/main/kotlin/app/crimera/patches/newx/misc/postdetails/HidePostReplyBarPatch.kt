package app.crimera.patches.newx.misc.postdetails

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.settings.returnVoidIfEnabled
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string

/**
 * The inline post-detail composer marks its text field with this stable Compose test tag. The
 * tag is inside the minimal-composer renderer, while the floating new-post action is rendered by
 * its caller, so returning from this renderer hides only the persistent reply bar.
 */
private object NewXPostDetailReplyBarFingerprint : Fingerprint(
    definingClass = "Lcom/x/composer/minimal/",
    returnType = "V",
    filters = listOf(string("post-detail-reply-text-field")),
)

@Suppress("unused")
val newXHidePostReplyBarPatch =
    bytecodePatch(
        name = "NewX: Hide post reply bar",
        description = "Hides the persistent post-detail reply bar while keeping the compose button available.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        val hidePostReplyBar =
            newXToggle(
                id = "newx.post_actions_media.hide_post_reply_bar",
                category = Categories.POST_ACTIONS_MEDIA,
                strings = settingStrings("piko_newx_hide_post_reply_bar"),
                order = 100,
                defaultValue = false,
            )

        execute {
            val matches = NewXPostDetailReplyBarFingerprint.scopedMatchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one NewX post-detail reply bar renderer, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }
            hidePostReplyBar.returnVoidIfEnabled(matches.single().method, 0)
        }
    }
