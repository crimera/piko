/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.directItem

import app.crimera.patches.instagram.utils.Constants.ENTITY_CLASS
import app.morphe.patcher.Fingerprint

internal const val DIRECT_ITEM_CLASS = "$ENTITY_CLASS/DirectItem;"

// --- Extension getters whose placeholder strings are rewritten at patch time. ---
internal object GetBaseClassNameExtension : Fingerprint(name = "getBaseClassName", definingClass = DIRECT_ITEM_CLASS)
internal object GetItemIdExtension : Fingerprint(name = "getItemId", definingClass = DIRECT_ITEM_CLASS)
internal object GetUserIdExtension : Fingerprint(name = "getUserId", definingClass = DIRECT_ITEM_CLASS)
internal object GetTextExtension : Fingerprint(name = "getText", definingClass = DIRECT_ITEM_CLASS)
internal object SetTextExtension : Fingerprint(name = "setText", definingClass = DIRECT_ITEM_CLASS)
internal object GetTimestampRawExtension : Fingerprint(name = "getTimestampRaw", definingClass = DIRECT_ITEM_CLASS)
internal object IsHideInThreadExtension : Fingerprint(name = "isHideInThread", definingClass = DIRECT_ITEM_CLASS)
internal object SetHideInThreadExtension : Fingerprint(name = "setHideInThread", definingClass = DIRECT_ITEM_CLASS)
internal object GetItemTypeExtension : Fingerprint(name = "getItemType", definingClass = DIRECT_ITEM_CLASS)
internal object GetThreadKeyExtension : Fingerprint(name = "getThreadKey", definingClass = DIRECT_ITEM_CLASS)
internal object GetThreadIdExtension : Fingerprint(name = "getThreadId", definingClass = DIRECT_ITEM_CLASS)

/**
 * The per-field JSON dispatch helper for DirectItem ({@code LX/0gL;.A00} on v426). Every JSON
 * key string constant is immediately followed by the {@code iput} that stores the parsed value
 * into the matching (obfuscated) field on the DirectItem base class. Walking this method lets us
 * read every real field name straight out of the target APK at patch time — no runtime guessing.
 *
 * The declaring class of the {@code item_id} field is also the DirectItem base class, so we
 * derive the base-class binary name from the same walk instead of hardcoding it.
 */
internal object DirectItemDispatchFingerprint : Fingerprint(
    strings = listOf("item_id", "user_id", "text", "timestamp", "hide_in_thread", "thread_key"),
    returnType = "Z",
)
