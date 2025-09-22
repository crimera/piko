package app.crimera.patches.twitter.timeline.forceHD

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.crimera.utils.extractDescriptors
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c

internal val playerSupportFingerprint =
    fingerprint {
        accessFlags(AccessFlags.PUBLIC, AccessFlags.STATIC)

        custom { methodDef, classDef ->
            classDef.type.contains("/av/player/support/") &&
                methodDef.parameters.size == 2
        }
    }

@Suppress("unused")
val forceHDPatch =
    bytecodePatch(
        name = "Enable force HD videos",
        description = "Videos will be played in highest quality always",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            val method =
                playerSupportFingerprint.method

            val instructions = method.instructions

            val igetObj = instructions.first { it.opcode == Opcode.IGET_OBJECT }
            val igetObjIns = igetObj as Instruction22c
            val ref = igetObjIns.reference.extractDescriptors()[1]
            val reg = igetObjIns.registerA
            val index = igetObj.location.index + 1

            method.addInstructionsWithLabels(
                index,
                """
                sget-boolean v0, $PREF_DESCRIPTOR;->ENABLE_FORCE_HD:Z
                if-eqz v0, :piko
                sget-object v$reg, $ref->VERY_HIGH:$ref  
                """.trimIndent(),
                ExternalLabel("piko", method.getInstruction(index)),
            )

            settingsStatusLoadFingerprint.enableSettings("enableForceHD")
        }
    }
