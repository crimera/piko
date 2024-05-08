package crimera.patches.twitter.ads.timelineEntryHook

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

@Patch(
    name = "Remove \"Communities to join\" Banner",
    dependencies = [SettingsPatch::class,TimelineEntryHookPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = true
)
object HideCTJ :BytecodePatch(
    setOf(SettingsStatusLoadFingerprint)
){
    override fun execute(context: BytecodeContext) {
        SettingsStatusLoadFingerprint.enableSettings("hideCommToJoin")
    }
}