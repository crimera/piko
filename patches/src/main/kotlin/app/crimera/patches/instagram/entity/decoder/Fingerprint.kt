/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.decoder

import app.crimera.patches.instagram.utils.Constants.EDIT_MEDIA_INFO_FRAGMENT_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchFirst
import app.morphe.patcher.checkCast
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

/**
 * Id of the current media, read from the media list with the current media index.
 */
object EditMediaInfoGetCurrentMediaIdFingerprint : Fingerprint(
    definingClass = EDIT_MEDIA_INFO_FRAGMENT_CLASS,
    returnType = "Ljava/lang/String;",
    parameters = listOf(EDIT_MEDIA_INFO_FRAGMENT_CLASS),
    filters = listOf(
        fieldAccess(definingClass = "this", opcode = Opcode.IGET_OBJECT, location = MatchFirst()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        // Current media index.
        fieldAccess(type = "I", opcode = Opcode.IGET, location = MatchAfterImmediately()),
        opcode(Opcode.CONST_4, location = MatchAfterImmediately()),
        opcode(Opcode.IF_LTZ, location = MatchAfterImmediately()),
        fieldAccess(definingClass = "this", type = "Ljava/util/List;", opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        methodCall(smali = "Ljava/util/List;->size()I", location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_GE, location = MatchAfterImmediately()),
        methodCall(
            smali = "Ljava/util/List;->get(I)Ljava/lang/Object;",
            location = MatchAfterImmediately(),
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        checkCast(MEDIA_CLASS_NAME, location = MatchAfterImmediately()),
    ),
)

object CommentButtonOnClickFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("select_comment_screen_delete_comments_tap", "comment_share_click"),
)
