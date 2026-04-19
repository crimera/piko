/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.makeEphemeralPermanent

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.extensionToClassName
import app.crimera.utils.fieldExtractor
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val makeEphemeralPermanentPatch =
    bytecodePatch(
        name = "Make ephemeral media permanent",
        description = "Changes unexpired view once, view twice media to permanent view.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)
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

                    val expireAtIPutObjectIndex = indexOfFirstInstruction(expireAtStringIndex, Opcode.IPUT_OBJECT)
                    val expireAtIPutObjectInstruction = getInstruction(expireAtIPutObjectIndex)

                    val expireAtLongRegister = viewModeIPutObjectInstruction.registersUsed[0]
                    val ephemeralMediaClassRegister = viewModeIPutObjectInstruction.registersUsed[1]
                    val dummyRegister = getInstruction(expireAtIPutObjectIndex - 2).registersUsed[1]

                    addInstructions(
                        expireAtIPutObjectIndex + 1,
                        """
                        iget-object v$dummyRegister, v$ephemeralMediaClassRegister, $ephemeralMediaClassName->$viewModeFieldName:Ljava/lang/String;
                        invoke-static {v$dummyRegister, v$expireAtLongRegister}, $PREF_DESCRIPTOR->unlimitedReplaysOnEphemeralMedia(Ljava/lang/String;Ljava/lang/Long;)Ljava/lang/String;
                        move-result-object v$expireAtLongRegister
                        iput-object v$expireAtLongRegister, v$ephemeralMediaClassRegister, $ephemeralMediaClassName->$viewModeFieldName:Ljava/lang/String;
                        """.trimIndent(),
                    )
                }
            }
            enableSettings("unlimitedReplaysOnEphemeralMedia")
        }
    }
