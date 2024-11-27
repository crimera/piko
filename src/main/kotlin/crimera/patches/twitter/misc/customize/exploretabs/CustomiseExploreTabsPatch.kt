package crimera.patches.twitter.misc.customize.timelinetabs

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint

object CustomiseExploreTabsFingerprint : MethodFingerprint(
    opcodes = listOf(Opcode.NEW_INSTANCE),
    customFingerprint = { it, _ ->
        it.definingClass.endsWith("JsonPageTabs;")
    },
)

@Patch(
    name = "Customize explore tabs",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    use = true,
)
@Suppress("unused")
object CustomiseExploreTabsPatch : BytecodePatch(
    setOf(CustomiseExploreTabsFingerprint, SettingsStatusLoadFingerprint),
) {
    override fun execute(context: BytecodeContext) {
        val result =
            CustomiseExploreTabsFingerprint.result
                ?: throw PatchException("CustomiseExploreTabsFingerprint not found")

        val method = result.mutableMethod

        val instructions = method.getInstructions()

        val index = instructions.first { it.opcode == Opcode.IGET_OBJECT }.location.index

        method.addInstructions(
            index + 1,
            """
            invoke-static {v1}, ${SettingsPatch.CUSTOMISE_DESCRIPTOR};->exploretabs(Ljava/util/ArrayList;)Ljava/util/ArrayList;
            move-result-object v1
            """.trimIndent(),
        )

        SettingsStatusLoadFingerprint.enableSettings("exploreTabCustomisation")
    }
}
