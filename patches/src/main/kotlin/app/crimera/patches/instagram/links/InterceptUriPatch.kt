/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links

import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

object TigonServiceLayerStartRequestFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/api/tigon/TigonServiceLayer;",
    name = "startRequest",
)

@Suppress("unused")
val interceptUriPatch =
    bytecodePatch(
        description = "Intercept uri to block.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            TigonServiceLayerStartRequestFingerprint.method.apply {
                val firstIfEqzIndex = indexOfFirstInstruction(Opcode.IF_EQZ)

                val getUriObjectInstruction = instructions.last { it.opcode == Opcode.IGET_OBJECT && it.location.index < firstIfEqzIndex }

                val uriRegister = getUriObjectInstruction.registersUsed[0]

                addInstructions(
                    getUriObjectInstruction.location.index + 1,
                    """
                    invoke-static/range { v$uriRegister .. v$uriRegister }, ${Constants.LINKS_DESCRIPTOR}->interceptUri(Ljava/net/URI;)V
                    """.trimIndent(),
                )
            }
        }
    }
