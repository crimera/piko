package crimera.patches.twitter.interaction.downloads

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.replaceInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.interaction.downloads.fingerprints.SetDownloadDestinationFingerprint
import crimera.patches.twitter.settings.SettingsPatch.UTILS_DESCRIPTOR

@Patch(
    name = "Change download folder",
    description = "Unlocks the ability to download videos from Twitter",
    requiresIntegrations = true,
    compatiblePackages = [CompatiblePackage("com.twitter.android")]
)
@Suppress("unused")
object ChangeDownloadFolderPatch : BytecodePatch(
    setOf(SetDownloadDestinationFingerprint)
) {
    private const val GETFOLDER_DESCRIPTOR =
        "invoke-static {p1}, $UTILS_DESCRIPTOR;->getVideoFolder(Ljava/lang/String;)Ljava/lang/String;"
    override fun execute(context: BytecodeContext) {
        val result = SetDownloadDestinationFingerprint.result
            ?: throw PatchException("Could not find fingerprint")

        val method = result.mutableMethod

        val insertAt = method.getInstructions()
            .first { it.opcode == Opcode.INVOKE_VIRTUAL }.location.index

        // when replacing values we should avoid hardcoding the registers
        val publicFolderRegister = method.getInstruction<OneRegisterInstruction>(insertAt-1).registerA
        method.replaceInstruction(insertAt-1, """
            sget-object v$publicFolderRegister, Landroid/os/Environment;->DIRECTORY_MOVIES:Ljava/lang/String;
        """.trimIndent())

        method.addInstructions(insertAt, """
            $GETFOLDER_DESCRIPTOR
            move-result-object p1
        """.trimIndent())
    }
}