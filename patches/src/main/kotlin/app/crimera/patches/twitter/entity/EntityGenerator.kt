/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.entity

import app.morphe.patcher.patch.bytecodePatch

val entityGenerator =
    bytecodePatch(
        description = "Reflect entity patch generator",
    ) {
        dependsOn(tweetEntityPatch, tweetInfoEntityPatch, extMediaEntityPatch)
    }
