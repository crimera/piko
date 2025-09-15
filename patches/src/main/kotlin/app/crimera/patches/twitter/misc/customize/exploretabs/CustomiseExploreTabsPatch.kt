package app.crimera.patches.twitter.misc.customize.exploretabs

import app.crimera.patches.twitter.misc.settings.CUSTOMISE_DESCRIPTOR
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

internal val customiseExploreTabsFingerprint =
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
        use = true,
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
            settingsStatusLoadFingerprint.method.enableSettings("exploreTabCustomisation")
        }
    }
