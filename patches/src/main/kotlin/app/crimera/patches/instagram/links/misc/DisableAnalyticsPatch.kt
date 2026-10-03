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
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private object IgBloksFullScreenOpenFingerprint : Fingerprint(
    returnType = "V",
    parameters =
        listOf(
            "Landroid/content/Context;",
            "Lcom/instagram/bloks/hosting/IgBloksScreenConfig;",
        ),
    strings = listOf("BKDataFetcher.fetch"),
    custom = { method, _ ->
        !AccessFlags.STATIC.isSet(method.accessFlags)
    }
)

private object EventBuilderCommitFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    strings = listOf(
        "EventBuilder was not acquired: BaseParameters null.",
        "Must call ejectBaseParameters and ejectExtraParameters before release",
    ),
    custom = { method, _ ->
        !AccessFlags.STATIC.isSet(method.accessFlags)
    }
)

@Suppress("unused")
val disableAnalyticsPatch =
    bytecodePatch(
        name = "Disable analytics",
        description = "Block analytics that are sent to Instagram/Facebook servers.",
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
            IgBloksFullScreenOpenFingerprint.apply {
                val openerClass = classDef.type
                method.apply {
                    val appIdField =
                        instructions
                            .asSequence()
                            .filter { it.opcode == Opcode.IGET_OBJECT }
                            .mapNotNull {
                                (it as? ReferenceInstruction)?.reference as? FieldReference
                            }.firstOrNull {
                                it.definingClass == openerClass &&
                                        it.type == "Ljava/lang/String;"
                            } ?: throw PatchException("Bloks full-screen opener app ID field not found")

                    addInstructionsWithLabels(
                        0,
                        """
                        move-object/from16 v0, p0
                        iget-object v0, v0, $appIdField
                        invoke-static {v0}, $LINKS_DESCRIPTOR->shouldBlockOnboardingScreen(Ljava/lang/String;)Z
                        move-result v0
                        if-eqz v0, :piko_continue
                        return-void
                        """.trimIndent(),
                        ExternalLabel("piko_continue", getInstruction(0)),
                    )
                }
            }

            // Skip event dispatch when analytics are disabled.
            // This prevents event delivery to the native logger queue.
            EventBuilderCommitFingerprint.method.apply {
                val serializerStringIndex = instructions.indexOfFirst {
                    ((it as? ReferenceInstruction)?.reference as? StringReference)?.string == "Failed to serialize params"
                }

                if (serializerStringIndex == -1) {
                    throw PatchException("EventBuilder serialization failure string not found")
                }

                val dispatchInstructionIndex = instructions.indices
                    .filter { it > serializerStringIndex }
                    .firstOrNull { idx ->
                        val instruction = instructions[idx]
                        if (instruction.opcode != Opcode.INVOKE_VIRTUAL && instruction.opcode != Opcode.INVOKE_INTERFACE) {
                            return@firstOrNull false
                        }
                        val ref = (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return@firstOrNull false
                        ref.returnType == "V" && ref.parameterTypes.size == 1 &&
                            ref.parameterTypes.single() != "Ljava/lang/String;" &&
                            ref.parameterTypes.single() != "Ljava/lang/Throwable;"
                    } ?: throw PatchException("EventBuilder commit dispatch instruction not found")

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

