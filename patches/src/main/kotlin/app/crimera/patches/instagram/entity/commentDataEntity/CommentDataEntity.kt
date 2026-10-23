/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.commentDataEntity

import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.USER_MODEL_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.misc.comment.copyComment.CopyTextChatButtonToStringFingerprint
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.patches.instagram.utils.methodReference
import app.crimera.utils.changeFirstString
import app.crimera.utils.changeStringAt
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import kotlin.properties.Delegates

var CHAT_CONTEXT_BUTTON_SUPER_CLASS: String by Delegates.notNull()
    private set

val commentDataEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of comment data",
    ) {
        dependsOn(decoderEntity)

        execute {
            CHAT_CONTEXT_BUTTON_SUPER_CLASS = CopyTextChatButtonToStringFingerprint.originalClassDef.superclass.toString()

            CommentShareClickFingerprint.let {
                val textField = it.method.getInstruction(it.instructionMatches.last().index - 1).getReference<FieldReference>()!!
                GetTextExtension.changeFirstString(textField.name)
            }

            val commentObject = RandomGetCommentObjectMediaFingerprint.originalMethod.returnType
            val commentMediaHelperClass = RandomGetCommentObjectMediaFingerprint.instructionMatches.first().methodReference.definingClass

            val commentGifObject = CommentGifConverterFingerprint.originalMethod.returnType
            CommentGifConverterFingerprint.instructionMatches.let {
                GetGifCreatorNameMediaExtension.changeFirstString(it[6].fieldReference.name)
                GetWebpUrlMediaExtension.changeFirstString(it[4].fieldReference.name)
                GetGifUrlMediaExtension.changeFirstString(it[3].fieldReference.name)
                GetGifTagMediaExtension.changeFirstString(it[0].fieldReference.name)
            }

            val commentObjectFields = classDefBy(commentObject).fields
            GetImageMediaExtension.changeFirstString(commentObjectFields.single { it.type == commentMediaHelperClass }.name)
            GetGifMediaExtension.changeFirstString(commentObjectFields.single { it.type == commentGifObject }.name)
            GetCommentUserDataExtension.changeFirstString(commentObjectFields.single { it.type == USER_MODEL_CLASS_NAME }.name)
            GetImageMediaExtension.changeStringAt(
                1,
                classDefBy(commentMediaHelperClass).fields.single { it.type == MEDIA_CLASS_NAME }.name,
            )
        }
    }
