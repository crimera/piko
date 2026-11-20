/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm.saveMessages

import app.crimera.patches.instagram.entity.decoder.USER_MODEL_CLASS_NAME
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val SAVED_MESSAGES_HOOK_CLASS = "$PATCHES_DESCRIPTOR/dm/SavedMessagesHook;"
internal const val USER_CACHE_CLASS = "Lcom/instagram/user/model/UserCache;"

internal object CachedUserLookupExtensionFingerprint : Fingerprint(
    definingClass = SAVED_MESSAGES_HOOK_CLASS,
    name = "lookupCachedUser",
    parameters = listOf(USER_SESSION_CLASS, "Ljava/lang/String;"),
    returnType = "Ljava/lang/Object;",
)

// Session-scoped user cache accessor.
internal object UserCacheInstanceFingerprint : Fingerprint(
    definingClass = USER_CACHE_CLASS,
    isStatic = true,
    parameters = listOf(USER_SESSION_CLASS),
    returnType = USER_CACHE_CLASS,
)

internal object UserCacheIdMapFingerprint : Fingerprint(
    definingClass = USER_CACHE_CLASS,
    returnType = "Lcom/instagram/user/model/User;",
    filters = listOf(
        string("UserCache getOrPut called with empty String"),
        fieldAccess(
            definingClass = USER_CACHE_CLASS,
            type = "Ljava/util/concurrent/ConcurrentMap;",
            opcode = Opcode.IGET_OBJECT,
        ),
    ),
)

internal fun userCacheLookupFingerprint(idMap: FieldReference) = Fingerprint(
    definingClass = USER_CACHE_CLASS,
    parameters = listOf("Ljava/lang/String;"),
    returnType = USER_MODEL_CLASS_NAME,
    filters = listOf(
        fieldAccess(
            reference = idMap,
            opcode = Opcode.IGET_OBJECT
        )
    ),
)

internal object DeltaThreadIdFieldExtensionFingerprint : Fingerprint(
    definingClass = SAVED_MESSAGES_HOOK_CLASS,
    name = "deltaThreadIdFieldName",
    returnType = "Ljava/lang/String;",
)

// MQTT post-processing step (not on REST path).
internal object DirectItemPostprocessFingerprint : Fingerprint(
    parameters = listOf(USER_SESSION_CLASS, "L"),
    strings = listOf("DirectMessage.postprocess.%s", "Encountered DirectMessage with null type"),
)

// The queued SQLite operation captures the server ID followed by the client context.
internal object DirectItemDbHideFingerprint : Fingerprint(
    name = "run",
    parameters = emptyList(),
    returnType = "V",
    filters = listOf(
        fieldAccess(definingClass = "this", type = "Lcom/instagram/model/direct/DirectThreadKey;", opcode = Opcode.IGET_OBJECT),
        fieldAccess(definingClass = "this", type = "Ljava/lang/String;", opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        fieldAccess(definingClass = "this", type = "Ljava/lang/String;", opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
        string("client_item_id=='"),
        string(" AND server_item_id IS NULL) OR ("),
        string("Both message ID and client context is null."),
    ),
)

internal object ThreadRecipientFingerprint : Fingerprint(
    name = "unsafeParseFromJson",
    parameters = listOf("L"),
    returnType = "Ljava/lang/Object;",
    filters = listOf(
        string("recipients"),
        methodCall(returnType = USER_MODEL_CLASS_NAME, location = MatchAfterWithin(24)),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        string("left_users"),
        string("thread_v2_id"),
    ),
)
