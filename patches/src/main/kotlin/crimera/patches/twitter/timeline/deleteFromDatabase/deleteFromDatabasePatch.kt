package crimera.patches.twitter.timeline.deleteFromDatabase

import app.revanced.patcher.patch.bytecodePatch
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

@Suppress("unused")
val deleteFromDatabasePatch = bytecodePatch(
    name = "Delete from database",
    description = "Delete entries from database(cache)",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        settingsStatusMatch.enableSettings("deleteFromDb")
    }
}