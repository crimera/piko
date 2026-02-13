package app.crimera.patches.twitter.entity

import app.morphe.patcher.patch.bytecodePatch

val entityGenerator =
    bytecodePatch(
        description = "Reflect entity patch generator",
    ) {
        dependsOn(tweetEntityPatch, tweetInfoEntityPatch, extMediaEntityPatch)
    }
