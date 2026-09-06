/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.ghostMode

import app.crimera.patches.instagram.misc.actionBar.mainFeedActionBarButton.mainFeedActionBarButtonPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.ACTIONBAR_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.resourceLiteral
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

object InboxButtonFingerprint : Fingerprint(
    filters = listOf(resourceLiteral(ResourceType.ID, "action_bar_inbox_button")),
)

@Suppress("unused")
val longPressInboxGhostModePatch =
    bytecodePatch(
        name = "Long press inbox to toggle ghost mode",
        description = "Long press the inbox button on the main feed to toggle ghost mode.",
    ) {
        dependsOn(settingsPatch, resourceMappingPatch, mainFeedActionBarButtonPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            InboxButtonFingerprint.matchAll().forEach { match ->
                match.method.apply {
                    match.instructionMatches.reversed().forEach { instructionMatch ->
                        val setIdIndex = indexOfFirstInstruction(instructionMatch.index, Opcode.INVOKE_VIRTUAL)
                        val setIdInstruction = getInstruction(setIdIndex)
                        val viewRegister = setIdInstruction.registersUsed[0]

                        addInstruction(
                            setIdIndex + 1,
                            "invoke-static {v$viewRegister}, $ACTIONBAR_DESCRIPTOR->hookInboxButton(Landroid/view/View;)V",
                        )
                    }
                }
            }
            enableSettings("longPressInboxGhostMode")
        }
    }
