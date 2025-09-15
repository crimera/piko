package app.crimera.patches.twitter.timeline.hideCommunityBadge

import app.crimera.patches.twitter.misc.settings.PREF_DESCRIPTOR
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.crimera.utils.extractDescriptors
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c

internal val commModelFingerprint =
    fingerprint {
        strings(
            "actionResults",
            "role",
        )
    }

@Suppress("unused")
val hideCommunityBadge =
    bytecodePatch(
        name = "Hide community badges",
        description = "",
        use = true,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val method = commModelFingerprint.method
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
            settingsStatusLoadFingerprint.method.enableSettings("hideCommBadge")
        }
    }
