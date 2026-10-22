/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment.saveMediaComment

import app.crimera.patches.instagram.entity.commentDataEntity.commentDataEntity
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.entity.mediadata.mediaDataEntity
import app.crimera.patches.instagram.misc.comment.addButtonAttribute
import app.crimera.patches.instagram.misc.comment.addButtonInterface
import app.crimera.patches.instagram.misc.comment.addCommentPatch
import app.crimera.patches.instagram.misc.comment.chatButtonConstructorFingerprint
import app.crimera.patches.instagram.misc.comment.commentButtonClickCheckPatch
import app.crimera.patches.instagram.misc.comment.debugComment.debugCommentPatch
import app.crimera.patches.instagram.misc.comment.nativeChatButtonResources
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.fieldReference
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.matchSingle

@Suppress("unused")
val saveMediaCommentPatch =
    bytecodePatch(
        name = "Save media comment",
        description = "Adds a button to save media comments on posts and reels.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(
            settingsPatch,
            addCommentPatch,
            commentButtonClickCheckPatch,
            commentDataEntity,
            mediaDataEntity,
            decoderEntity,
            debugCommentPatch,
        )
        execute {

            val action = ChatButtonActionsFingerprint.matchSingle().instructionMatches.last().fieldReference
            val (stringLateral, drawableLateral) = nativeChatButtonResources(chatButtonConstructorFingerprint(action))

            addButtonAttribute(
                stringLateral,
                drawableLateral,
                InitSaveMediaButtonExtensionFingerprint,
                InitSaveMediaButtonInitExtensionFingerprint,
            )

            addButtonInterface(InitSaveMediaButtonExtensionFingerprint)

            enableSettings("saveMediaCommentButton")
        }
    }
