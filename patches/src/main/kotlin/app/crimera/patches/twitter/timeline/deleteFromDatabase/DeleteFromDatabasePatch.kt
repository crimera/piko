package app.crimera.patches.twitter.timeline.deleteFromDatabase

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val deleteFromDatabasePatch =
    bytecodePatch(
        name = "Delete from database",
        description = "Delete entries from database(cache)",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            SettingsStatusLoadFingerprint.enableSettings("deleteFromDb")
        }
    }
