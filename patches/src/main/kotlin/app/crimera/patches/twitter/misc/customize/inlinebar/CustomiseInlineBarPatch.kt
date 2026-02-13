package app.crimera.patches.twitter.misc.customize.inlinebar

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private object CustomiseInlineBarFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    filters = listOf(
        string("bookmarks_in_timelines_enabled")
    )
)

@Suppress("unused")
val customiseInlineBarPatch =
    bytecodePatch(
        name = "Customize Inline action Bar items",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val method = CustomiseInlineBarFingerprint.method
            val instructions = method.instructions

            val returnObj_loc = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index
            val r0 = method.getInstruction<OneRegisterInstruction>(returnObj_loc).registerA

            val METHOD =
                """
                invoke-static {v$r0}, $CUSTOMISE_DESCRIPTOR;->inlineBar(Ljava/util/List;)Ljava/util/List;
                move-result-object v$r0
                """.trimIndent()

            method.addInstructions(returnObj_loc, METHOD)
            SettingsStatusLoadFingerprint.enableSettings("inlineBarCustomisation")
        }
    }
