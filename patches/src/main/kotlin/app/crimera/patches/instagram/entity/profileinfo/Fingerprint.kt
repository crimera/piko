/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.profileinfo

import app.crimera.patches.instagram.utils.Constants.ENTITY_CLASS
import app.crimera.patches.instagram.utils.Constants.USER_DETAIL_VIEW_MODEL_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal const val EXTENSION_CLASS = "$ENTITY_CLASS/ProfileInfo;"

internal object GetProfileRelatedDetailsExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "getProfileRelatedDetails",
)

internal object GetUserDetailViewModelExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "getUserDetailViewModel",
)

internal object IsSelfProfileExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "isSelfProfile",
)

internal object GetProfileSessionExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "getUserSession",
)

internal object GetProfileUserExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS,
    name = "getProfileUser",
)

internal object ProfileUserIdFingerprint : Fingerprint(
    definingClass = USER_DETAIL_VIEW_MODEL_CLASS,
    parameters = emptyList(),
    returnType = "Ljava/lang/String;",
    filters = listOf(
        fieldAccess(definingClass = USER_DETAIL_VIEW_MODEL_CLASS, opcode = Opcode.IGET_OBJECT),
        methodCall(name = "getValue", returnType = "Ljava/lang/Object;", location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.CHECK_CAST, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        string("INVALID_USER_ID"),
    ),
)

// This fingerprint will also be using for follow back indicator patch && settings button && profile more options
object ProfileUserInfoViewBinderFingerprint : Fingerprint(
    filters = listOf(
        string("ProfileUserInfoViewBinder.newView"),
        opcode(Opcode.MOVE_RESULT_OBJECT),
    ),
)

internal object ProfileRelatedDetailsFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(),
    strings = listOf("is_self", "trigger", "content_source", "destination"),
)
