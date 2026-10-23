/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links

import app.crimera.patches.instagram.utils.Constants
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

object TigonServiceLayerStartRequestFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/api/tigon/TigonServiceLayer;",
    filters = listOf(
        fieldAccess(type = "Ljava/net/URI;", opcode = Opcode.IGET_OBJECT),
        methodCall(smali = "Ljava/net/URI;->getHost()Ljava/lang/String;", location = MatchAfterImmediately()),
    ),
)

@Suppress("unused")
val interceptUriPatch =
    bytecodePatch(
        description = "Intercept uri to block.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            TigonServiceLayerStartRequestFingerprint.matchSingle().let {
                val uriIndex = it.instructionMatches.first().index
                val fieldRegisters = it.instructionMatches.first().instruction.registersUsed
                val hostRegisters = it.instructionMatches.last().instruction.registersUsed
                if (fieldRegisters.size != 2 || hostRegisters != listOf(fieldRegisters[0])) {
                    throw PatchException("Expected the request URI field to reach getHost")
                }
                val uriRegister = fieldRegisters[0]

                it.method.addInstructions(
                    uriIndex + 1,
                    """
                    invoke-static/range { v$uriRegister .. v$uriRegister }, ${Constants.LINKS_DESCRIPTOR}->interceptUri(Ljava/net/URI;)V
                    """.trimIndent(),
                )
            }
        }
    }
