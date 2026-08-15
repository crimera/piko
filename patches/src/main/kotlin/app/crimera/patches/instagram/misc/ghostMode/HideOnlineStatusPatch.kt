/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.ghostMode

import app.crimera.patches.instagram.misc.actionBar.chatActionBarButton.chatActionBarButtonPatch
import app.crimera.patches.instagram.misc.actionBar.inboxActionBarButton.inboxActionBarButtonPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PRESENCE_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.PRESENCE_STATUS_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

/**
 * Instagram's native "Show activity status" toggle is bilateral: switching it off also stops
 * the app from reading the friends' status. Presence is reported through three independent
 * channels, and this patch silences all of them without touching any incoming one.
 */
@Suppress("unused")
val hideOnlineStatusPatch =
    bytecodePatch(
        name = "Hide online status",
        description = "Hides your activity status while still showing the activity status of others",
    ) {
        dependsOn(settingsPatch, chatActionBarButtonPatch, inboxActionBarButtonPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            // DGW/UPI channel. Hooking the constructor covers all of its call sites at once.
            PresenceWriteRequestConstructorFingerprint.method.apply {
                val superCallIndex = indexOfFirstInstruction(Opcode.INVOKE_DIRECT)

                addInstructions(
                    superCallIndex + 1,
                    """
                    invoke-static {p3}, $PRESENCE_DESCRIPTOR->overridePresenceStatus($PRESENCE_STATUS_CLASS)$PRESENCE_STATUS_CLASS
                    move-result-object p3
                    """.trimIndent(),
                )
            }

            // MQTT connection inference, silenced by subscribing to the topic that the app
            // otherwise only subscribes to when the UPI rollout flag is on.
            CreateMqttClientFingerprint.apply {
                val topicIndex = stringMatches[0].index

                method.apply {
                    // Result of the LX/DA7;->A03 check that gates adding the topic.
                    val flagResultInstruction =
                        instructions.last {
                            it.location.index < topicIndex && it.opcode == Opcode.MOVE_RESULT
                        }

                    val flagRegister = flagResultInstruction.registersUsed[0]

                    addInstructions(
                        flagResultInstruction.location.index + 1,
                        """
                        invoke-static {v$flagRegister}, $PRESENCE_DESCRIPTOR->shouldDisablePresenceReporting(Z)Z
                        move-result v$flagRegister
                        """.trimIndent(),
                    )
                }
            }

            // Legacy "/t_fs" publish, dropped by clearing the second argument of
            // HET(isForegrounded, sendLegacyMqttPresence).
            UpdateAppStateRunnableFingerprint.method.apply {
                val hetCallInstruction = instructions.last { it.opcode == Opcode.INVOKE_INTERFACE }
                val hetCallIndex = hetCallInstruction.location.index
                val sendPresenceRegister = hetCallInstruction.registersUsed[2]

                addInstructions(
                    hetCallIndex,
                    """
                    invoke-static {v$sendPresenceRegister}, $PRESENCE_DESCRIPTOR->shouldSendLegacyPresence(Z)Z
                    move-result v$sendPresenceRegister
                    """.trimIndent(),
                )
            }

            enableSettings("hideOnlineStatus")
        }
    }
