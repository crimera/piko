/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.actionBar.inboxActionBarButton

import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.checkCast
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

object InboxActionBarBuilderFingerprint : Fingerprint(
    strings = listOf("PrebindActionBar"),
    filters = listOf(checkCast("Lcom/instagram/igds/components/actionbar/IgdsActionBar;")),
)

val inboxActionBarButtonPatch =
    bytecodePatch(
        description = "This patch is adds support for adding buttons on Inbox action bar.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        
        execute {

            InboxActionBarBuilderFingerprint.let {
                val castIndex = it.instructionMatches.first().index
                val viewGroupRegister = it.instructionMatches.first().getInstruction<OneRegisterInstruction>().registerA
                it.method.addInstructions(
                    castIndex + 1,
                    """
                    invoke-static {v$viewGroupRegister}, $ACTIONBAR_DESCRIPTOR->inboxActionBarButton(Landroid/view/ViewGroup;)V
                    """.trimIndent(),
                )
            }
        }
    }
