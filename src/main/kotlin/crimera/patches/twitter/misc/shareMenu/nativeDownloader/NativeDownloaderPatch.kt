package crimera.patches.twitter.misc.shareMenu.nativeDownloader

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.shared.misc.mapping.ResourceMappingPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.misc.shareMenu.fingerprints.ShareMenuButtonFuncCallFingerprint
import crimera.patches.twitter.misc.shareMenu.hooks.ShareMenuButtonAddHooks
import crimera.patches.twitter.misc.shareMenu.hooks.ShareMenuButtonHooks
import crimera.patches.twitter.misc.shareMenu.hooks.ShareMenuButtonInitHooks

val MethodFingerprint.exception: PatchException
    get() = PatchException("${this.javaClass.name} is not found")

@Patch(
    name = "Custom downloader",
    description = "",
    dependencies = [SettingsPatch::class, NativeDownloaderHooksPatch::class, ResourceMappingPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = true,
)
@Suppress("unused")
object NativeDownloaderPatch : BytecodePatch(
    setOf(
        ShareMenuButtonFuncCallFingerprint,
        ShareMenuButtonInitHooks,
        SettingsStatusLoadFingerprint,
        ShareMenuButtonAddHooks,
        ShareMenuButtonHooks,
    ),
) {
    override fun execute(context: BytecodeContext) {
        val result =
            ShareMenuButtonFuncCallFingerprint.result
                ?: throw PatchException("ShareMenuButtonFuncCallFingerprint not found")

        val DD = "${SettingsPatch.PATCHES_DESCRIPTOR}/NativeDownloader;"

        val method = result.mutableMethod
        val instructions = method.getInstructions()

        // one click func
        var strLoc: Int = 0
        var refReg: Int = 0
        result.scanResult.stringsScanResult!!.matches.forEach { match ->
            val str = match.string
            if (str.contains("click") && refReg == 0) {
                val movObj = method.getInstruction<TwoRegisterInstruction>(match.index - 1)
                refReg = movObj.registerA
            } else if (str.contains("tweetview?id=")) {
                strLoc = match.index
                return@forEach
            }
        }
        if (strLoc == 0 || refReg == 0) {
            throw PatchException("hook not found")
        }

        // inject func
        val postObj = method.getInstruction<TwoRegisterInstruction>(strLoc + 2)
        val postObjReg = postObj.registerA
        val ctxReg = postObj.registerB

        method.addInstructions(
            strLoc + 3,
            """
            invoke-virtual/range{v$refReg .. v$refReg}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;
            move-result-object v$ctxReg
            check-cast v$ctxReg, Landroid/app/Activity;
            invoke-static {v$ctxReg, v$postObjReg}, $DD->downloader(Landroid/content/Context;Ljava/lang/Object;)V
            return-void
            """.trimIndent(),
        )

        val filters = instructions.first { it.opcode == Opcode.GOTO_16 && it.location.index > strLoc }
        method.removeInstruction(filters.location.index - 1)

        // show icon always
        val buttonReference =
            ShareMenuButtonHooks.buttonReference("SendToTweetViewSandbox")
                ?: throw PatchException("ShareMenuButtonHooks not found")

        ShareMenuButtonAddHooks.addButton(buttonReference, "enableNativeDownloader")

        // text func
        ShareMenuButtonInitHooks.setButtonText("View in Tweet Sandbox", "piko_pref_native_downloader_alert_title")

        // icon
        ShareMenuButtonInitHooks.setButtonIcon(buttonReference, "ic_vector_incoming")

        SettingsStatusLoadFingerprint.enableSettings("nativeDownloader")
    }
}
