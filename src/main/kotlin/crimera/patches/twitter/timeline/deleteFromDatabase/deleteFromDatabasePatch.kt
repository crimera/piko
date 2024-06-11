package crimera.patches.twitter.timeline.deleteFromDatabase

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

@Patch(
    name = "Delete from database",
    description = "Delete entries from database(cache)",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = false
)
object deleteFromDatabasePatch:BytecodePatch(
    setOf(SettingsStatusLoadFingerprint)
){
    override fun execute(context: BytecodeContext) {
        SettingsStatusLoadFingerprint.enableSettings("deleteFromDb")
    }
}