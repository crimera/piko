/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.directItem

import app.crimera.patches.instagram.utils.Constants.ENTITY_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.StringComparisonType
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

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

internal object DirectItemDispatchFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("L", "L", "Ljava/lang/String;"),
    filters = listOf("item_id", "client_context", "timestamp", "user_id", "text", "hide_in_thread", "thread_key").map { string(it) },
)

internal object SentByViewerKeyFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf("I"),
    filters = listOf(string("is_sent_by_viewer")),
)

internal fun sentByViewerFieldFingerprint(definingClass: String, keyGetter: MethodReference) = Fingerprint(
    definingClass = definingClass,
    filters = listOf(
        methodCall(reference = keyGetter),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        methodCall(smali = "Ljava/lang/String;->equals(Ljava/lang/Object;)Z", location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        methodCall(parameters = emptyList(), returnType = "Z", opcode = Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        fieldAccess(type = "Z", opcode = Opcode.IPUT_BOOLEAN, location = MatchAfterImmediately()),
    ),
)

private val FIELD_WRITE_OPCODES = listOf(
    Opcode.IPUT,
    Opcode.IPUT_WIDE,
    Opcode.IPUT_OBJECT,
    Opcode.IPUT_BOOLEAN,
    Opcode.IPUT_BYTE,
    Opcode.IPUT_CHAR,
    Opcode.IPUT_SHORT,
)

/**
 * Comparison of the parsed JSON key with [key], followed by [branch].
 */
private fun jsonKeyFilters(key: String, branch: Opcode) = listOf(
    string(key),
    methodCall(smali = "Ljava/lang/String;->equals(Ljava/lang/Object;)Z", location = MatchAfterImmediately()),
    opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
    opcode(branch, location = MatchAfterImmediately()),
)

/**
 * Field assigned with the value of the JSON [key] by a parser of [definingClass].
 */
internal fun jsonKeyFieldFingerprint(key: String, definingClass: String) = Fingerprint(
    definingClass = definingClass,
    filters = jsonKeyFilters(key, Opcode.IF_EQZ) + fieldAccess(opcodes = FIELD_WRITE_OPCODES),
)

/**
 * JSON [key] whose value assignment is shared with other keys, and reached by a branch.
 */
internal fun jsonSharedKeyFingerprint(key: String, definingClass: String) = Fingerprint(
    definingClass = definingClass,
    filters = jsonKeyFilters(key, Opcode.IF_NEZ),
)

/**
 * MQTT items set their text after the item type.
 */
internal fun directItemSubTextFingerprint(itemClass: String, itemTypeClass: String) = Fingerprint(
    definingClass = itemClass,
    parameters = listOf(itemTypeClass, "Ljava/lang/Object;"),
    filters = listOf(
        methodCall(definingClass = "this", parameters = listOf(itemTypeClass), returnType = "V"),
        fieldAccess(
            definingClass = "this",
            type = "Ljava/lang/Object;",
            opcode = Opcode.IPUT_OBJECT,
            location = MatchAfterImmediately(),
        ),
    ),
)

internal object DirectThreadKeyToStringFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/model/direct/DirectThreadKey;",
    name = "toString",
    filters = listOf(
        // Thread id, the first value of the string.
        fieldAccess(definingClass = "this", type = "Ljava/lang/String;", opcode = Opcode.IGET_OBJECT),
        string("DirectThreadKey{mThreadId=", StringComparisonType.STARTS_WITH),
    ),
)
