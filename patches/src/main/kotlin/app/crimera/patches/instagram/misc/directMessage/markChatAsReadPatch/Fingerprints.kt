/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.directMessage.markChatAsReadPatch

import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

const val EXTENSION_CLASS_NAME = "${PATCHES_DESCRIPTOR}/dm/MarkChatAsRead;"

internal object GetThreadSeenDummyParameterClassNameExtensionFingerprint : Fingerprint(
    name = "getThreadSeenDummyParameterClassName",
    definingClass = EXTENSION_CLASS_NAME,
)

internal object GetThreadSeenFunctionClassNameExtensionFingerprint : Fingerprint(
    name = "getThreadSeenFunctionClassName",
    definingClass = EXTENSION_CLASS_NAME,
)

internal object GetThreadSeenFunctionMethodNameExtensionFingerprint : Fingerprint(
    name = "getThreadSeenFunctionMethodName",
    definingClass = EXTENSION_CLASS_NAME,
)

internal object GetMessageCursorFieldNameExtensionFingerprint : Fingerprint(
    name = "getMessageCursorFieldName",
    definingClass = EXTENSION_CLASS_NAME,
)

internal object GetThreadInfoFieldNameExtensionFingerprint : Fingerprint(
    name = "getThreadInfoFieldName",
    definingClass = EXTENSION_CLASS_NAME,
)

internal object GetButtonEnumClassNameExtensionFingerprint : Fingerprint(
    name = "getButtonEnumClassName",
    definingClass = EXTENSION_CLASS_NAME,
)

// --------------------------------------
internal object DmInfoJsonParserFingerprint : Fingerprint(
    name = "unsafeParseFromJson",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L"),
    filters = listOf(
        string("visual_messages_newest_cursor"),
        methodCall(smali = "Ljava/lang/String;->equals(Ljava/lang/Object;)Z", location = MatchAfterImmediately()),
        methodCall(parameters = emptyList(), returnType = "Ljava/lang/String;"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        fieldAccess(type = "Ljava/lang/String;", opcode = Opcode.IPUT_OBJECT, location = MatchAfterImmediately()),
    ),
)

internal object ThreadLongPressButtonsEnumInitFingerprint : Fingerprint(
    name = "<clinit>",
    strings = listOf("LEAVE", "NFB_SETTINGS"),
    filters = listOf(
        string("MARK_AS_READ"),
        fieldAccess(definingClass = "this", opcode = Opcode.SPUT_OBJECT),
    ),
)

internal object ThreadLongPressMuteButtonBuilderFingerprint : Fingerprint(
    isStatic = true,
    returnType = "V",
    parameters = listOf(USER_SESSION_CLASS, "L", "Ljava/util/List;"),
    filters =
        listOf(
            methodCall(parameters = emptyList(), returnType = "Lcom/instagram/model/direct/DirectThreadKey;"),
            methodCall(name = "isMuted", parameters = emptyList(), returnType = "Z"),
            methodCall(smali = "Ljava/util/List;->add(Ljava/lang/Object;)Z"),
        ),
)

internal object ThreadLongPressButtonActionFingerprint : Fingerprint(
    isStatic = false,
    returnType = "V",
    filters = listOf(string("inbox_thread_long_press"), string("thread_mark_unread"), string("thread_unflag")),
)
