/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.misc

import app.crimera.patches.instagram.links.interceptUriPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.LINKS_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.fieldReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

private object IgBloksFullScreenOpenFingerprint : Fingerprint(
    returnType = "V",
    parameters =
        listOf(
            "Landroid/content/Context;",
            "Lcom/instagram/bloks/hosting/IgBloksScreenConfig;",
        ),
    strings = listOf("BKDataFetcher.fetch"),
    filters = listOf(
        // App id of the opened screen.
        fieldAccess(definingClass = "this", type = "Ljava/lang/String;", opcode = Opcode.IGET_OBJECT),
    ),
)

private object EventBuilderCommitFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    strings = listOf(
        "EventBuilder was not acquired: BaseParameters null.",
        "Must call ejectBaseParameters and ejectExtraParameters before release",
    ),
    filters = listOf(
        string("Failed to serialize params"),
        // Event dispatch.
        methodCall(
            parameters = listOf("L"),
            returnType = "V",
            opcodes = listOf(Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_INTERFACE),
        ),
    ),
)

@Suppress("unused")
val disableAnalyticsPatch =
    bytecodePatch(
        name = "Disable analytics",
        description = "Block analytics that are sent to Instagram/Facebook servers.",
        default = false,
    ) {
        dependsOn(settingsPatch, interceptUriPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            enableSettings("disableAnalytics")

            // Disable onboarding permission prompts.
            // If this patch is applied and the setting is on, the "Set up on new device" screen,
            // which requests permission for location and contacts, keeps reappearing even after
            // you have allowed or skipped it.
            // This is caused by information that the prompt has already been seen is not sent to
            // the server.
            // Therefore, this patch needs to block it as well.
            IgBloksFullScreenOpenFingerprint.let {
                val appIdField = it.instructionMatches.first().fieldReference

                it.method.addInstructionsWithLabels(
                    0,
                    """
                    move-object/from16 v0, p0
                    iget-object v0, v0, $appIdField
                    invoke-static {v0}, $LINKS_DESCRIPTOR->shouldBlockOnboardingScreen(Ljava/lang/String;)Z
                    move-result v0
                    if-eqz v0, :piko_continue
                    return-void
                    """.trimIndent(),
                    ExternalLabel("piko_continue", it.method.getInstruction(0)),
                )
            }

            // Skip event dispatch when analytics are disabled.
            // This prevents event delivery to the native logger queue.
            EventBuilderCommitFingerprint.method.apply {
                val dispatchInstructionIndex = EventBuilderCommitFingerprint.instructionMatches.last().index
                val dispatchInstruction = getInstruction(dispatchInstructionIndex)
                val freeRegister = findFreeRegister(dispatchInstructionIndex, dispatchInstruction.registersUsed)
                val nextInstruction = getInstruction(dispatchInstructionIndex + 1)

                addInstructionsWithLabels(
                    dispatchInstructionIndex,
                    """
                    $PREF_CALL_DESCRIPTOR->disableAnalytics()Z
                    move-result v$freeRegister
                    if-nez v$freeRegister, :piko_skip_dispatch
                    """.trimIndent(),
                    ExternalLabel("piko_skip_dispatch", nextInstruction),
                )
            }
        }
    }

