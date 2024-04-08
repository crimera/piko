package crimera.patches.twitter.timeline.showpollresults

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.timeline.showpollresults.fingerprints.JsonCardInstanceDataFingerprint

@Patch(
    name = "Show poll results",
    description = "Adds an option to show poll results without voting",
    compatiblePackages = [CompatiblePackage("com.twitter.android")]
)
object ShowPollResultsPatch: BytecodePatch(
    setOf(JsonCardInstanceDataFingerprint, SettingsStatusLoadFingerprint)
) {
    override fun execute(context: BytecodeContext) {
        val result = JsonCardInstanceDataFingerprint.result
            ?: throw PatchException("Could not find JsonCardInstanceData Fingerprint")

        val method = result.mutableMethod

        val loc = method.getInstructions().first { it.opcode == Opcode.MOVE_RESULT_OBJECT }.location.index

        val pollDescriptor =
            "invoke-static {p2}, ${SettingsPatch.PREF_DESCRIPTOR};->polls(Ljava/util/Map;)Ljava/util/Map;"

        method.addInstructions(
            loc+1,
            """
                $pollDescriptor
                move-result-object p2
            """.trimIndent()
        )

        SettingsStatusLoadFingerprint.result!!.mutableMethod.addInstruction(
            0,
            "${SettingsPatch.SSTS_DESCRIPTOR}->enableShowPollResults()V"
        )
    }
}