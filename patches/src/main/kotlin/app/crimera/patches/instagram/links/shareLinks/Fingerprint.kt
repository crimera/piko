/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.shareLinks

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

private const val HIGHLIGHT_SHARE_ENDPOINT_STRING =
    "third_party_sharing/%s/get_story_highlights_to_share_url/"
private const val HIGHLIGHT_PERMALINK_ACTION_STRING = "create_highlight_permalink_share_url"

internal object PermalinkResponseJsonParserFingerprint : Fingerprint(
    name = "unsafeParseFromJson",
    filters = listOf(
        fieldAccess(type = "Ljava/lang/String;", opcode = Opcode.IPUT_OBJECT),
        string("PermalinkResponseImpl"),
    ),
)

internal object ProfileUrlResponseJsonParserFingerprint : Fingerprint(
    name = "unsafeParseFromJson",
    filters = listOf(
        fieldAccess(type = "Ljava/lang/String;", opcode = Opcode.IPUT_OBJECT),
        string("ProfileThirdPartySharingUrlResponseImpl"),
    ),
)

internal object AudioUrlResponseJsonParserFingerprint : Fingerprint(
    name = "unsafeParseFromJson",
    filters = listOf(
        string("audio_to_share_url"),
        fieldAccess(type = "Ljava/lang/String;", opcode = Opcode.IPUT_OBJECT),
    ),
)

internal object StoryItemThirdPartySharingUrlResponseImplFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    definingClass = "StoryItemThirdPartySharingUrlResponseImpl;",
    filters = listOf(opcode(Opcode.RETURN_OBJECT)),
)

internal object StoryItemShareUrlReadFingerprint : Fingerprint(
    filters = listOf(
        literal("story_item_to_share_url".hashCode()),
        methodCall(
            parameters = listOf("I"),
            returnType = "Ljava/lang/String;",
            opcode = Opcode.INVOKE_INTERFACE,
            location = MatchAfterImmediately(),
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
    ),
)

internal object LiveThirdPartySharingUrlResponseImplFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    definingClass = "Lcom/instagram/api/schemas/LiveThirdPartySharingUrlResponseImpl;",
    filters = listOf(opcode(Opcode.RETURN_OBJECT)),
)

internal object PandoLiveShareUrlGetterFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/api/schemas/ImmutablePandoLiveThirdPartySharingUrlResponse;",
    returnType = "Ljava/lang/String;",
    filters = listOf(literal("live_to_share_url".hashCode()), opcode(Opcode.RETURN_OBJECT)),
)

private val liveShareUrlRead = listOf(
    literal("live_to_share_url".hashCode()),
    methodCall(
        parameters = listOf("I"),
        returnType = "Ljava/lang/String;",
        opcode = Opcode.INVOKE_INTERFACE,
        location = MatchAfterImmediately(),
    ),
    opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
)

internal object LiveShareUrlCallbackFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    filters = liveShareUrlRead,
)

internal object LiveShareUrlEventFingerprint : Fingerprint(
    name = "invokeSuspend",
    parameters = listOf("Ljava/lang/Object;"),
    filters = liveShareUrlRead + listOf(
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        methodCall(definingClass = "Ljava/lang/String;", name = "length", location = MatchAfterImmediately()),
        // The options dialog reads the same field again to emit the URL after the empty check.
        methodCall(parameters = listOf("I"), returnType = "Ljava/lang/String;", opcode = Opcode.INVOKE_INTERFACE),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
    ),
)

internal object HighlightShareUrlRequestFingerprint : Fingerprint(
    strings = listOf(HIGHLIGHT_SHARE_ENDPOINT_STRING, HIGHLIGHT_PERMALINK_ACTION_STRING),
    filters = listOf(
        // Share URL callback.
        opcode(Opcode.NEW_INSTANCE),
    ),
)

/**
 * Share URL callback of the highlight request, where the URL is read from the response.
 */
internal fun highlightShareUrlCallbackFingerprint(callbackType: String) = Fingerprint(
    definingClass = callbackType,
    returnType = "V",
    parameters = listOf("Ljava/lang/Object;"),
    filters = listOf(
        methodCall(returnType = "Ljava/lang/String;", opcode = Opcode.INVOKE_INTERFACE),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
    ),
)
