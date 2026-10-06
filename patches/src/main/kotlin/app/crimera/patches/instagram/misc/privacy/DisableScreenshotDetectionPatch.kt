/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.privacy

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal object ScreenshotDetectorFingerprint : Fingerprint(
    strings = listOf("ig_android_story_screenshot_directory", "screenshot_detector"),
)

internal object AddFlagsToWindowFingerprint : Fingerprint(
    strings = listOf("Inconsistency in window FLAG_SECURE state detected! window state: "),
)

internal object DirectScreenshotCaptureTriggerFingerprint : Fingerprint(
    strings = listOf("igd_screenshot_capture"),
)

internal object ChatRecyclerViewRelatedFingerprint : Fingerprint(
    strings = listOf("Removed holder should be bound and it should come here only in pre-layout. Holder: "),
)

@Suppress("unused")
val disableScreenshotDetection =
    bytecodePatch(
        name = "Disable screenshot detection",
        description = "Disables screenshots detection in DM",
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            val PREF_CALL =
                """
                invoke-static {}, $PREF_DESCRIPTOR->disableScreenshotDetection()Z
                move-result
                """.trimIndent()

            // Thanks to MyInsta.
            ScreenshotDetectorFingerprint.apply {
                val strIndex = stringMatches[0].index

                method.apply {
                    val observerStartInvokeInstruction =
                        instructions.last {
                            it.location.index < strIndex &&
                                it.opcode == Opcode.INVOKE_VIRTUAL
                        }

                    val index = observerStartInvokeInstruction.location.index

                    val nextConstInstruction = getInstruction(index + 1)
                    val freeRegister = nextConstInstruction.registersUsed[0]

                    addInstructionsWithLabels(
                        index,
                        """
                        $PREF_CALL v$freeRegister
                        if-nez v$freeRegister, :piko
                        """.trimIndent(),
                        ExternalLabel("piko", nextConstInstruction),
                    )

                    val secureWindowState = AddFlagsToWindowFingerprint.method
                    val secureWindowRequest = AddFlagsToWindowFingerprint.classDef.methods.singleOrNull {
                        it.parameterTypes == listOf("Landroid/view/Window;", "Ljava/lang/String;") &&
                            it.returnType == "V" &&
                            it.implementation != null &&
                            it.instructions.any { instruction ->
                                instruction.getReference<MethodReference>()?.toString() ==
                                    "Landroid/view/Window;->setFlags(II)V"
                            }
                    } ?: throw PatchException("Expected one screenshot protection request method")

                    // DM viewers and threads request protection directly, bypassing state reconciliation.
                    listOf(secureWindowState, secureWindowRequest).forEach { secureMethod ->
                        if (AccessFlags.STATIC.isSet(secureMethod.accessFlags) ||
                            secureMethod.returnType != "V" ||
                            secureMethod.parameterTypes.firstOrNull() != "Landroid/view/Window;" ||
                            secureMethod.implementation!!.registerCount - secureMethod.parameterTypes.size - 1 < 2
                        ) {
                            throw PatchException("Unexpected screenshot protection method layout")
                        }
                        secureMethod.addInstructionsWithLabels(
                            0,
                            """
                            ${PREF_CALL} v0
                            if-eqz v0, :piko
                            move-object/from16 v0, p1
                            if-eqz v0, :return
                            const/16 v1, 0x2000
                            invoke-virtual {v0, v1}, Landroid/view/Window;->clearFlags(I)V
                            :return
                            return-void
                            """.trimIndent(),
                            ExternalLabel("piko", secureMethod.getInstruction(0)),
                        )
                    }

                    // Thanks to InstaPro
                    DirectScreenshotCaptureTriggerFingerprint.method.apply {
                        addInstructionsWithLabels(
                            0,
                            """
                            $PREF_CALL v0
                            if-eqz v0, :piko
                            return-void
                            """.trimIndent(),
                            ExternalLabel("piko", getInstruction(0)),
                        )
                    }

                    // Thanks to InstaPro
                    ChatRecyclerViewRelatedFingerprint.method.apply {
                        val firstIntAdderIndex = indexOfFirstInstruction(Opcode.AND_INT_2ADDR)
                        val flagIntIndex = firstIntAdderIndex - 2
                        val freeRegister = getInstruction(flagIntIndex).registersUsed[0]

                        val firstIGetObjectAfterFlagIntIndex = indexOfFirstInstruction(firstIntAdderIndex, Opcode.IGET_OBJECT)

                        addInstructionsWithLabels(
                            flagIntIndex,
                            """
                            $PREF_CALL v$freeRegister
                            if-nez v$freeRegister, :piko
                            """.trimIndent(),
                            ExternalLabel("piko", getInstruction(firstIGetObjectAfterFlagIntIndex)),
                        )
                    }

                    enableSettings("disableScreenshotDetection")
                }
            }
        }
    }
