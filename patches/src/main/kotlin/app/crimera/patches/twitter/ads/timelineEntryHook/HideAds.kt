package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints.FeatureFlagLoadFingerprint
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants
import app.crimera.utils.enableSettings
import app.crimera.utils.flagSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private object HidePromotedTrendFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/model/json/timeline/urt/JsonTimelineTrend;",
    returnType = "Ljava/lang/Object",
)

@Suppress("unused")
val hideAds =
    bytecodePatch(
        name = "Remove Ads",
        description = "Removed promoted posts, trends and google ads",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(timelineEntryHookPatch, settingsPatch)
        execute {
            // Normal Ads.
            SettingsStatusLoadFingerprint.enableSettings("hideAds")

            // Google Ads.
            FeatureFlagLoadFingerprint.flagSettings("hideGoogleAds")

            // Promoted Trends
            val method = HidePromotedTrendFingerprint.method
            val instructions = method.instructions

            val return_obj = instructions.last { it.opcode == Opcode.RETURN_OBJECT }
            val return_loc = return_obj.location.index
            val return_reg = method.getInstruction<OneRegisterInstruction>(return_loc).registerA

            val last_new_inst = instructions.last { it.opcode == Opcode.NEW_INSTANCE }.location.index
            val loc = last_new_inst + 3
            val reg = method.getInstruction<TwoRegisterInstruction>(loc).registerA

            method.addInstructionsWithLabels(
                return_loc,
                """
                invoke-static {v$reg},${Constants.PATCHES_DESCRIPTOR}/TimelineEntry;->hidePromotedTrend(Ljava/lang/Object;)Z
                move-result v$reg
                if-eqz v$reg, :cond_1212
                const v$return_reg, 0x0
                """.trimIndent(),
                ExternalLabel("cond_1212", return_obj),
            )
        }
    }
