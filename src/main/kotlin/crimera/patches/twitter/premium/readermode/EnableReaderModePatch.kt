package crimera.patches.twitter.premium.readermode

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.getInstructions
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.annotation.CompatiblePackage
import app.revanced.patcher.patch.annotation.Patch
import com.android.tools.smali.dexlib2.Opcode
import crimera.patches.twitter.misc.settings.SettingsPatch
import crimera.patches.twitter.misc.settings.fingerprints.SettingsStatusLoadFingerprint
import crimera.patches.twitter.premium.readermode.fingerprints.EnableReaderMode1Fingerprint
import crimera.patches.twitter.premium.readermode.fingerprints.EnableReaderMode2Fingerprint
import java.util.logging.Logger

@Patch(
    name = "Enable Reader Mode",
    description = "Enables \"Reader Mode\" on long threads. Requires X 10.72.2-release.0 or earlier.",
    dependencies = [SettingsPatch::class],
    compatiblePackages = [CompatiblePackage("com.twitter.android")],
    requiresIntegrations = true,
)
@Suppress("unused")
object EnableReaderModePatch : BytecodePatch(
    setOf(EnableReaderMode1Fingerprint, EnableReaderMode2Fingerprint, SettingsStatusLoadFingerprint),
) {
    override fun execute(context: BytecodeContext) {
        val result1 =
            EnableReaderMode1Fingerprint.result ?: run {
                // Reader mode was removed in 10.72.3
                Logger
                    .getLogger("app.revanced.piko") // The logger name must start with "app.revanced"
                    .warning("\"Enable Reader Mode\" is not supported in this version. Use X 10.72.2 or earlier. Patch not executed.")
                return
            }

        val PREF = "invoke-static {}, ${SettingsPatch.PREF_DESCRIPTOR};->enableReaderMode()Z"

        // find location of the flag
        var strLoc: Int = 0
        result1.scanResult.stringsScanResult!!.matches.forEach { match ->
            val str = match.string
            if (str.equals("subscriptions_feature_1005"))
                {
                    strLoc = match.index
                    return@forEach
                }
        }

        if (strLoc == 0)
            {
                throw PatchException("hook not found")
            }
        // remove the flag check
        val methods = result1.mutableMethod
        val instructions = methods.getInstructions()
        val filters = instructions.filter { it.opcode == Opcode.IF_EQZ }
        for (item in filters) {
            val loc = item.location.index
            if (loc > strLoc)
                {
                    methods.addInstruction(loc - 1, PREF.trimIndent())
                    break
                }
        }

        val result2 =
            EnableReaderMode2Fingerprint.result
                ?: throw PatchException("EnableReaderMode2Fingerprint not found")

        // remove the flag check
        val methods2 = result2.mutableMethod
        val loc =
            methods2
                .getInstructions()
                .first { it.opcode == Opcode.IF_EQZ }
                .location.index
        methods2.addInstruction(loc - 1, PREF.trimIndent())

        SettingsStatusLoadFingerprint.enableSettings("enableReaderMode")
        // end
    }
}
