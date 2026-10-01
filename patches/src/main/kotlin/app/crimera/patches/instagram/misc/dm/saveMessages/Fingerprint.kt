/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm.saveMessages

import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.Fingerprint

private const val SAVED_MESSAGES_HOOK_CLASS = "$PATCHES_DESCRIPTOR/dm/SavedMessagesHook;"

internal object DeltaThreadIdFieldExtensionFingerprint : Fingerprint(
    definingClass = SAVED_MESSAGES_HOOK_CLASS,
    name = "deltaThreadIdFieldName",
    returnType = "Ljava/lang/String;",
)

// returnType omitted: v426 returns Z, v433+ returns V. Only classDef is used, not the method directly.
internal object DirectItemFieldParserFingerprint : Fingerprint(
    strings = listOf("item_id", "hide_in_thread"),
)

// MQTT post-processing step (not on REST path). Its return type varies between app versions.
internal object DirectItemPostprocessFingerprint : Fingerprint(
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

// DM thread deserializer dispatch. returnType omitted: v426 returns Z, v430+ returns V.
internal object ThreadUsersDispatchFingerprint : Fingerprint(
    strings = listOf("users", "admin_user_ids", "left_users", "thread_v2_id", "input_mode"),
)
