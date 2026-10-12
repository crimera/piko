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
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

object TigonServiceLayerStartRequestFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/api/tigon/TigonServiceLayer;",
    name = "startRequest",
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
            TigonServiceLayerStartRequestFingerprint.let {
                val uriIndex = it.instructionMatches.first().index
                val uriRegister = it.instructionMatches.first().getInstruction<OneRegisterInstruction>().registerA

                it.method.addInstructions(
                    uriIndex + 1,
                    """
                    invoke-static/range { v$uriRegister .. v$uriRegister }, ${Constants.LINKS_DESCRIPTOR}->interceptUri(Ljava/net/URI;)V
                    """.trimIndent(),
                )
            }
        }
    }
