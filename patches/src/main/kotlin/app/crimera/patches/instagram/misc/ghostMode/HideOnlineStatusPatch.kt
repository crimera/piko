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
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_CLASS_DESCRIPTOR = "$PATCHES_DESCRIPTOR/Presence;"

private const val REALTIME_CLIENT_MANAGER_CLASS = "Lcom/instagram/realtimeclient/RealtimeClientManager;"
private const val UPDATE_APP_STATE_RUNNABLE_CLASS =
    "Lcom/instagram/realtimeclient/RealtimeClientManager\$updateAppStateInternal\$1;"
private const val PRESENCE_UPI_PACKAGE = "Lcom/facebook/presence/model/upi"
private const val PRESENCE_STATUS_CLASS = "$PRESENCE_UPI_PACKAGE/PresenceStatus;"
private const val PRESENCE_WRITE_REQUEST_CLASS = "$PRESENCE_UPI_PACKAGE/PresenceWriteRequest;"

/**
 * The single constructor every outgoing presence write funnels through, from both
 * `LX/6uW` (stream setup and teardown) and `LX/8zp` (foreground/background transitions).
 * The class survives obfuscation because it is `@Serializable`.
 */
private object PresenceWriteRequestConstructorFingerprint : Fingerprint(
    definingClass = PRESENCE_WRITE_REQUEST_CLASS,
    name = "<init>",
    returnType = "V",
    parameters =
        listOf(
            "$PRESENCE_UPI_PACKAGE/AppState;",
            "$PRESENCE_UPI_PACKAGE/PresencePollingMode;",
            PRESENCE_STATUS_CLASS,
            "$PRESENCE_UPI_PACKAGE/PresenceWriteRequestType;",
            "Ljava/lang/Long;",
            "Ljava/lang/String;",
        ),
)

/**
 * Builds the initial MQTT subscription list, which contains `/disable_presence_reporting`
 * only when the UPI rollout flag (`LX/DA7;->A03`) is on.
 */
private object CreateMqttClientFingerprint : Fingerprint(
    definingClass = REALTIME_CLIENT_MANAGER_CLASS,
    name = "createMqttClient",
    strings = listOf("/disable_presence_reporting"),
)

/**
 * Calls `HET(isForegrounded, sendLegacyMqttPresence)` on the MQTT client, the only call site
 * of that interface method in the app.
 */
private object UpdateAppStateRunnableFingerprint : Fingerprint(
    definingClass = UPDATE_APP_STATE_RUNNABLE_CLASS,
    name = "run",
    returnType = "V",
)

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
            PresenceWriteRequestConstructorFingerprint.method.addInstructions(
                0,
                """
                invoke-static {p3}, $EXTENSION_CLASS_DESCRIPTOR->overridePresenceStatus($PRESENCE_STATUS_CLASS)$PRESENCE_STATUS_CLASS
                move-result-object p3
                """.trimIndent(),
            )

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
                        invoke-static {v$flagRegister}, $EXTENSION_CLASS_DESCRIPTOR->shouldDisablePresenceReporting(Z)Z
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
                    invoke-static {v$sendPresenceRegister}, $EXTENSION_CLASS_DESCRIPTOR->shouldSendLegacyPresence(Z)Z
                    move-result v$sendPresenceRegister
                    """.trimIndent(),
                )
            }

            enableSettings("hideOnlineStatus")
        }
    }
