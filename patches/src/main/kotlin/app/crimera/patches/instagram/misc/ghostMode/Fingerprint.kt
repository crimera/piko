/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.ghostMode

import app.crimera.patches.instagram.utils.Constants.PRESENCE_STATUS_CLASS
import app.crimera.patches.instagram.utils.Constants.PRESENCE_UPI_PACKAGE
import app.crimera.patches.instagram.utils.Constants.PRESENCE_WRITE_REQUEST_CLASS
import app.crimera.patches.instagram.utils.Constants.REALTIME_CLIENT_MANAGER_CLASS
import app.crimera.patches.instagram.utils.Constants.UPDATE_APP_STATE_RUNNABLE_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags

// This fingerprint is also used in MarkAsRead patch.
object DMSeenFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    filters =
        listOf(
            string("mark_thread_seen-"),
        ),
)

/**
 * The single constructor every outgoing presence write funnels through, from both
 * `LX/6uW` (stream setup and teardown) and `LX/8zp` (foreground/background transitions).
 * The class survives obfuscation because it is `@Serializable`.
 */
internal object PresenceWriteRequestConstructorFingerprint : Fingerprint(
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
internal object CreateMqttClientFingerprint : Fingerprint(
    definingClass = REALTIME_CLIENT_MANAGER_CLASS,
    name = "createMqttClient",
    strings = listOf("/disable_presence_reporting"),
)

/**
 * Calls `HET(isForegrounded, sendLegacyMqttPresence)` on the MQTT client, the only call site
 * of that interface method in the app.
 */
internal object UpdateAppStateRunnableFingerprint : Fingerprint(
    definingClass = UPDATE_APP_STATE_RUNNABLE_CLASS,
    name = "run",
    returnType = "V",
)
