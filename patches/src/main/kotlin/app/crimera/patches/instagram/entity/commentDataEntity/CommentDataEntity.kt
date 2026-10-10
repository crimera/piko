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
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
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
            var commentMediaHelperClass: String

            RandomGetCommentObjectMediaFingerprint.apply {
                commentObject = method.returnType

                commentMediaHelperClass = method.instructions.mapNotNull { instruction ->
                    instruction.getReference<MethodReference>()?.takeIf {
                        it.name == "<init>" && it.returnType == "V" && it.parameterTypes.size == 4 &&
                            it.parameterTypes.drop(1).map { type -> type.toString() } == listOf(
                                "Lcom/instagram/common/gallery/Medium;", MEDIA_CLASS_NAME, "Ljava/lang/String;",
                            )
                    }?.definingClass
                }.distinct().singleOrNull()
                    ?: throw PatchException("Expected one comment photo wrapper constructor")

                (classDef.methods.singleOrNull { it.parameters.size == 1 }
                    ?: throw PatchException("Expected one comment GIF converter")).apply {
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

            val commentMediaHelperFieldName = commentObjectFields.singleOrNull { it.type == commentMediaHelperClass }?.name
                ?: throw PatchException("Expected one comment photo wrapper field")
            GetImageMediaExtension.changeFirstString(commentMediaHelperFieldName)

            val gifObjectFieldFromCommentObject = commentObjectFields.singleOrNull { it.type == commentGifObject }?.name
                ?: throw PatchException("Expected one comment GIF field")
            GetGifMediaExtension.changeFirstString(gifObjectFieldFromCommentObject)

            val commentUserFieldName = commentObjectFields.singleOrNull { it.type == USER_MODEL_CLASS_NAME }?.name
                ?: throw PatchException("Expected one comment user field")
            GetCommentUserDataExtension.changeFirstString(commentUserFieldName)

            val commentMediaObjectFieldName =
                classDefBy { it.type == commentMediaHelperClass }
                    .fields
                    .singleOrNull { it.type == MEDIA_CLASS_NAME }
                    ?.name ?: throw PatchException("Expected one photo wrapper media field")
            GetImageMediaExtension.changeStringAt(1, commentMediaObjectFieldName)
        }
    }
