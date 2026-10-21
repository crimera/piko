/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.telemetry

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.findInstructionIndicesReversedOrThrow
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import org.w3c.dom.Element

private fun Instruction.isFalcoLogCall(): Boolean =
    (opcode == Opcode.INVOKE_STATIC || opcode == Opcode.INVOKE_STATIC_RANGE) &&
        getReference<MethodReference>()?.let {
            it.definingClass == "Lcom/facebook/falco/fabric/FFSingletonJNILogger;" &&
                it.name.startsWith("log") && it.returnType == "V"
        } == true

private object FalcoLogCallerFingerprint : Fingerprint(
    custom = { method, _ ->
        method.implementation?.instructions?.any { it.isFalcoLogCall() } == true
    },
)

/**
 * Manifest patch to:
 * - Disable all persistent background daemons (:mqtt, :fbns, :fdidsync, :pretosproc, :honolulu, etc.)
 * - Disable cross-app tracking content providers (USDID, FamilyID, PhoneID, InstallReferrer)
 * - Disable background sync workers, telemetry upload services, and boot/connectivity receivers
 * - Strip isolated secondary processes from all components while keeping Android OS declarations intact
 */
val disableBackgroundManifestPatch =
    resourcePatch(
        name = "Disable background components",
        description = "Disables background services, receivers, tracking providers, and forces single-process execution.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            document("AndroidManifest.xml").use { document ->
                val root = document.documentElement
                val appNode = root.getElementsByTagName("application").item(0) as? Element ?: return@use

                val blockedPermissions =
                    setOf(
                        "android.permission.ACCESS_ADSERVICES_AD_ID",
                        "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
                        "com.instagram.fbpermission.PROVIDER_TRIGGER_REALTIME_KEEPALIVE",
                        "com.instagram.android.fbpermission.PROVIDER_READ_BACKGROUND_STATE",
                    )

                val usesPermissions = root.getElementsByTagName("uses-permission")
                for (i in usesPermissions.length - 1 downTo 0) {
                    val elem = usesPermissions.item(i) as Element
                    val name = elem.getAttribute("android:name")
                    if (name in blockedPermissions) {
                        root.removeChild(elem)
                    }
                }

                val blockedComponentPatterns =
                    listOf(
                        "analytics2",
                        "onefabric",
                        "AnalyticsService",
                        "AnalyticsJobService",
                        "AnalyticsUpload",
                        "Fbns",
                        "InappFbns",
                        "Mqtt",
                        "KeepWarmReceiver",
                        "LauncherSyncBootReceiver",
                        "LocalNotificationBootReceiver",
                        "SchedulerNetworkChangeReceiver",
                        "BackgroundSyncJobService",
                        "CCUJobService",
                        "DelayedWorkerService",
                        "SystemJobService",
                        "SystemForegroundService",
                        "RemoteWorkManagerService",
                        "OxygenScheduledInstallerJobService",
                        "IsManagedAppReceiver",
                        "IgBgFetchSchedulerService",
                        "TaskLifeDetectingService",
                        "HeroKeepAliveService",
                        "AppComponentManager",
                        "HonoluluCameraLoggingService",
                        "AsyncScriptingService",
                        "SandboxedProcessService",
                        "UsdidValuesProvider",
                        "FirstPartyUserValuesLiteProvider",
                        "FamilyAppsUserValuesProvider",
                        "PhoneIdProvider",
                        "InstallReferrerProvider",
                    )

                val blockedProcesses =
                    setOf(
                        ":mqtt",
                        ":fbns",
                        ":pretosproc",
                        ":fdidsync",
                        ":zygote_warmer",
                        ":honolulu",
                        ":AsyncScriptingProcess",
                        ":sandboxed_process0",
                        ":sandboxed_process1",
                        ":he_sandboxed_process0",
                        ":he_sandboxed_process1",
                    )

                fun disableElements(tagName: String) {
                    val nodes = appNode.getElementsByTagName(tagName)
                    for (i in nodes.length - 1 downTo 0) {
                        val elem = nodes.item(i) as Element
                        val name = elem.getAttribute("android:name")
                        val proc = elem.getAttribute("android:process")

                        val shouldDisable =
                            blockedProcesses.contains(proc) ||
                                blockedComponentPatterns.any { name.contains(it, ignoreCase = true) }

                        if (shouldDisable) {
                            elem.setAttribute("android:enabled", "false")
                            elem.setAttribute("android:exported", "false")
                            if (elem.hasAttribute("android:process")) {
                                elem.removeAttribute("android:process")
                            }
                            val intentFilters = elem.getElementsByTagName("intent-filter")
                            for (j in intentFilters.length - 1 downTo 0) {
                                elem.removeChild(intentFilters.item(j))
                            }
                        }
                    }
                }

                disableElements("service")
                disableElements("receiver")
                disableElements("provider")

                for (tag in listOf("activity", "service", "receiver", "provider")) {
                    val elements = appNode.getElementsByTagName(tag)
                    for (i in elements.length - 1 downTo 0) {
                        val elem = elements.item(i) as Element
                        val proc = elem.getAttribute("android:process")
                        if (blockedProcesses.contains(proc) || proc.startsWith(":")) {
                            elem.removeAttribute("android:process")
                        }
                    }
                }
            }
        }
    }

/**
 * Bytecode patch to:
 * 1. Neutralize runtime telemetry uploaders (IGAnalytics2SimpleUploader, IgAnalytics2TaskBasedUploader, PrivacyControlledUploader).
 * 2. Suppress internal metric event generation at the root (EventBuilder isSampled -> false, report -> void).
 * 3. Suppress background push & state receivers (FbnsInitBroadcastReceiver, IgAppComponentReceiver).
 */
@Suppress("unused")
val disableBackgroundAndTelemetryPatch =
    bytecodePatch(
        name = "Disable background processes and telemetry",
        description = "Completely strips background sync, secondary daemons, analytics uploaders, tracking providers, and internal metric generation.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(disableBackgroundManifestPatch)

        execute {
            // 1. Neutralize HTTP telemetry uploaders
            val simpleUploader =
                Fingerprint(
                    name = "HZG",
                    custom = { _, classDef -> classDef.endsWith("/IGAnalytics2SimpleUploader;") },
                )
            val taskUploader =
                Fingerprint(
                    name = "HZG",
                    custom = { _, classDef -> classDef.endsWith("/IgAnalytics2TaskBasedUploader;") },
                )
            val privacyUploader =
                Fingerprint(
                    name = "HZG",
                    custom = { _, classDef -> classDef.endsWith("/PrivacyControlledUploader;") },
                )

            listOf(simpleUploader, taskUploader, privacyUploader).forEach { fp ->
                fp.method.addInstructions(
                    0,
                    """
                    return-void
                    """.trimIndent(),
                )
            }

            // Falco log methods are native. Skip their calls while preserving queue cleanup in callers.
            FalcoLogCallerFingerprint.matchAll().forEach { match ->
                match.method.apply {
                    findInstructionIndicesReversedOrThrow { isFalcoLogCall() }.forEach { index ->
                        replaceInstruction(index, "nop")
                    }
                }
            }

            // 3. Suppress upload scheduling in FFSamplingBridgeJava
            mutableClassDefByOrNull("Lcom/facebook/falco/sampling/FFSamplingBridgeJava;")?.let { cls ->
                cls.methods.filter { it.implementation != null && it.name in listOf("scheduleUpload", "doUpload") }.forEach { method ->
                    method.addInstructions(0, "return-void")
                }
            }

            // 4. Suppress EventBuilder (isSampled -> false, report -> return-void)
            listOf("LX/1fX;", "LX/3r7;", "LX/3hf;").forEach { className ->
                mutableClassDefByOrNull(className)?.let { cls ->
                    cls.methods.find { it.implementation != null && it.name == "isSampled" }?.addInstructions(
                        0,
                        """
                        const/4 v0, 0x0
                        return v0
                        """.trimIndent(),
                    )
                    cls.methods.find { it.implementation != null && it.name == "report" }?.addInstructions(0, "return-void")
                }
            }

            // 5. Suppress reportEvent in direct telemetry reporters
            mutableClassDefByOrNull("LX/5UN;")?.let { cls ->
                cls.methods.find { it.implementation != null && it.name == "reportEvent" }?.addInstructions(0, "return-void")
            }

            // 6. Neutralize FbnsInitBroadcastReceiver.onReceive
            mutableClassDefByOrNull("Lcom/instagram/push/FbnsInitBroadcastReceiver;")?.let { cls ->
                cls.methods.find { it.implementation != null && it.name == "onReceive" }?.addInstructions(
                    0,
                    "return-void",
                )
            }

            // 7. Neutralize other push & component management receivers
            listOf(
                "Lcom/instagram/appcomponentmanager/IgAppComponentReceiver;",
                "Lcom/instagram/notifications/push/fbns/IgPushSdkFbnsReceiverShim;",
                "Lcom/instagram/push/InstagramAppUpgradeEventReceiver;",
            ).forEach { className ->
                mutableClassDefByOrNull(className)?.let { cls ->
                    cls.methods.find { it.implementation != null && it.name == "onReceive" }?.addInstructions(
                        0,
                        "return-void",
                    )
                }
            }
        }
    }
