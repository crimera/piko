/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.directMessage.markChatAsReadPatch

import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal
import com.android.tools.smali.dexlib2.AccessFlags

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

internal object GetButtonEnumClassNameExtensionFingerprint : Fingerprint(
    name = "getButtonEnumClassName",
    definingClass = EXTENSION_CLASS_NAME,
)

// --------------------------------------
internal object DmInfoJsonParserFingerprint : Fingerprint(
    strings = listOf("visual_messages_newest_cursor"),
    custom = { methodDef, _ ->
        methodDef.name.lowercase().contains("parsefromjson")
    },
)

internal object ThreadLongPressButtonsEnumInitFingerprint : Fingerprint(
    strings = listOf("MARK_AS_READ", "LEAVE", "NFB_SETTINGS"),
)

internal object ThreadLongPressMuteButtonBuilderFingerprint : Fingerprint(
    returnType = "V",
    custom = { methodDef, _ ->
        methodDef.parameters.size == 3 &&
            methodDef.parameters.first().type == USER_SESSION_CLASS &&
            methodDef.parameters.last().type == "Ljava/util/List;"
    },
    filters =
        listOf(
            literal(0),
            literal(25),
        ),
)

internal object ThreadLongPressButtonStringListFingerprint : Fingerprint(
    strings = listOf("[DEBUG] Thread info"),
    returnType = "[Ljava/lang/String;",
)

internal object ThreadLongPressButtonActionFingerprint : Fingerprint(
    classFingerprint = ThreadLongPressButtonStringListFingerprint,
    strings = listOf("direct_inbox_action", "thread_unflag"),
)
