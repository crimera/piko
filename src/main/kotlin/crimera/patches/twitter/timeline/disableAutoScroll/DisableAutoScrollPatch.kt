package crimera.patches.twitter.timeline.disableAutoScroll


import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object DisableAutoScrollFingerprint:MethodFingerprint(
    returnType = "V",
    strings = listOf(
        "applicationManager",
        "releaseCompletable",
        "preferences",
        "twSystemClock",
        "launchTracker",
        "cold_start_launch_time_millis",
        ),
)

//credits to @Ouxyl
@Patch(
    name = "Disable auto timeline scroll on launch",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = true,
    requiresIntegrations = true
)
object DisableAutoScrollPatch:BytecodePatch(
    setOf(DisableAutoScrollFingerprint)
){
    override fun execute(context: BytecodeContext) {
        val result = DisableAutoScrollFingerprint.result
            ?:throw PatchException("DisableAutoScrollFingerprint not found")

        val method = result.mutableClass.methods.last()

        method.addInstructions(0,"""
        const v0,0x0
        return v0
        """.trimIndent())

        SettingsStatusLoadFingerprint.enableSettings("disableAutoTimelineScroll")
        //end
    }
}