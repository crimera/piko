package crimera.patches.twitter.misc.customize.exploretabs

import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.*
import crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint

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
        execute {
            compatibleWith("com.twitter.android")
            dependsOn(settingsPatch)

            val result by customiseExploreTabsFingerprint()

            val method = result.mutableMethod

            val instructions = method.instructions

            val index = instructions.first { it.opcode == Opcode.IGET_OBJECT }.location.index

            method.addInstructions(
                index + 1,
                """
                invoke-static {v1}, ${CUSTOMISE_DESCRIPTOR};->exploretabs(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                move-result-object v1
                """.trimIndent(),
            )

            val settingsStatusMatch by settingsStatusLoadFingerprint()
            settingsStatusMatch.enableSettings("exploreTabCustomisation")
        }
    }
