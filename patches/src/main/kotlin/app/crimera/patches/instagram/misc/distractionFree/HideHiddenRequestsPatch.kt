/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.distractionFree

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

// Creates the row of the message requests page that opens the hidden requests, now called spam: it inflates the row and returns its holder.
private object HiddenRequestsRowFingerprint : Fingerprint(
    filters =
        listOf(
            resourceLiteral(ResourceType.LAYOUT, "pending_threads_spam_folder_row"),
            methodCall(returnType = "Landroid/view/View;", location = MatchAfterWithin(2)),
            opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        ),
)

@Suppress("unused")
val hideHiddenRequestsPatch =
    bytecodePatch(
        name = "Hide hidden requests",
        description = "Hides the hidden requests (spam) button in the message requests page",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            HiddenRequestsRowFingerprint.apply {
                val inflated = instructionMatches.last()
                val row = inflated.getInstruction<OneRegisterInstruction>().registerA
                // The view is passed as a range, so its register does not have to be below v16.
                method.addInstruction(
                    inflated.index + 1,
                    "invoke-static/range {v$row .. v$row}, $PATCHES_DESCRIPTOR/dm/HideHiddenRequests;->hideRow(Landroid/view/View;)V",
                )
            }

            enableSettings("hideHiddenRequests")
        }
    }
