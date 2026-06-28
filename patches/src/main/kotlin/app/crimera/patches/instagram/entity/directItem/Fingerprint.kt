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
internal object IsSentByViewerExtension : Fingerprint(name = "isSentByViewer", definingClass = DIRECT_ITEM_CLASS)
internal object SetHideInThreadExtension : Fingerprint(name = "setHideInThread", definingClass = DIRECT_ITEM_CLASS)
internal object GetItemTypeExtension : Fingerprint(name = "getItemType", definingClass = DIRECT_ITEM_CLASS)
internal object GetThreadKeyExtension : Fingerprint(name = "getThreadKey", definingClass = DIRECT_ITEM_CLASS)
internal object GetThreadIdExtension : Fingerprint(name = "getThreadId", definingClass = DIRECT_ITEM_CLASS)
internal object GetMediaObjectExtension : Fingerprint(name = "getMediaObject", definingClass = DIRECT_ITEM_CLASS)

// returnType omitted: v426 returns Z, v433+ returns V. const-string → iput pattern is present in both.
internal object DirectItemDispatchFingerprint : Fingerprint(
    strings = listOf("item_id", "user_id", "text", "timestamp", "hide_in_thread", "thread_key"),
)
