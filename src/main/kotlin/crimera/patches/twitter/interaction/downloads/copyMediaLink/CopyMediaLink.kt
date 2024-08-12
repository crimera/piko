package crimera.patches.twitter.interaction.downloads.copyMediaLink

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object DownloadCallFingerprint: MethodFingerprint(
    returnType = "V",
    strings = listOf(
        "downloadData",
        "activity.getString(R.str…nload_permission_request)",
        "isUseSnackbar"
    )
)


@Patch(
    name = "Add ability to copy media link",
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    dependencies = [SettingsPatch::class],
    use = true
)
object CopyMediaLink:BytecodePatch(
    setOf(DownloadCallFingerprint, SettingsStatusLoadFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = DownloadCallFingerprint.result
            ?: throw PatchException("DownloadCallFingerprint not found")

        val method = result.mutableMethod

        val instructions = method.getInstructions()

        val gotoLoc = instructions.first{it.opcode == Opcode.GOTO}.location.index

        val METHOD = """
            invoke-static{p0,p1}, ${SettingsPatch.PATCHES_DESCRIPTOR}/DownloadPatch;->mediaHandle(Ljava/lang/Object;Ljava/lang/Object;)V
        """.trimIndent()

        method.removeInstruction(gotoLoc-1)
        method.addInstruction(gotoLoc-1,METHOD);

        SettingsStatusLoadFingerprint.enableSettings("mediaLinkHandle")
    //end func
    }
    //end
}