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
            }
        }
    }
