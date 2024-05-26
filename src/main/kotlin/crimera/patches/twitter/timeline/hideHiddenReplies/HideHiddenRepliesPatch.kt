package crimera.patches.twitter.timeline.hideHiddenReplies

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

import app.revanced.patcher.fingerprint.MethodFingerprint
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction


object HideHiddenRepliesFingerprint: MethodFingerprint(
    returnType = "Ljava/lang/Object;",
    customFingerprint = { it, _ ->
        it.definingClass == "Lcom/twitter/model/json/timeline/urt/JsonTimelineTweet;"
    }
)


@Patch(
    name = "Hide hidden replies",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    dependencies = [SettingsPatch::class]
)
@Suppress("unused")
object HideHiddenRepliesPatch: BytecodePatch(
    setOf(HideHiddenRepliesFingerprint, SettingsStatusLoadFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = HideHiddenRepliesFingerprint.result
            ?: throw PatchException("Fingerprint not found")

        val method = result.mutableMethod
        val instructions = method.getInstructions()

        val get_bool = instructions.last { it.opcode == Opcode.IGET_BOOLEAN }.location.index
        val reg = method.getInstruction<TwoRegisterInstruction>(get_bool).registerA

        val M = "invoke-static {v$reg}, ${SettingsPatch.PREF_DESCRIPTOR};->hideHiddenReplies(Z)Z"

        method.addInstructions(get_bool+1, """
            $M
            move-result v$reg
        """.trimIndent())

        SettingsStatusLoadFingerprint.enableSettings("hideHiddenReplies")
    }
}