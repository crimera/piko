package app.crimera.patches.twitter.timeline.forceHD

import app.crimera.patches.twitter.entity.entityGenerator
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.Constants
import app.crimera.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.enableSettings
import app.crimera.utils.extractDescriptors
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c

private val playerSupportFingerprint =
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
        dependsOn(settingsPatch, entityGenerator)

        execute {

            playerSupportFingerprint.method.apply {

                val listReg = (instructions.first { it.opcode == Opcode.INVOKE_INTERFACE} as BuilderInstruction35c).registerC

                val igetObjIndex = instructions.first { it.opcode == Opcode.IGET_OBJECT }.location.index

                addInstructions(
                    igetObjIndex +1,
                    """
                        invoke-static {v$listReg},${Constants.PATCHES_DESCRIPTOR}/TimelineEntry;->timelineVideos(Ljava/util/List;)Ljava/util/List;
                        move-result-object v$listReg
                    """.trimIndent())

                settingsStatusLoadFingerprint.enableSettings("enableForceHD")
            }

        }
    }
