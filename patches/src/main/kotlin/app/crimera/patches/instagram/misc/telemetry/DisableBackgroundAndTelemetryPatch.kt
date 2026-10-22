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
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.string
import app.morphe.util.findInstructionIndicesReversedOrThrow
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
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

private object ComponentStartupFingerprint : Fingerprint(
    definingClass = "/InstagramApplicationForMainProcess;",
    name = "initializeAllColdStartJobs",
    returnType = "V",
    filters = listOf(
        methodCall(definingClass = "Lcom/facebook/common/build/BuildConstants;", parameters = emptyList(), returnType = "I"),
        opcode(Opcode.MOVE_RESULT),
        methodCall(definingClass = "Ljava/io/File;", name = "exists", returnType = "Z"),
        opcode(Opcode.IF_NE),
        methodCall(definingClass = "Ljava/io/File;", name = "lastModified", returnType = "J"),
        opcode(Opcode.IF_GEZ),
        string("Android PackageManager returned version code: %d, apk version code is: %d"),
        methodCall(parameters = listOf("Landroid/content/Context;"), returnType = "V"),
    ),
)

private val refreshDisabledComponentsPatch = bytecodePatch {
    execute {
        val match = ComponentStartupFingerprint.matchSingle()
        val instructions = match.method.implementation!!.instructions
        val versionResultIndex = match.instructionMatches[1].index
        val guardIndex = match.instructionMatches[3].index
        val guard = instructions[guardIndex] as BuilderOffsetInstruction
        val timestampGuard = instructions[match.instructionMatches[5].index] as BuilderOffsetInstruction
        val refreshIndex = match.instructionMatches.last().index
        val registers = guard.registersUsed
        val versionRegister = instructions[versionResultIndex].registersUsed.single()
        val constant = instructions.take(guardIndex).lastOrNull {
            it.opcode.setsRegister() && it.registersUsed.firstOrNull() == registers.lastOrNull()
        }
        val versionWriteIndex = instructions.take(guardIndex).indexOfLast {
            it.opcode.setsRegister() && it.registersUsed.firstOrNull() == versionRegister
        }
        if (registers.size != 2 || registers.first() != versionRegister ||
            versionWriteIndex != versionResultIndex ||
            (constant as? WideLiteralInstruction)?.wideLiteral != 1L ||
            guard.target.location.index != refreshIndex + 1 ||
            timestampGuard.target.location.index != refreshIndex + 1
        ) {
            throw PatchException("Could not resolve the component refresh version guard")
        }
        // Reuse the native update-time check so same-version APK updates reset persisted enabled overrides once.
        match.method.replaceInstruction(guardIndex, "nop")
    }
}

/**
 * Manifest patch to:
 * - Disable all persistent background daemons (:mqtt, :fbns, :fdidsync, :pretosproc, :honolulu, etc.)
 * - Disable cross-app tracking content providers (USDID, FamilyID, PhoneID, InstallReferrer)
 * - Disable background sync workers, telemetry upload services, and boot/connectivity receivers
 * - Preserve component processes and prevent the component manager from re-enabling blocked components
 */
val disableBackgroundManifestPatch =
    resourcePatch(
        name = "Disable background components",
        description = "Disables background services, receivers, and tracking providers.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(refreshDisabledComponentsPatch)

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
                            // The component manager overrides android:enabled unless its metadata also disables the component.
                            val metadata = elem.getElementsByTagName("meta-data")
                            for (key in listOf("default-state", "cmp_manager.persist_state")) {
                                val entry =
                                    (0 until metadata.length)
                                        .map { metadata.item(it) as Element }
                                        .firstOrNull { it.getAttribute("android:name") == key }
                                        ?: document.createElement("meta-data").apply {
                                            setAttribute("android:name", key)
                                            elem.appendChild(this)
                                        }
                                entry.removeAttribute("android:resource")
                                entry.setAttribute("android:value", "false")
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
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(disableBackgroundManifestPatch)

        execute {
            // 1. Neutralize HTTP telemetry uploaders
            val simpleUploader =
                Fingerprint(
                    returnType = "V",
                    parameters = listOf("L", "L", "L"),
                    custom = { _, classDef -> classDef.endsWith("/IGAnalytics2SimpleUploader;") },
                )
            val taskUploader =
                Fingerprint(
                    returnType = "V",
                    parameters = listOf("L", "L", "L"),
                    custom = { _, classDef -> classDef.endsWith("/IgAnalytics2TaskBasedUploader;") },
                )
            val privacyUploader =
                Fingerprint(
                    returnType = "V",
                    parameters = listOf("L", "L", "L"),
                    custom = { _, classDef -> classDef.endsWith("/PrivacyControlledUploader;") },
                )

            listOf(simpleUploader, taskUploader, privacyUploader).forEach { fp ->
                fp.matchSingle().method.addInstructions(
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
            Fingerprint(
                name = "isSampled",
                returnType = "Z",
                parameters = emptyList(),
                custom = { method, classDef ->
                    method.implementation != null && "Lcom/facebook/quicklog/EventBuilder;" in classDef.interfaces
                },
            ).matchAll().forEach { match ->
                match.method.addInstructions(
                    0,
                    """
                    const/4 v0, 0x0
                    return v0
                    """.trimIndent(),
                )
            }
            Fingerprint(
                name = "report",
                returnType = "V",
                parameters = emptyList(),
                custom = { method, classDef ->
                    method.implementation != null && "Lcom/facebook/quicklog/EventBuilder;" in classDef.interfaces
                },
            ).matchAll().forEach { match ->
                match.method.addInstructions(0, "return-void")
            }

            // 5. Neutralize FbnsInitBroadcastReceiver.onReceive
            mutableClassDefByOrNull("Lcom/instagram/push/FbnsInitBroadcastReceiver;")?.let { cls ->
                cls.methods.find { it.implementation != null && it.name == "onReceive" }?.addInstructions(
                    0,
                    "return-void",
                )
            }

            // 6. Neutralize other push & component management receivers
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
