package app.crimera.patches.twitter.misc.bringbacktwitter

import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val bringBackTwitterPatch =
    bytecodePatch(
        name = "Bring back twitter",
        description = "Bring back old twitter logo and name",
        use = false,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(bringBackTwitterResource)

        execute {
        }
    }
