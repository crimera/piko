package crimera.patches.twitter.misc.customize.inlinebar

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import crimera.patches.twitter.misc.settings.CUSTOMISE_DESCRIPTOR
import crimera.patches.twitter.misc.settings.enableSettings
import crimera.patches.twitter.misc.settings.settingsPatch
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

internal val customiseInlineBarFingerprint = fingerprint {
    returns("Ljava/util/List;")
    strings("bookmarks_in_timelines_enabled")
}

// TODO: make a separate extensions package
@Suppress("unused")
val customiseInlineBarPatch = bytecodePatch(
    name = "Customize Inline action Bar items",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val results by customiseInlineBarFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {
        val method = results.mutableMethod
        val instructions = method.instructions

        val returnObj_loc = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index
        val r0 = method.getInstruction<OneRegisterInstruction>(returnObj_loc).registerA

        val METHOD = """
            invoke-static {v$r0}, ${CUSTOMISE_DESCRIPTOR};->inlineBar(Ljava/util/List;)Ljava/util/List;
            move-result-object v$r0
        """.trimIndent()

        method.addInstructions(returnObj_loc, METHOD)

        settingsStatusMatch.enableSettings("inlineBarCustomisation")
    }
}