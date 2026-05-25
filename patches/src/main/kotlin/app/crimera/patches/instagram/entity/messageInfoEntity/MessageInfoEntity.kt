/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.messageInfoEntity

import app.crimera.utils.changeFirstString
import app.crimera.utils.changeStringAt
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

val messageInfoEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of message info",
    ) {
        execute {
            StellaDirectMessagingServiceAudioRelatedFingerprint.apply {
                val strIndex = stringMatches.first().index
                method.apply {
                    val audioDataIGetObjectIndex = indexOfFirstInstruction(strIndex, Opcode.IGET_OBJECT)
                    val iGetObjectMetaData = getInstruction(audioDataIGetObjectIndex).fieldExtractor()
                    GetAudioMediaExtension.changeFirstString(iGetObjectMetaData.name)

                    mutableClassDefBy(extensionToClassName(iGetObjectMetaData.returnType))
                        .methods
                        .first {
                            it.returnType ==
                                "Ljava/lang/Integer;"
                        }.apply {
                            val mediaIGetObjectIndex = indexOfFirstInstruction(Opcode.IGET_OBJECT)
                            val fieldName = getInstruction(mediaIGetObjectIndex).fieldExtractor().name
                            GetAudioMediaExtension.changeStringAt(1, fieldName)
                        }
                }
            }
        }
    }
