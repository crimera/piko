package crimera.patches.twitter.misc.customize.searchtabs

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
        execute {
            compatibleWith("com.twitter.android")
            dependsOn(settingsPatch)
            val results by customiseSearchTabsPatchFingerprint()

            val method = results.mutableMethod
            val instructions = method.instructions

            val returnObj_loc = instructions.last { it.opcode == Opcode.RETURN_OBJECT }.location.index
            val r0 = method.getInstruction<OneRegisterInstruction>(returnObj_loc).registerA

            method.addInstructions(
                returnObj_loc,
                """
                invoke-static {v$r0}, ${CUSTOMISE_DESCRIPTOR};->searchTabs(Ljava/util/List;)Ljava/util/List;
                move-result-object v$r0
                """.trimIndent(),
            )
            val settingsStatusMatch by settingsStatusLoadFingerprint()
            settingsStatusMatch.enableSettings("searchTabCustomisation")

            // end
        }
    }
