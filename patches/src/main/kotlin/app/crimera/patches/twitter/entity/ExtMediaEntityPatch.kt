/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.entity

import app.crimera.utils.changeFirstString
import app.crimera.utils.changeStringAt
import app.crimera.utils.getFieldName
import app.crimera.utils.getMethodName
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

val extMediaEntityPatch =
    bytecodePatch(
        description = "For extended media entity reflection",
    ) {
        execute {

            MediaOptionSheetMediaListVideoDownloaderImplDownloadMethodFingerprint.method.apply {
                val firstIGetObjectIndex = indexOfFirstInstruction(Opcode.IGET_OBJECT)
                val videoInfoFieldName = MediaOptionSheetMediaListVideoDownloaderImplDownloadMethodFingerprint.getFieldName(firstIGetObjectIndex)
                ExtMediaHighResVideoFingerprint.changeFirstString(videoInfoFieldName)

                val secondIGetObjectIndex = indexOfFirstInstruction(firstIGetObjectIndex+1,Opcode.IGET_OBJECT)
                val videoVariantsFieldName = MediaOptionSheetMediaListVideoDownloaderImplDownloadMethodFingerprint.getFieldName(secondIGetObjectIndex)
                ExtMediaHighResVideoFingerprint.changeStringAt(1,videoVariantsFieldName)
            }

            // ------------
            val imageFieldName =
                ExtMediaGetImageMethodFinder.getFieldName(
                    ExtMediaGetImageMethodFinder.method
                        .instructions
                        .last {
                            it.opcode ==
                                Opcode.IGET_OBJECT
                        }.location.index,
                )
            ExtMediaGetImageFingerprint.changeFirstString(imageFieldName)
// ------------
        }
    }
