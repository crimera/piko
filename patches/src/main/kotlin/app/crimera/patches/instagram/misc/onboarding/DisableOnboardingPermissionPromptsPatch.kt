/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.onboarding

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.LINKS_DESCRIPTOR
import app.crimera.patches.common.parameterRegisterStart
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private object IgBloksFullScreenOpenFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf(
        "Landroid/content/Context;",
        "Lcom/instagram/bloks/hosting/IgBloksScreenConfig;",
    ),
    filters = listOf(
        // The first String field supplies the screen app ID to the Bloks fetcher.
        fieldAccess(definingClass = "this", type = "Ljava/lang/String;", opcode = Opcode.IGET_OBJECT),
        string("BKDataFetcher.fetch"),
    ),
    custom = { method, _ -> !AccessFlags.STATIC.isSet(method.accessFlags) },
)

@Suppress("unused")
val disableOnboardingPermissionPromptsPatch = patch(
    name = "Disable onboarding permission prompts",
    description = "Prevents contacts and location permission onboarding prompts from appearing",
    default = true,
) {
    dependsOn(settingsPatch)
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute {
        IgBloksFullScreenOpenFingerprint.let {
            val appIdField = it.instructionMatches.first().fieldReference
            val method = it.method
            check(parameterRegisterStart(method) > 0) { "Bloks opener has no local register" }

            method.addInstructionsWithLabels(
                0,
                """
                move-object/from16 v0, p0
                iget-object v0, v0, $appIdField
                invoke-static {v0}, $LINKS_DESCRIPTOR->shouldBlockOnboardingScreen(Ljava/lang/String;)Z
                move-result v0
                if-eqz v0, :piko_continue
                return-void
                """.trimIndent(),
                ExternalLabel("piko_continue", method.getInstruction(0)),
            )
        }
    }
}
