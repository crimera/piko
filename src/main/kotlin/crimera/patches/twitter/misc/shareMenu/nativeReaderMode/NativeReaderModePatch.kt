package crimera.patches.twitter.misc.shareMenu.nativeReaderMode

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import app.revanced.patches.shared.misc.mapping.ResourceMappingPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction21c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.misc.shareMenu.fingerprints.ActionEnumsFingerprint
import crimera.patches.twitter.misc.shareMenu.fingerprints.ShareMenuButtonFuncCallFingerprint
import crimera.patches.twitter.misc.shareMenu.hooks.ShareMenuButtonAddHook
import crimera.patches.twitter.misc.shareMenu.hooks.ShareMenuButtonInitHook
import crimera.patches.twitter.models.extractDescriptors

@Patch(
    name = "Native reader mode",
    description = "Requires X 11.0.0-release.0 or higher.",
    dependencies = [SettingsPatch::class, ResourceMappingPatch::class, NativeReaderModeResourcePatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = true,
)
@Suppress("unused")
object NativeReaderModePatch : BytecodePatch(
    setOf(
        ShareMenuButtonFuncCallFingerprint,
        ShareMenuButtonInitHook,
        SettingsStatusLoadFingerprint,
        ShareMenuButtonAddHook,
        ActionEnumsFingerprint,
    ),
) {
    override fun execute(context: BytecodeContext) {
        val actionName = "ReaderMode"

        // Add action
        val downloadActionReference = ActionEnumsFingerprint.addAction(actionName, ActionEnumsFingerprint.result!!)

        // Register button
        ShareMenuButtonAddHook.registerButton(actionName, "enableNativeReaderMode")
        val viewDebugDialogReference =
            (
                ShareMenuButtonAddHook.result
                    ?.method
                    ?.implementation
                    ?.instructions
                    ?.first { it.opcode == Opcode.SGET_OBJECT } as Instruction21c
            ).reference

        // Set Button Text
        ShareMenuButtonInitHook.setButtonText(actionName, "piko_title_native_reader_mode")
        ShareMenuButtonInitHook.setButtonIcon(actionName, "ic_vector_book_stroke_on")

        // TODO: handle possible nulls
        val buttonFunc = ShareMenuButtonFuncCallFingerprint.result
        val buttonFuncMethod =
            ShareMenuButtonFuncCallFingerprint.result
                ?.method
                ?.implementation
                ?.instructions
                ?.toList()

        val deleteStatusLoc =
            buttonFunc
                ?.scanResult
                ?.stringsScanResult
                ?.matches
                ?.first { it.string == "Delete Status" }
                ?.index
                ?: throw PatchException("Delete status not found")

        @Suppress("ktlint:standard:property-naming")
        val OkLoc =
            buttonFunc
                ?.scanResult
                ?.stringsScanResult
                ?.matches
                ?.first { it.string == "OK" }
                ?.index
                ?: throw PatchException("OK not found")

        val conversationalRepliesLoc =
            buttonFunc.scanResult.stringsScanResult
                ?.matches
                ?.first {
                    it.string ==
                        "conversational_replies_android_pinned_replies_creation_enabled"
                }?.index
                ?: throw PatchException("conversational_replies_android_pinned_replies_creation_enabled not found")

        val timelineRef =
            (
                buttonFuncMethod
                    ?.filterIndexed { i, ins ->
                        i > conversationalRepliesLoc && ins.opcode == Opcode.IGET_OBJECT
                    }?.first() as Instruction22c?
            ) ?: throw PatchException("Failed to find timelineRef")
        val timelineRefReg = (buttonFuncMethod?.get(deleteStatusLoc - 1) as Instruction35c).registerD

        val activityRefReg = (buttonFuncMethod[OkLoc - 3] as Instruction35c).registerD

        // Add Button function
        ShareMenuButtonFuncCallFingerprint.addButtonInstructions(
            downloadActionReference,
            """
            check-cast v$timelineRefReg, ${timelineRef.reference.extractDescriptors()[0]}
            iget-object v1, v$timelineRefReg, ${timelineRef.reference}
            
            invoke-static {v$activityRefReg, v1}, ${SettingsPatch.NATIVE_DESCRIPTOR}/readerMode/ReaderModeUtils;->launchReaderMode(Landroid/content/Context;Ljava/lang/Object;)V
            """.trimIndent(),
            viewDebugDialogReference,
        )

        SettingsStatusLoadFingerprint.enableSettings("nativeReaderMode")
    }
}
