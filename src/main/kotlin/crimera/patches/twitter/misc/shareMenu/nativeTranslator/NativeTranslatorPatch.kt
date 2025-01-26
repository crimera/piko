package crimera.patches.twitter.misc.shareMenu.nativeTranslator

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.shared.misc.mapping.ResourceMappingPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22c
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.misc.shareMenu.fingerprints.ShareMenuButtonFuncCallFingerprint
import crimera.patches.twitter.misc.shareMenu.hooks.ShareMenuButtonAddHook
import crimera.patches.twitter.misc.shareMenu.hooks.ShareMenuButtonInitHook
import crimera.patches.twitter.misc.shareMenu.nativeDownloader.NativeDownloaderPatch

@Patch(
    name = "Custom translator",
    description = "",
    dependencies = [SettingsPatch::class, NativeTranslatorHooksPatch::class, ResourceMappingPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = true,
)
@Suppress("unused")
object NativeTranslatorPatch : BytecodePatch(
    setOf(
        ShareMenuButtonFuncCallFingerprint,
        ShareMenuButtonInitHook,
        SettingsStatusLoadFingerprint,
        ShareMenuButtonAddHook,
    ),
) {
    override fun execute(context: BytecodeContext) {
        val result =
            ShareMenuButtonFuncCallFingerprint.result
                ?: throw PatchException("ShareMenuButtonFuncCallFingerprint not found")

        val DD = "${SettingsPatch.PATCHES_DESCRIPTOR}/translator/NativeTranslator;"

        val method = result.mutableMethod
        val instructions = method.getInstructions()

        // one click func
        var targetIndex: Int = 0
        var refReg: Int = 0
        result.scanResult.stringsScanResult!!.matches.forEach { stringMatch ->
            val str = stringMatch.string
            if (str.contains("click") && refReg == 0) {
                val movObj = method.getInstruction<TwoRegisterInstruction>(stringMatch.index - 1)
                refReg = movObj.registerA
            } else if (str.contains("spaces?id=")) {
                targetIndex =
                    instructions.last { it.location.index < stringMatch.index && it.opcode == Opcode.CHECK_CAST }.location.index + 1
                return@forEach
            }
        }
        if (targetIndex == 0 || refReg == 0) {
            throw PatchException("hook not found")
        }

        // inject func
        val postObj = method.getInstruction<BuilderInstruction22c>(targetIndex)

        val postObjReg = postObj.registerA
        val ctxReg = postObj.registerB

        method.addInstructions(
            targetIndex + 1,
            """
            invoke-virtual/range{v$refReg .. v$refReg}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;
            move-result-object v$ctxReg
            check-cast v$ctxReg, Landroid/app/Activity;
            invoke-static {v$ctxReg, v$postObjReg}, $DD->translate(Landroid/content/Context;Ljava/lang/Object;)V
            return-void
            """.trimIndent(),
        )

        // show icon always
        ShareMenuButtonAddHook.addButton("SendToSpacesSandbox", "enableNativeTranslator")

        // text func
        var offset = 0
        if (NativeDownloaderPatch.offset) {
            offset = 3
        }
        ShareMenuButtonInitHook.setButtonText("View in Spaces Sandbox", "translate_tweet_show", offset)

        // icon
        //     ShareMenuButtonInitHook.setButtonIcon(buttonReference, "ic_vector_sparkle", 0)

        SettingsStatusLoadFingerprint.enableSettings("nativeTranslator")
    }
}
