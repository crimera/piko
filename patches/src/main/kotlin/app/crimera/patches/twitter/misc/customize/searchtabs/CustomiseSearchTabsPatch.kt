package app.crimera.patches.twitter.misc.customize.searchtabs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

internal val customiseSearchTabsPatchFingerprint =
    fingerprint {
        returns("Ljava/util/List;")
        strings(
            "search_features_media_tab_enabled",
            "search_features_lists_search_enabled",
        )
    }

@Suppress("unused")
val customiseSearchTabsPatch =
    bytecodePatch(
        name = "Customize search tab items",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val method = customiseSearchTabsPatchFingerprint.method
            val instructions = method.instructions

            val returnObj_loc = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index

            val r0 = (method.getInstruction(returnObj_loc) as OneRegisterInstruction).registerA

            method.addInstructions(
                returnObj_loc,
                """
                invoke-static {v$r0}, $CUSTOMISE_DESCRIPTOR;->searchTabs(Ljava/util/List;)Ljava/util/List;
                move-result-object v$r0
                """.trimIndent(),
            )
            settingsStatusLoadFingerprint.method.enableSettings("searchTabCustomisation")
        }
    }
