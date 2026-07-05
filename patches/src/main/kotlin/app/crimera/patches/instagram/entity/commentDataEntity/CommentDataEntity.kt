/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.commentDataEntity

import app.crimera.patches.instagram.entity.decoder.CommentButtonOnClickFingerprint
import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.USER_MODEL_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.misc.comment.copyComment.CopyTextChatButtonToStringFingerprint
import app.crimera.utils.changeFirstString
import app.crimera.utils.changeStringAt
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.crimera.utils.methodExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode
import kotlin.properties.Delegates

var CHAT_CONTEXT_BUTTON_SUPER_CLASS: String by Delegates.notNull()
    private set

val commentDataEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of comment data",
    ) {
        dependsOn(decoderEntity)

        execute {
            CHAT_CONTEXT_BUTTON_SUPER_CLASS = CopyTextChatButtonToStringFingerprint.classDef.superclass.toString()

            CommentButtonOnClickFingerprint.apply {
                val commentShareClickStrIndex = stringMatches[1].index

                method.apply {

                    val ifEqzIndex = indexOfFirstInstruction(commentShareClickStrIndex, Opcode.IF_EQZ)
                    val commentTextField = getInstruction(ifEqzIndex - 1).fieldExtractor().name
                    GetTextExtension.changeFirstString(commentTextField)
                }
            }
            var commentObject: String
            var commentGifObject: String
            var commentMediaHelperClass = "fieldName"

            RandomGetCommentObjectMediaFingerprint.apply {
                commentObject = method.returnType

                method.apply {
                    instructions.filter { it.opcode == Opcode.NEW_INSTANCE }.firstOrNull {
                        val index = it.location.index
                        val prevOpcode = getInstruction(index - 1).opcode
                        if (prevOpcode == Opcode.CONST_4) {
                            val nextInvokeDirectIndex = indexOfFirstInstruction(index, Opcode.INVOKE_DIRECT)
                            commentMediaHelperClass =
                                extensionToClassName(getInstruction(nextInvokeDirectIndex).methodExtractor().definingClass)
                            true
                        } else {
                            false
                        }
                    }
                }

                classDef.methods.first { it.parameters.size == 1 }.apply {
                    commentGifObject = returnType

                    val lastIPutObjectInstruction = instructions.last { it.opcode == Opcode.IPUT_OBJECT }
                    val lastIPutObjectIndex = lastIPutObjectInstruction.location.index

                    val gifCreatorNameFieldName = lastIPutObjectInstruction.fieldExtractor().name
                    GetGifCreatorNameMediaExtension.changeFirstString(gifCreatorNameFieldName)

                    val webpUrlFieldName = getInstruction(lastIPutObjectIndex - 2).fieldExtractor().name
                    GetWebpUrlMediaExtension.changeFirstString(webpUrlFieldName)

                    val gifUrlFieldName = getInstruction(lastIPutObjectIndex - 3).fieldExtractor().name
                    GetGifUrlMediaExtension.changeFirstString(gifUrlFieldName)

                    val gifTagFieldName = getInstruction(indexOfFirstInstruction(Opcode.IPUT) - 1).fieldExtractor().name
                    GetGifTagMediaExtension.changeFirstString(gifTagFieldName)
                }
            }

            val commentObjectFields = mutableClassDefBy { it.type == commentObject }.fields

            val commentMediaHelperFieldName = commentObjectFields.first { it.type == commentMediaHelperClass }.name
            GetImageMediaExtension.changeFirstString(commentMediaHelperFieldName)

            val gifObjectFieldFromCommentObject = commentObjectFields.first { it.type == commentGifObject }.name
            GetGifMediaExtension.changeFirstString(gifObjectFieldFromCommentObject)

            val commentUserFieldName = commentObjectFields.first { it.type == USER_MODEL_CLASS_NAME }.name
            GetCommentUserDataExtension.changeFirstString(commentUserFieldName)

            val commentMediaObjectFieldName =
                classDefBy { it.type == commentMediaHelperClass }
                    .fields
                    .first { it.type == MEDIA_CLASS_NAME }
                    .name
            GetImageMediaExtension.changeStringAt(1, commentMediaObjectFieldName)
        }
    }
