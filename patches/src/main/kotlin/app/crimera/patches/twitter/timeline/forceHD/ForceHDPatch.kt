package app.crimera.patches.twitter.timeline.forceHD

import app.crimera.patches.twitter.entity.entityGenerator
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.utils.Constants
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction35c

private object PlayerSupportFingerprint : Fingerprint(
    definingClass = "/av/player/support/",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC),
    custom = { methodDef, _ ->
        methodDef.parameters.size == 2
    }
)

@Suppress("unused")
val forceHDPatch =
    bytecodePatch(
        name = "Enable force HD videos",
        description = "Videos will be played in highest quality always",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch, entityGenerator)

        execute {

            PlayerSupportFingerprint.method.apply {

                val listReg = (instructions.first { it.opcode == Opcode.INVOKE_INTERFACE} as BuilderInstruction35c).registerC

                val igetObjIndex = instructions.first { it.opcode == Opcode.IGET_OBJECT }.location.index

                addInstructions(
                    igetObjIndex +1,
                    """
                        invoke-static {v$listReg},${Constants.PATCHES_DESCRIPTOR}/TimelineEntry;->timelineVideos(Ljava/util/List;)Ljava/util/List;
                        move-result-object v$listReg
                    """.trimIndent())

                SettingsStatusLoadFingerprint.enableSettings("enableForceHD")
            }

        }
    }
