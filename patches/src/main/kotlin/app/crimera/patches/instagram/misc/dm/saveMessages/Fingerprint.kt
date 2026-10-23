/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm.saveMessages

import app.crimera.patches.instagram.entity.directItem.DirectItemDispatchFingerprint
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.methodCall
import app.morphe.patcher.newInstance
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private const val SAVED_MESSAGES_HOOK_CLASS = "$PATCHES_DESCRIPTOR/dm/SavedMessagesHook;"

internal object DeltaThreadIdFieldExtensionFingerprint : Fingerprint(
    definingClass = SAVED_MESSAGES_HOOK_CLASS,
    name = "deltaThreadIdFieldName",
    returnType = "Ljava/lang/String;",
)

internal object DirectItemJsonParserFingerprint : Fingerprint(
    classFingerprint = DirectItemDispatchFingerprint,
    name = "unsafeParseFromJson",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(
            definingClass = "this",
            parameters = listOf("L", "L", "Ljava/lang/String;"),
            returnType = "V",
            opcode = Opcode.INVOKE_STATIC,
        ),
        opcode(Opcode.RETURN_OBJECT),
    ),
)

// MQTT post-processing step (not on REST path).
internal object DirectItemPostprocessFingerprint : Fingerprint(
    parameters = listOf(USER_SESSION_CLASS, "L"),
    strings = listOf("DirectMessage.postprocess.%s", "Encountered DirectMessage with null type"),
)

// SQLite DAO hide-by-id. Injected at entry so our DB record is still present when the hook fires.
internal object DirectItemDbHideFingerprint : Fingerprint(
    strings = listOf("Both message ID and client context is null."),
    parameters = listOf(
        "Lcom/instagram/model/direct/DirectThreadKey;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
    ),
    returnType = "V",
)

// DM thread deserializer dispatch.
internal object ThreadUsersDispatchFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("admin_user_ids", "left_users", "thread_v2_id", "input_mode"),
    filters = listOf(
        string("users"),
        // Users array guard, which jumps to the block that stores the users list.
        opcode(Opcode.IF_NE, MatchAfterWithin(10)),
        newInstance("Ljava/util/ArrayList;", MatchAfterImmediately()),
    ),
)
