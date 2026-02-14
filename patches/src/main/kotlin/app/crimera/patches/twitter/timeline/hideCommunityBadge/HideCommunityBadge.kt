package app.crimera.patches.twitter.timeline.hideCommunityBadge

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.crimera.utils.extractDescriptors
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c

private object CommModelFingerprint : Fingerprint(
    strings = listOf(
        "actionResults",
        "role",
    )
)

@Suppress("unused")
val hideCommunityBadge =
    bytecodePatch(
        name = "Hide community badges",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val method = CommModelFingerprint.method
            val instructions = method.instructions

            val iputObj = instructions.last { it.opcode == Opcode.IPUT_OBJECT }
            val iputObjIns = iputObj as Instruction22c
            val ref = iputObjIns.reference.extractDescriptors()[1]
            val reg = iputObjIns.registerA
            val index = iputObj.location.index

            method.addInstructionsWithLabels(
                index,
                """
                sget-boolean v0, $PREF_DESCRIPTOR;->HIDE_COMM_BADGE:Z
                if-eqz v0, :piko
                    sget-object v$reg, $ref->NON_MEMBER:$ref  
                """.trimIndent(),
                ExternalLabel("piko", iputObj),
            )
            SettingsStatusLoadFingerprint.enableSettings("hideCommBadge")
        }
    }
