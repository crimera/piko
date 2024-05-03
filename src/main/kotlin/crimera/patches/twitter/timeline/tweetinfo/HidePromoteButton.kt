package crimera.patches.twitter.timeline.tweetinfo

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

@Patch(
    name = "Hide promote button",
    description = "Hides promote button under self posts",
    dependencies = [SettingsPatch::class, TweetInfoHook::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = false
)
object HidePromoteButton :BytecodePatch(
    setOf(SettingsStatusLoadFingerprint)
){
    override fun execute(context: BytecodeContext) {
        SettingsStatusLoadFingerprint.enableSettings("hidePromoteButton")
    }
}