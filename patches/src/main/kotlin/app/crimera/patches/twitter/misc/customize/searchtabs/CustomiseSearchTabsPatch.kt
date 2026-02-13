package app.crimera.patches.twitter.misc.customize.searchtabs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private object CustomiseSearchTabsPatchFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    strings = listOf(
        "search_features_media_tab_enabled",
        "search_features_lists_search_enabled",
    )
)

@Suppress("unused")
val customiseSearchTabsPatch =
    bytecodePatch(
        name = "Customize search tab items",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val method = CustomiseSearchTabsPatchFingerprint.method
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
            SettingsStatusLoadFingerprint.enableSettings("searchTabCustomisation")
        }
    }
