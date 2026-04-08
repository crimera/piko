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
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val makeEphemeralPermanentPatch =
    bytecodePatch(
        name = "Make ephemeral media permanent",
        description = "changes view once, view twice media to permanent view. Do note older ephemeral media might look blank.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)
        execute {

            EphemeralMediaJsonParserFingerprint.apply {
                val strIndex = stringMatches[0].index
                method.apply {
                    val viewModeIPutObjectInstruction =
                        getInstruction(
                            indexOfFirstInstruction(strIndex, Opcode.IPUT_OBJECT),
                        )
                    val viewModeStrRegister = viewModeIPutObjectInstruction.registersUsed[0]

                    addInstructions(
                        viewModeIPutObjectInstruction.location.index,
                        """
                        invoke-static {v$viewModeStrRegister}, ${PREF_DESCRIPTOR}->unlimitedReplaysOnEphemeralMedia(Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v$viewModeStrRegister
                        """.trimIndent(),
                    )
                }
            }
            enableSettings("unlimitedReplaysOnEphemeralMedia")
        }
    }
