package app.crimera.patches.twitter.premium.unlockdownloads

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.extensions.InstructionExtensions.instructions
import app.revanced.patcher.extensions.InstructionExtensions.removeInstruction
import app.revanced.patcher.fingerprint
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private val downloadPatchFingerprint =
    fingerprint {
        opcodes(
            Opcode.IF_EQ,
            Opcode.SGET_OBJECT,
            Opcode.GOTO_16,
            Opcode.NEW_INSTANCE,
        )

        strings(
            "mediaEntity",
            "media_options_sheet",
        )
    }

private val fileDownloaderFingerprint =
    fingerprint {
        returns("Z")

        strings("mediaEntity", "url")

        opcodes(Opcode.IF_EQZ)
    }

// credits @revanced
private val immersiveBottomSheetPatchFingerprint =
    fingerprint {
        returns("V")

        strings("captionsState")

        accessFlags(AccessFlags.PUBLIC, AccessFlags.CONSTRUCTOR)
    }

private val mediaEntityFingerprint =
    fingerprint {
        opcodes(Opcode.IGET_BOOLEAN)
        custom { it, _ ->
            it.definingClass == "Lcom/twitter/model/json/core/JsonMediaEntity;"
        }
    }

// Credits to @iKirby
@Suppress("unused")
val downloadPatch =
    bytecodePatch(
        name = "Download patch",
        description = "Unlocks the ability to download videos and gifs from Twitter/X",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {

            val method = downloadPatchFingerprint.method
            val instructions = method.instructions

            val first_if_loc = instructions.first { it.opcode == Opcode.IF_EQ }.location.index
            val reg = method.getInstruction<TwoRegisterInstruction>(first_if_loc)
            val r1 = reg.registerA
            val r2 = reg.registerB

            // //add support for gif
            method.addInstructionsWithLabels(
                first_if_loc + 1,
                """
               const/4 v$r2, 0x2
               
               if-eq v$r1, v$r2, :cond_1212
            """,
                ExternalLabel("cond_1212", method.instructions.first { it.opcode == Opcode.NEW_INSTANCE }),
            )

            // enable download for all media
            instructions.first { it.opcode == Opcode.IGET_BOOLEAN }.location.index.apply {
                method.removeInstruction(this)
                method.removeInstruction(this)
            }

            val method2 = fileDownloaderFingerprint.method
            val instructions2 = method2.instructions
            val first_if2_loc = instructions2.first { it.opcode == Opcode.IF_EQZ }.location.index
            val r3 = method2.getInstruction<OneRegisterInstruction>(first_if2_loc).registerA

            // remove premium restriction
            method2.addInstructions(
                first_if2_loc,
                """
                const v$r3, true
                """.trimIndent(),
            )

            // force video downloadable
            val method3 = mediaEntityFingerprint.method
            val instructions3 = method3.instructions
            val loc = instructions3.last { it.opcode == Opcode.IGET_BOOLEAN }.location.index
            val r4 = method3.getInstruction<TwoRegisterInstruction>(loc).registerA

            method3.addInstructions(
                loc + 1,
                """
                const v$r4, true
                """.trimIndent(),
            )

            // force add download option in immersive bottomsheet
            val method4 = immersiveBottomSheetPatchFingerprint.method
            val instructions4 = method4.instructions

            val last_iput_loc = instructions4.last { it.opcode == Opcode.IPUT_BOOLEAN }.location.index
            val iput_reg = method4.getInstruction<OneRegisterInstruction>(last_iput_loc).registerA
            method4.addInstruction(
                last_iput_loc,
                """
                const v${iput_reg}, 0x1
                """.trimIndent(),
            )

            settingsStatusLoadFingerprint.enableSettings("enableVidDownload")
        }
    }
