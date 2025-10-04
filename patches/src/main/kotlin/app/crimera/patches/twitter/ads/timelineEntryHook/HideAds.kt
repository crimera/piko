package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.featureFlag.featureFlagPatch.fingerprints.featureFlagLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants
import app.crimera.utils.enableSettings
import app.crimera.utils.flagSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private val hidePromotedTrendFingerprint =
    fingerprint {
        returns("Ljava/lang/Object")
        custom { it, _ ->
            it.definingClass == "Lcom/twitter/model/json/timeline/urt/JsonTimelineTrend;"
        }
    }

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
            settingsStatusLoadFingerprint.enableSettings("hideAds")

            // Google Ads.
            featureFlagLoadFingerprint.flagSettings("hideGoogleAds")

            // Promoted Trends
            val method = hidePromotedTrendFingerprint.method
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
