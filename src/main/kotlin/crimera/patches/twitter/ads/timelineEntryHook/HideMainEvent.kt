package crimera.patches.twitter.ads.timelineEntryHook

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

@Patch(
    name = "Remove main event",
    description = "Removes main event (Paris) from Explore page",
    dependencies = [SettingsPatch::class,TimelineEntryHookPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = true
)
object HideMainEvent :BytecodePatch(
    setOf(SettingsStatusLoadFingerprint)
){
    override fun execute(context: BytecodeContext) {
        SettingsStatusLoadFingerprint.enableSettings("hideMainEvent")
    }
}