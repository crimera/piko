

package crimera.patches.twitter.misc.shareMenu.nativeDownloader

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.shared.misc.mapping.resourceMappingPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import crimera.patches.twitter.misc.settings.PATCHES_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import crimera.patches.twitter.misc.shareMenu.fingerprints.ShareMenuButtonFuncCallFingerprint
import crimera.patches.twitter.misc.shareMenu.hooks.*

var offset: Boolean = false

@Suppress("unused")
val nativeDownloaderPatch =
    bytecodePatch(
        name = "Custom downloader",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch, nativeDownloaderHooksPatch, resourceMappingPatch)

        execute {
            val result by ShareMenuButtonFuncCallFingerprint()

            val DD = "${PATCHES_DESCRIPTOR}/NativeDownloader;"

            val method = result.mutableMethod
            val instructions = method.instructions

            // one click func
            var strLoc: Int = 0
            var refReg: Int = 0
            result.stringMatches?.forEach { match ->
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

            val shareMenuButtonHookMatch by ShareMenuButtonHook()
            val buttonReference =
                shareMenuButtonHookMatch.buttonReference("SendToTweetViewSandbox")

            // text func
            val shareMenuButtonInitHookMatch by ShareMenuButtonInitHook()
            shareMenuButtonInitHookMatch.setButtonText("View in Tweet Sandbox", "piko_pref_native_downloader_alert_title")

            // icon
            shareMenuButtonInitHookMatch.setButtonIcon(buttonReference, "ic_vector_incoming")

            val shareMenuButtonAddHookMatch by ShareMenuButtonAddHook()
            shareMenuButtonAddHookMatch.addButton(buttonReference, "enableNativeDownloader")

            val settingsStatusMatch by settingsStatusLoadFingerprint()
            settingsStatusMatch.enableSettings("nativeDownloader")

            offset = true
        }
    }
