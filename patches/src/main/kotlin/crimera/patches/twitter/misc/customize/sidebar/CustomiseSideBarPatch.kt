package crimera.patches.twitter.misc.customize.sidebar

import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
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

internal val CustomiseSideBarFingerprint = fingerprint {
    returns("Ljava/lang/Object")
    strings("android_global_navigation_top_level_monetization_enabled")
    custom { it, _ ->
        it.name == "invoke"
    }
}

@Suppress("unused")
val customiseSideBarPatch = bytecodePatch(
    name = "Customize side bar items",
) {
    dependsOn(settingsPatch)
    compatibleWith("com.twitter.android")

    val result by CustomiseSideBarFingerprint()
    val settingsStatusMatch by settingsStatusLoadFingerprint()

    execute {

        val method = result.mutableMethod

        val instructions = method.instructions

        val return_obj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index
        val r0 = method.getInstruction<OneRegisterInstruction>(return_obj).registerA

        val METHOD = """
            invoke-static {v$r0}, ${CUSTOMISE_DESCRIPTOR};->sideBar(Ljava/util/List;)Ljava/util/List;
            move-result-object v$r0
        """.trimIndent()

        method.addInstructionsWithLabels(return_obj, METHOD)

        settingsStatusMatch.enableSettings("sideBarCustomisation")
    }
}
