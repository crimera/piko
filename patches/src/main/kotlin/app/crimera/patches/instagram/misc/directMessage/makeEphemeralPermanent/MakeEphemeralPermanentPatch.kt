/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.directMessage.makeEphemeralPermanent

import app.crimera.patches.instagram.entity.messageInfoEntity.messageInfoEntity
import app.crimera.patches.instagram.misc.directMessage.saveAllMessages.saveAllMessagesPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

internal object EphemeralMediaJsonParserFingerprint : Fingerprint(
    custom = { methodDef, _ ->
        methodDef.name.lowercase().contains("parsefromjson")
    },
    returnType = "Ljava/lang/Object;",
    strings = listOf("url_expire_at_secs", "view_mode", "seen_count", "tap_models"),
)

@Suppress("unused")
val makeEphemeralPermanentPatch =
    bytecodePatch(
        name = "Make ephemeral media permanent",
        description = "Changes unexpired view once, view twice media to permanent view.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, messageInfoEntity, saveAllMessagesPatch)
        execute {

            EphemeralMediaJsonParserFingerprint.apply {
                val expireAtStringIndex = stringMatches[0].index
                val viewModeStringIndex = stringMatches[1].index
                method.apply {
                    val viewModeIPutObjectInstruction =
                        getInstruction(
                            indexOfFirstInstruction(viewModeStringIndex, Opcode.IPUT_OBJECT),
                        )

                    val viewModeInstructionExtraction = viewModeIPutObjectInstruction.fieldExtractor()
                    val ephemeralMediaClassName = extensionToClassName(viewModeInstructionExtraction.definingClass)
                    val viewModeFieldName = viewModeInstructionExtraction.name

                    val expireAtInstructionExtraction =
                        instructions
                            .last {
                                it.location.index < viewModeStringIndex &&
                                    it.opcode == Opcode.IPUT_OBJECT
                            }.fieldExtractor()
                    val expireAtFieldName = expireAtInstructionExtraction.name

                    val returnObjectInstruction = instructions.last { it.opcode == Opcode.RETURN_OBJECT }
                    val ephemeralMediaClassRegister = returnObjectInstruction.registersUsed[0]

                    val midIfEqInstruction = instructions.filter { it.opcode == Opcode.IF_EQ }[1]
                    val lastIfEqIndex = midIfEqInstruction.location.index
                    val registers = midIfEqInstruction.registersUsed
                    val registerA = registers[0]
                    val registerB = registers[1]

                    addInstructionsWithLabels(
                        lastIfEqIndex,
                        """
                        if-ne v$registerA, v$registerB, :piko
                        
                        iget-object v0, v$ephemeralMediaClassRegister, $ephemeralMediaClassName->$expireAtFieldName:Ljava/lang/Long;
                        iget-object v1, v$ephemeralMediaClassRegister, $ephemeralMediaClassName->$viewModeFieldName:Ljava/lang/String;
                        
                        invoke-static {v0, v1}, $PATCHES_DESCRIPTOR/EphemeralMediaPatch;->makeEphemeralMediaPermanent(Ljava/lang/Long;Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v1                        
                        
                        iput-object v1, v$ephemeralMediaClassRegister, $ephemeralMediaClassName->$viewModeFieldName:Ljava/lang/String;
                        return-object v$ephemeralMediaClassRegister
                        """.trimIndent(),
                        ExternalLabel("piko", midIfEqInstruction),
                    )
                }
            }
            enableSettings("unlimitedReplaysOnEphemeralMedia")
        }
    }
