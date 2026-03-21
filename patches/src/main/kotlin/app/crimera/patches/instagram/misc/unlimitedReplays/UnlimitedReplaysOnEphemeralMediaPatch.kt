/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.unlimitedReplays

import app.crimera.patches.instagram.misc.settings.settingsPatch
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
val unlimitedReplaysOnEphemeralMediaPatch =
    bytecodePatch(
        name = "Unlimited replays on ephemeral media",
        description = "Unlimited replays on view once, view twice media before they get expired",
        use = true,
    ) {
        compatibleWith("com.instagram.android")
        dependsOn(settingsPatch)
        execute {

            fun MutableMethod.injectCode(searchIndex: Int) {
                val firstIPutIndex = indexOfFirstInstruction(searchIndex, Opcode.IPUT)
                val IPutInstruction = getInstruction(firstIPutIndex)
                val viewCountRegister = IPutInstruction.registersUsed[0]

                addInstructions(
                    firstIPutIndex,
                    """
                     invoke-static {v$viewCountRegister}, $PREF_DESCRIPTOR->unlimitedReplaysOnEphemeralMedia(I)I
                    move-result v$viewCountRegister
                    """.trimIndent(),
                )
            }

            EphemeralMediaViewUpdate1Fingerprint.method
                .apply {
                    instructions
                    val strIndex = EphemeralMediaViewUpdate1Fingerprint.stringMatches[0].index
                    injectCode(strIndex)
                }

            EphemeralMediaViewUpdate2Fingerprint.method.apply {
                val strIndex = EphemeralMediaViewUpdate1Fingerprint.stringMatches[0].index
                injectCode(strIndex)
            }

            EphemeralMediaViewUpdate3Fingerprint.method.apply {
                injectCode(0)
            }

            enableSettings("unlimitedReplaysOnEphemeralMedia")
        }
    }
