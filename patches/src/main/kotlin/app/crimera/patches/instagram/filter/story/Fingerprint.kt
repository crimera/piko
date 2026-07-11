/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.filter.story

import app.morphe.patcher.Fingerprint

internal object StoryResponseJsonParserFingerprint : Fingerprint(
    strings = listOf("tray", "share_to_friends_story_pending_media", "hallpass_share_info"),
    custom = { methodDef, _ ->
        methodDef.name.lowercase().contains("parsefromjson")
    },
)
