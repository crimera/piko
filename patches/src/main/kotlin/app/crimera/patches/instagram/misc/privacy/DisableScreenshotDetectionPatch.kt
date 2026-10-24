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
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val FLAG_SECURE = 0x2000

internal object ScreenshotDetectorFingerprint : Fingerprint(
    strings = listOf("screenshot_detector"),
    filters = listOf(
        // Screenshot directory observer.
        methodCall(smali = "Landroid/os/FileObserver;->startWatching()V"),
        opcode(Opcode.CONST_4, location = MatchAfterImmediately()),
        string("ig_android_story_screenshot_directory"),
    ),
)

internal object AddFlagsToWindowFingerprint : Fingerprint(
    strings = listOf("Inconsistency in window FLAG_SECURE state detected! window state: "),
)

internal object SecureWindowRequestFingerprint : Fingerprint(
    classFingerprint = AddFlagsToWindowFingerprint,
    returnType = "V",
    parameters = listOf("Landroid/view/Window;", "Ljava/lang/String;"),
    filters = listOf(methodCall(smali = "Landroid/view/Window;->setFlags(II)V")),
)

internal object DirectScreenshotCaptureTriggerFingerprint : Fingerprint(
    strings = listOf("igd_screenshot_capture"),
)

internal object ChatRecyclerViewRelatedFingerprint : Fingerprint(
    strings = listOf("Removed holder should be bound and it should come here only in pre-layout. Holder: "),
    filters = listOf(
        // Secure flag of the view holder.
        literal(FLAG_SECURE),
        fieldAccess(type = "I", opcode = Opcode.IGET, location = MatchAfterImmediately()),
        opcode(Opcode.AND_INT_2ADDR, location = MatchAfterImmediately()),
        fieldAccess(opcode = Opcode.IGET_OBJECT),
    ),
)

@Suppress("unused")
val disableScreenshotDetection =
    bytecodePatch(
        name = "Disable screenshot detection",
        description = "Disables screenshots detection in DM",
        default = false,
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
                method.apply {
                    val index = instructionMatches.first().index
                    val nextConstInstruction = getInstruction<OneRegisterInstruction>(index + 1)
                    val freeRegister = nextConstInstruction.registerA

                    addInstructionsWithLabels(
                        index,
                        """
                        $PREF_CALL v$freeRegister
                        if-nez v$freeRegister, :piko
                        """.trimIndent(),
                        ExternalLabel("piko", nextConstInstruction),
                    )

                    val secureWindowState = AddFlagsToWindowFingerprint.method
                    val secureWindowRequest = SecureWindowRequestFingerprint.method

                    // DM viewers and threads request protection directly, bypassing state reconciliation.
                    listOf(secureWindowState, secureWindowRequest).forEach { secureMethod ->
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
                        val flagIntIndex = ChatRecyclerViewRelatedFingerprint.instructionMatches.first().index
                        val freeRegister = ChatRecyclerViewRelatedFingerprint.instructionMatches.first().getInstruction<OneRegisterInstruction>().registerA
                        val firstIGetObjectAfterFlagIntIndex = ChatRecyclerViewRelatedFingerprint.instructionMatches.last().index

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
