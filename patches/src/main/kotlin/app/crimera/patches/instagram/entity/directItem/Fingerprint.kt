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
internal object GetClientContextExtension : Fingerprint(name = "getClientContext", definingClass = DIRECT_ITEM_CLASS)
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
// Media resolution: concrete item class (X/6fW) + one field-name provider per supported shape.
// All resolved by stable JSON key + the unobfuscated com.instagram.feed.media.Media type.
internal object GetMediaClassNameExtension : Fingerprint(name = "mediaClassName", definingClass = DIRECT_ITEM_CLASS)
internal object FieldMediaExtension : Fingerprint(name = "fieldMedia", definingClass = DIRECT_ITEM_CLASS)
internal object FieldMediaShareExtension : Fingerprint(name = "fieldMediaShare", definingClass = DIRECT_ITEM_CLASS)
internal object FieldRavenMediaExtension : Fingerprint(name = "fieldRavenMedia", definingClass = DIRECT_ITEM_CLASS)
internal object FieldClipExtension : Fingerprint(name = "fieldClip", definingClass = DIRECT_ITEM_CLASS)
internal object FieldClipMediaExtension : Fingerprint(name = "fieldClipMedia", definingClass = DIRECT_ITEM_CLASS)
internal object FieldReelExtension : Fingerprint(name = "fieldReel", definingClass = DIRECT_ITEM_CLASS)
internal object FieldReelMediaExtension : Fingerprint(name = "fieldReelMedia", definingClass = DIRECT_ITEM_CLASS)
internal object FieldVoiceExtension : Fingerprint(name = "fieldVoice", definingClass = DIRECT_ITEM_CLASS)
internal object FieldVoiceMediaExtension : Fingerprint(name = "fieldVoiceMedia", definingClass = DIRECT_ITEM_CLASS)
internal object FieldVisualExtension : Fingerprint(name = "fieldVisual", definingClass = DIRECT_ITEM_CLASS)
internal object FieldVisualMediaExtension : Fingerprint(name = "fieldVisualMedia", definingClass = DIRECT_ITEM_CLASS)
// xma reshare: item List field + the permalink String field on each element (JSON key "target_url").
internal object FieldXmaExtension : Fingerprint(name = "fieldXma", definingClass = DIRECT_ITEM_CLASS)
internal object FieldXmaLinkExtension : Fingerprint(name = "fieldXmaLink", definingClass = DIRECT_ITEM_CLASS)

// returnType omitted: v426 returns Z, v433+ returns V. const-string → iput pattern is present in both.
internal object DirectItemDispatchFingerprint : Fingerprint(
    strings = listOf("item_id", "client_context", "user_id", "text", "timestamp", "hide_in_thread", "thread_key"),
)
