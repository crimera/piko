package crimera.patches.twitter.misc.hidecommunitynotes

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.hidecommunitynotes.fingerprints.HideCommunityNoteFingerprint
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

@Patch(
    name = "Hide Community Notes",
    compatiblePackages = [CompatiblePackage("com.twitter.android")] ,
    dependencies = [SettingsPatch::class],
    use = false
)
object HideCommunityNotePatch :BytecodePatch(
    setOf(HideCommunityNoteFingerprint)
){
    override fun execute(context: BytecodeContext) {
        val result = HideCommunityNoteFingerprint.result
            ?: throw PatchException("HideCommunityNoteFingerprint not Found")

        val methods = result.mutableMethod
        val instructions = methods.getInstructions()

        val loc = instructions.last { it.opcode == Opcode.IPUT_OBJECT }.location.index

     val HOOK_DESCRIPTOR =
            "invoke-static {}, ${SettingsPatch.PREF_DESCRIPTOR};->hideCommNotes()Z"

        methods.addInstructionsWithLabels(loc,"""
            $HOOK_DESCRIPTOR
            move-result v0
            if-nez v0, :end
        """.trimIndent(),
            ExternalLabel("end",instructions.last { it.opcode == Opcode.RETURN_VOID })
        )

        SettingsStatusLoadFingerprint.result!!.mutableMethod.addInstruction(
            0,
            "${SettingsPatch.SSTS_DESCRIPTOR}->hideCommunityNotes()V"
        )

        //end
    }
}