/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.commentDataEntity

import app.crimera.patches.instagram.entity.decoder.CommentButtonOnClickFingerprint
import app.crimera.utils.changeFirstString
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

val commentDataEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of comment data",
    ) {
        execute {

            CommentButtonOnClickFingerprint.apply {
                val commentShareClickStrIndex = stringMatches[1].index

                method.apply {

                    val ifEqzIndex = indexOfFirstInstruction(commentShareClickStrIndex, Opcode.IF_EQZ)
                    val commentTextField = getInstruction(ifEqzIndex - 1).fieldExtractor().name
                    GetTextExtension.changeFirstString(commentTextField)
                }

                RandomGetCommentObjectMediaFingerprint.apply {
                    val commentObject = method.returnType

                    classDef.methods.first { it.parameters.size == 1 }.apply {
                        val commentGifObject = returnType

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

                        val gifObjectFieldFromCommentObject =
                            mutableClassDefBy { it.type == commentObject }
                                .fields
                                .first {
                                    it.type ==
                                        commentGifObject
                                }.name
                        GetGifMediaExtension.changeFirstString(gifObjectFieldFromCommentObject)
                    }
                }
            }
        }
    }
