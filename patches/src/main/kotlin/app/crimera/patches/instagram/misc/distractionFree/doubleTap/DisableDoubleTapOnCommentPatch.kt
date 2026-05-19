/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree.doubleTap

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchFirst
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

internal object CommentOnDoubleTap1Fingerprint : Fingerprint(
    strings = listOf("comment_row_component", "fb_comment_double_tap"),
    name = "onDoubleTap",
    filters =
        listOf(
            opcode(Opcode.IGET_OBJECT, MatchFirst()),
            opcode(Opcode.IGET_OBJECT, MatchAfterImmediately()),
            opcode(Opcode.IGET_BOOLEAN, MatchAfterImmediately()),
        ),
)

internal object CommentOnDoubleTap2Fingerprint : Fingerprint(
    strings = listOf("comment_row_component", "fb_comment_double_tap"),
    name = "onDoubleTap",
    filters =
        listOf(
            opcode(Opcode.IGET_OBJECT, MatchFirst()),
            opcode(Opcode.SGET_OBJECT, MatchAfterImmediately()),
            opcode(Opcode.IGET_OBJECT, MatchAfterImmediately()),
            opcode(Opcode.IGET_BOOLEAN, MatchAfterImmediately()),
        ),
)

@Suppress("unused")
val disableDoubleTapOnCommentPatch =
    bytecodePatch(
        description = "Disable double tap like on comment",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            val fingerprints = listOf(CommentOnDoubleTap1Fingerprint, CommentOnDoubleTap2Fingerprint)

            fingerprints.forEach {
                it.method.apply {
                    addInstructions(
                        0,
                        DOUBLE_TAP_PREF_DESCRIPTOR.format("disableDoubleTapComment"),
                    )
                }
            }
        }
    }
