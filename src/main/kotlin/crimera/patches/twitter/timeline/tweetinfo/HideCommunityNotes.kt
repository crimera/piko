package crimera.patches.twitter.timeline.tweetinfo

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

@Patch(
    name = "Hide Community Notes",
    dependencies = [SettingsPatch::class, TweetInfoHook::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = true
)
object HideCommunityNotes :BytecodePatch(
    setOf(SettingsStatusLoadFingerprint)
){
    override fun execute(context: BytecodeContext) {
        SettingsStatusLoadFingerprint.enableSettings("hideCommunityNotes")
    }
}