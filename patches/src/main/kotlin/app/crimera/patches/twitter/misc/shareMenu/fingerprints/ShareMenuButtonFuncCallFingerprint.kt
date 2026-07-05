/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.shareMenu.fingerprints

import app.morphe.patcher.Fingerprint

object ShareMenuButtonFuncCallFingerprint : Fingerprint(
    returnType = "V",
    strings =
        listOf(
            "OK",
            "Delete Status",
            "click",
            "tweet_analytics",
            "author_moderated_replies_author_enabled",
            "conversational_replies_android_pinned_replies_creation_enabled",
            "share_menu_click",
        ),
)
