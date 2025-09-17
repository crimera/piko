package app.crimera.patches.twitter.entity

import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val entityGenerator =
    bytecodePatch(
        description = "Reflect entity patch generator",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(tweetEntityPatch, tweetInfoEntityPatch, extMediaEntityPatch)
    }
