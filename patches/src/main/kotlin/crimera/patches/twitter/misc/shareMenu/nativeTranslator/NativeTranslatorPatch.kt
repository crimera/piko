package crimera.patches.twitter.misc.shareMenu.nativeTranslator

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.shared.misc.mapping.resourceMappingPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22c
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import crimera.patches.twitter.misc.settings.PATCHES_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import crimera.patches.twitter.misc.shareMenu.fingerprints.ShareMenuButtonFuncCallFingerprint
import crimera.patches.twitter.misc.shareMenu.hooks.*
import crimera.patches.twitter.misc.shareMenu.nativeDownloader.offset

@Suppress("unused")
val nativeTranslatorPatch =
    bytecodePatch(
        name = "Custom translator",
        use = true,
    ) {
        execute {
            compatibleWith("com.twitter.android")
            dependsOn(settingsPatch, nativeTranslatorHooksPatch, resourceMappingPatch)

            val result by ShareMenuButtonFuncCallFingerprint()

            val DD = "${PATCHES_DESCRIPTOR}/translator/NativeTranslator;"

            val method = result.mutableMethod
            val instructions = method.instructions

            // one click func
            var targetIndex: Int = 0
            var refReg: Int = 0
            result.stringMatches?.forEach { stringMatch ->
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

            // text func
            var offsetVal = 0
            if (offset) {
                offsetVal = 3
            }

            // /
            val shareMenuButtonHookMatch by ShareMenuButtonHook()
            val buttonReference =
                shareMenuButtonHookMatch.buttonReference("SendToSpacesSandbox")

            // text func
            val shareMenuButtonInitHookMatch by ShareMenuButtonInitHook()
            shareMenuButtonInitHookMatch.setButtonText("View in Spaces Sandbox", "translate_tweet_show", offsetVal)

            // icon
            shareMenuButtonInitHookMatch.setButtonIcon(buttonReference, "ic_vector_sparkle", 0)

            val shareMenuButtonAddHookMatch by ShareMenuButtonAddHook()
            shareMenuButtonAddHookMatch.addButton(buttonReference, "enableNativeTranslator")

            val settingsStatusMatch by settingsStatusLoadFingerprint()
            settingsStatusMatch.enableSettings("nativeTranslator")
        }
    }
