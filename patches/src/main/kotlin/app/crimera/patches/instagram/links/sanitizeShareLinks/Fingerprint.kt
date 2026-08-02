/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.sanitizeShareLinks

import app.morphe.patcher.Fingerprint

internal val TARGET_STRING_ARRAY =
    arrayOf(
        "XDTPermalinkResponse",
        "profile_to_share_url",
    )

internal object PermalinkResponseJsonParserFingerprint : Fingerprint(
    strings = listOf(TARGET_STRING_ARRAY[0]),
    custom = { methodDef, _ ->
        methodDef.name.lowercase().contains("parsefromjson")
    },
)

internal object ProfileUrlResponseJsonParserFingerprint : Fingerprint(
    strings = listOf(TARGET_STRING_ARRAY[1]),
    custom = { methodDef, _ ->
        methodDef.name.lowercase().contains("parsefromjson")
    },
)

internal object StoryItemThirdPartySharingUrlResponseImplFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    definingClass = "StoryItemThirdPartySharingUrlResponseImpl;",
)

internal object LiveThirdPartySharingUrlResponseImplFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    definingClass = "Lcom/instagram/api/schemas/LiveThirdPartySharingUrlResponseImpl;",
)
