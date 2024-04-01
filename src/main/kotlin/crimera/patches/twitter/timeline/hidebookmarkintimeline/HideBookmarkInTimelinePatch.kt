package crimera.patches.twitter.timeline.hidebookmarkintimeline

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.timeline.hidebookmarkintimeline.fingerprints.HideBookmarkInTimelineFingerprint1
import crimera.patches.twitter.timeline.hidebookmarkintimeline.fingerprints.HideBookmarkInTimelineFingerprint2

import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch

@Patch(
    name = "Hide bookmark icon in timeline",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
)
object HideBookmarkInTimelinePatch:BytecodePatch(
    setOf(HideBookmarkInTimelineFingerprint1,HideBookmarkInTimelineFingerprint2, SettingsStatusLoadFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result1 = HideBookmarkInTimelineFingerprint1.result
            ?:throw PatchException("HideBookmarkInTimelineFingerprint1 not found")

        val PREF = "invoke-static {}, ${SettingsPatch.PREF_DESCRIPTOR};->hideInlineBookmark()Z"

        val method1 = result1.mutableMethod
        val loc = method1.getInstructions().first { it.opcode == Opcode.CONST_STRING }.location.index+2
        method1.addInstruction(loc,PREF)

        val result2 = HideBookmarkInTimelineFingerprint2.result
            ?:throw PatchException("HideBookmarkInTimelineFingerprint2 not found")
        val method2 = result2.mutableMethod
        val loc2 = method2.getInstructions().first { it.opcode == Opcode.CONST_STRING }.location.index+2
        method2.addInstruction(loc2,PREF)

        SettingsStatusLoadFingerprint.result!!.mutableMethod.addInstruction(
            0,
            "${SettingsPatch.SSTS_DESCRIPTOR}->hideInlineBmk()V"
        )

        //end
    }

}