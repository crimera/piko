/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment.copyComment

import app.crimera.patches.instagram.entity.commentDataEntity.commentDataEntity
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.misc.comment.addButtonAttribute
import app.crimera.patches.instagram.misc.comment.addButtonInterface
import app.crimera.patches.instagram.misc.comment.addCommentPatch
import app.crimera.patches.instagram.misc.comment.commentButtonClickCheckPatch
import app.crimera.patches.instagram.misc.comment.nativeChatButtonResources
import app.crimera.patches.instagram.misc.comment.debugComment.debugCommentPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch

// Thanks to MyInsta.
@Suppress("unused")
val copyCommentPatch =
    bytecodePatch(
        name = "Copy comment",
        description = "Adds a button to copy comments on posts and reels.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(
            settingsPatch,
            addCommentPatch,
            commentButtonClickCheckPatch,
            commentDataEntity,
            decoderEntity,
            debugCommentPatch,
        )
        execute {

            val constructor = CopyTextChatButtonToStringFingerprint.classDef.methods.singleOrNull { it.name == "<init>" }
                ?: throw PatchException("Expected one native copy button constructor")
            val (stringLateral, drawableLateral) = nativeChatButtonResources(constructor)

            addButtonAttribute(
                stringLateral,
                drawableLateral,
                InitCopyButtonExtensionFingerprint,
                InitCopyButtonInitExtensionFingerprint,
            )

            addButtonInterface(InitCopyButtonExtensionFingerprint)

            enableSettings("copyCommentButton")
        }
    }
