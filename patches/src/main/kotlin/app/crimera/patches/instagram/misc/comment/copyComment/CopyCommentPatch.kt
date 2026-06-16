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
import app.crimera.patches.instagram.misc.comment.debugComment.debugCommentPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31i

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
            resourceMappingPatch,
            decoderEntity,
            debugCommentPatch,
        )
        execute {

            var stringLateral: Long
            CopyTextChatButtonToStringFingerprint.classDef.methods.first { it.name == "<init>" }.apply {
                stringLateral = (instructions.last { it.opcode == Opcode.CONST } as Instruction31i).wideLiteral
            }

            var drawableLateral: Long = getResourceId(ResourceType.DRAWABLE, "instagram_copy_outline_24")

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
