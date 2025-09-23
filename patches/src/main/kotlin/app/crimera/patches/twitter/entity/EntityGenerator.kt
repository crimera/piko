package app.crimera.patches.twitter.entity

import app.revanced.patcher.patch.bytecodePatch

val entityGenerator =
    bytecodePatch(
        description = "Reflect entity patch generator",
    ) {
        dependsOn(tweetEntityPatch, tweetInfoEntityPatch, extMediaEntityPatch)
    }
