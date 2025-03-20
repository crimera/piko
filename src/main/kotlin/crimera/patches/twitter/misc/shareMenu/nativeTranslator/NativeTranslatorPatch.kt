package crimera.patches.twitter.misc.shareMenu.nativeTranslator

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.shared.misc.mapping.ResourceMappingPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.misc.shareMenu.fingerprints.ActionEnumsFingerprint
import crimera.patches.twitter.misc.shareMenu.fingerprints.ShareMenuButtonFuncCallFingerprint
import crimera.patches.twitter.misc.shareMenu.hooks.ShareMenuButtonAddHook
import crimera.patches.twitter.misc.shareMenu.hooks.ShareMenuButtonInitHook

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
        ActionEnumsFingerprint
    ),
) {
    override fun execute(context: BytecodeContext) {
        val actionName = "Translate"

        // Add action
        val downloadActionReference = ActionEnumsFingerprint.addAction(actionName, ActionEnumsFingerprint.result!!)

        // Register button
        ShareMenuButtonAddHook.registerButton(actionName)
        val viewDebugDialogReference =
            (ShareMenuButtonAddHook.result?.method?.implementation?.instructions?.last { it.opcode == Opcode.SGET_OBJECT } as Instruction21c).reference

        // Set Button Text
        ShareMenuButtonInitHook.setButtonText(actionName, "translate_tweet_show")
        ShareMenuButtonInitHook.setButtonIcon(actionName, "ic_vector_sparkle")

        val buttonFunc = ShareMenuButtonFuncCallFingerprint.result
        val buttonFuncMethod = ShareMenuButtonFuncCallFingerprint.result?.mutableMethod
        val deleteStatusLoc = buttonFunc?.scanResult?.stringsScanResult?.matches!!.first().index
        val activityRefReg = buttonFuncMethod?.getInstruction<TwoRegisterInstruction>(deleteStatusLoc + 1)?.registerA
        val timelineRefReg = buttonFuncMethod?.getInstruction<Instruction35c>(deleteStatusLoc - 1)?.registerD

        // Add Button function
        ShareMenuButtonFuncCallFingerprint.addButtonInstructions(
            downloadActionReference, """
                check-cast v$timelineRefReg, Lcom/twitter/model/timeline/n2;
                iget-object v1, v$timelineRefReg, Lcom/twitter/model/timeline/n2;->k:Lcom/twitter/model/core/e;
                
                invoke-virtual/range{v$activityRefReg .. v$activityRefReg}, Ljava/lang/ref/Reference;->get()Ljava/lang/Object;
                move-result-object v0
                check-cast v0, Landroid/app/Activity;
                
                invoke-static {v0, v1}, ${SettingsPatch.PATCHES_DESCRIPTOR}/translator/NativeTranslator;->translate(Landroid/content/Context;Ljava/lang/Object;)V
                
                return-void
        """.trimIndent(), viewDebugDialogReference
        )

        SettingsStatusLoadFingerprint.enableSettings("nativeTranslator")
    }
}
