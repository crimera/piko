package app.crimera.patches.twitter.misc.customize.exploretabs

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.CUSTOMISE_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fingerprint
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private val customiseExploreTabsFingerprint =
    fingerprint {
        opcodes(Opcode.NEW_INSTANCE)
        custom { it, _ ->
            it.definingClass.endsWith("JsonPageTabs;")
        }
    }

@Suppress("unused")
val customiseExploreTabsPatch =
    bytecodePatch(
        name = "Customize explore tabs",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val method = customiseExploreTabsFingerprint.method

            val instructions = method.instructions

            val index = instructions.first { it.opcode == Opcode.IGET_OBJECT }.location.index

            method.addInstructions(
                index + 1,
                """
                invoke-static {v1}, $CUSTOMISE_DESCRIPTOR;->exploretabs(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                move-result-object v1
                """.trimIndent(),
            )
            settingsStatusLoadFingerprint.enableSettings("exploreTabCustomisation")
        }
    }
