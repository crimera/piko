package app.crimera.patches.twitter.misc.customize.sidebar

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

internal val customiseNavBarFingerprint =
    fingerprint {
        returns("Ljava/lang/Object")
        strings("android_global_navigation_top_level_monetization_enabled")
        custom { it, _ ->
            it.name == "invoke"
        }
    }

@Suppress("unused")
val customiseSideBarPatch =
    bytecodePatch(
        name = "Customize side bar items",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val method = customiseNavBarFingerprint.method

            val instructions = method.instructions

            var filledNewArrIndex = instructions.last { it.opcode == Opcode.FILLED_NEW_ARRAY_RANGE }.location.index
            val return_obj =
                instructions
                    .first { it.opcode == Opcode.RETURN_OBJECT && it.location.index > filledNewArrIndex }
                    .location.index
            val r0 = method.getInstruction<OneRegisterInstruction>(return_obj).registerA

            val METHOD =
                """
                invoke-static {v$r0}, $CUSTOMISE_DESCRIPTOR;->sideBar(Ljava/util/List;)Ljava/util/List;
                move-result-object v$r0
                """.trimIndent()

            method.addInstructionsWithLabels(return_obj, METHOD)
            settingsStatusLoadFingerprint.enableSettings("sideBarCustomisation")
        }
    }
