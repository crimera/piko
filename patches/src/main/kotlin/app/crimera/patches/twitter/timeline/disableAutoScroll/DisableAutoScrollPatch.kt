/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.disableAutoScroll

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode

private object DisableAutoScrollFingerprint : Fingerprint(
    returnType = "V",
    strings =
        listOf(
            "applicationManager",
            "releaseCompletable",
            "preferences",
            "twSystemClock",
            "launchTracker",
            "cold_start_launch_time_millis",
        ),
)

private object XLiteHomeReselectFingerprint : Fingerprint(
    parameters = listOf("Z", "Z"),
    returnType = "Z",
    filters =
        listOf(
            string("timeline_auto_refresh_on_foreground_timeout_millis"),
            methodCall(
                opcode = Opcode.INVOKE_INTERFACE,
                name = "getInt",
                parameters = listOf("Ljava/lang/String;", "I"),
                returnType = "I",
            ),
            opcode(
                opcode = Opcode.MOVE_RESULT,
                location = MatchAfterImmediately(),
            ),
        ),
)

private object XLiteLifecycleAutoRefreshFingerprint : Fingerprint(
    name = "invokeSuspend",
    parameters = listOf("Ljava/lang/Object;"),
    returnType = "Ljava/lang/Object;",
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.SGET_OBJECT,
                name = "VIEWPORT_AWARE_AUTO_REFRESH",
            ),
            fieldAccess(
                opcode = Opcode.SGET_OBJECT,
                name = "AUTO_REFRESH",
            ),
        ),
)

// credits to @Ouxyl
@Suppress("unused")
val disableAutoScrollPatch =
    bytecodePatch(
        name = "Disable auto timeline scroll on launch",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            DisableAutoScrollFingerprint.classDef.methods.last().addInstructions(
                0,
                """
                const v0,0x0
                return v0
                """.trimIndent(),
            )

            val homeReselectMatches = XLiteHomeReselectFingerprint.matchAll()
            if (homeReselectMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite home reselect handler, found ${homeReselectMatches.size}: " +
                        homeReselectMatches.joinToString { it.originalMethod.toString() },
                )
            }

            homeReselectMatches.single().method.addInstructions(
                0,
                """
                const/4 v0, 0x0
                return v0
                """.trimIndent(),
            )

            val lifecycleAutoRefreshMatches = XLiteLifecycleAutoRefreshFingerprint.matchAll()
            if (lifecycleAutoRefreshMatches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite lifecycle auto-refresh coroutine, found " +
                        "${lifecycleAutoRefreshMatches.size}: " +
                        lifecycleAutoRefreshMatches.joinToString { it.originalMethod.toString() },
                )
            }

            lifecycleAutoRefreshMatches.single().method.addInstructions(0, "return-object p1")
            enableSettings("disableAutoTimelineScroll")
        }
    }
