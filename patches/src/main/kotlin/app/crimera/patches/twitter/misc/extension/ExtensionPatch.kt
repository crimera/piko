/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.extension

import app.morphe.patches.all.misc.extension.sharedExtensionPatch

val sharedExtensionPatch =
    sharedExtensionPatch(
        listOf("shared", "twitter"),
        twitterInitHook,
    )
