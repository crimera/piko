/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.sensitivemediasettings

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction


private object sensitiveMediaSettingsPatchFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/model/json/core/JsonTweetWithVisibilityResults\$\$JsonObjectMapper;",
    name = "parseField",
    returnType = "V",
    parameters = listOf("Lcom/twitter/model/json/core/JsonTweetWithVisibilityResults;", "Ljava/lang/String;", "Lcom/fasterxml/jackson/core/h;")
)

@Suppress("unused")
val sensitiveMediaPatch =
    bytecodePatch(
        name = "Show sensitive media",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val method = sensitiveMediaSettingsPatchFingerprint.method
            val instructions = method.instructions

            val ndx = instructions.first {
                it.opcode == Opcode.CONST_STRING &&
                (it as? ReferenceInstruction)?.reference?.toString() == "media_visibility_results"
            }.location.index

            val istrSoftInterPiv = instructions.first {
                it.location.index > ndx &&
                it.opcode == Opcode.CONST_STRING &&
                (it as? ReferenceInstruction)?.reference?.toString() == "soft_intervention_pivot"
            }

            method.addInstructionsWithLabels(
                ndx + 4,
                """
                invoke-static {}, $PREF_DESCRIPTOR;->showSensitiveMedia()Z
                move-result v0
                if-nez v0, :nextstr
                """.trimIndent(),
                ExternalLabel("nextstr", istrSoftInterPiv),
            )
            enableSettings("showSensitiveMedia")
        }
    }
