package crimera.patches.twitter.misc.hidecommunitynotes

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.hidecommunitynotes.fingerprints.HideCommunityNoteFingerprint

@Patch(
    name = "Hide Community Notes",
    compatiblePackages = [CompatiblePackage("com.twitter.android")] ,
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
        methods.removeInstruction(loc)

        //end
    }
}