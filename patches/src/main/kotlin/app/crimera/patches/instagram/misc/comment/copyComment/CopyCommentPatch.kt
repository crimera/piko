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
import app.crimera.patches.instagram.misc.comment.chatButtonConstructorFingerprint
import app.crimera.patches.instagram.misc.comment.commentButtonClickCheckPatch
import app.crimera.patches.instagram.misc.comment.debugComment.debugCommentPatch
import app.crimera.patches.instagram.misc.comment.nativeChatButtonResources
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.registersUsed

// Thanks to MyInsta.
@Suppress("unused")
val copyCommentPatch =
    bytecodePatch(
        name = "Copy comment",
        description = "Adds buttons to copy comments and their authors' usernames on posts and reels.",
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

            val (stringLateral, drawableLateral) = nativeChatButtonResources(
                chatButtonConstructorFingerprint(CopyTextChatButtonToStringFingerprint.originalClassDef.type),
            )

            addButtonAttribute(
                stringLateral,
                drawableLateral,
                InitCopyButtonExtensionFingerprint,
                InitCopyButtonInitExtensionFingerprint,
            )

            addButtonInterface(InitCopyButtonExtensionFingerprint)

            val stringClass = addButtonAttribute(
                stringLateral,
                drawableLateral,
                CopyUsernameButtonExtensionFingerprint,
                InitCopyUsernameButtonExtensionFingerprint,
                stringIdMethod = "$COPY_USERNAME_BUTTON_EXTENSION_CLASS->getLabelResourceId()I",
            )
            addButtonInterface(CopyUsernameButtonExtensionFingerprint)

            commentButtonLabelFingerprint(stringClass).matchSingle().let { match ->
                val getString = match.instructionMatches.last()
                val registers = getString.instruction.registersUsed
                val invoke = if (registers.all { it <= 15 }) {
                    "invoke-static {v${registers[0]}, v${registers[1]}}"
                } else {
                    "invoke-static/range {v${registers[0]} .. v${registers[1]}}"
                }
                match.method.replaceInstruction(
                    getString.index,
                    "$invoke, $COPY_USERNAME_BUTTON_EXTENSION_CLASS->getLabel(Landroid/content/Context;I)Ljava/lang/String;",
                )
            }

            enableSettings("copyCommentButton")
        }
    }
