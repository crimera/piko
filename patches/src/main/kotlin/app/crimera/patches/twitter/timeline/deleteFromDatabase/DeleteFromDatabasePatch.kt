package app.crimera.patches.twitter.timeline.deleteFromDatabase

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val deleteFromDatabasePatch =
    bytecodePatch(
        name = "Delete from database",
        description = "Delete entries from database(cache)",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            settingsStatusLoadFingerprint.enableSettings("deleteFromDb")
        }
    }
