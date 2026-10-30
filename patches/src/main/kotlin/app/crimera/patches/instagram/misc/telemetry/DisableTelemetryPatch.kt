/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.telemetry

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import app.morphe.util.findInstructionIndicesReversedOrThrow
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

private val refreshComponentsPatch = bytecodePatch {
    execute {
        val match = ComponentStartupFingerprint.match()
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
        // Restore component states left by the former background patches, including same-version APK updates.
        match.method.replaceInstruction(guardIndex, "nop")
    }
}

private val disableTelemetryComponentsPatch = resourcePatch {
    dependsOn(refreshComponentsPatch)

    execute {
        document("AndroidManifest.xml").use { document ->
            val appNode = document.documentElement.getElementsByTagName("application").item(0) as Element
            // Only dedicated analytics upload components; shared workers and push services must remain available.
            val telemetryComponents = setOf(
                "com.instagram.analytics.uploadscheduler.AnalyticsUploadAlarmReceiver",
                "com.facebook.analytics2.fabric.onefabric.OneFabricUploadAlarmReceiver",
                "com.facebook.analytics2.fabric.onefabric.FFAlarmUploadJobService",
                "com.google.android.gms.analytics.AnalyticsService",
                "com.google.android.gms.analytics.AnalyticsJobService",
                "com.facebook.analytics2.logger.GooglePlayUploadService",
                "com.facebook.analytics2.logger.service.LollipopUploadSafeService",
                "com.facebook.analytics2.logger.legacy.uploader.LollipopUploadService",
                "com.facebook.analytics2.logger.legacy.uploader.Analytics2UploadService",
                "com.facebook.analytics2.logger.legacy.uploader.AlarmBasedUploadService",
                "com.facebook.analytics2.logger.legacy.uploader.HighPriUploadRetryReceiver",
            )

            for (tagName in listOf("service", "receiver")) {
                val nodes = appNode.getElementsByTagName(tagName)
                for (i in 0 until nodes.length) {
                    val elem = nodes.item(i) as Element
                    if (elem.getAttribute("android:name") !in telemetryComponents) continue

                    elem.setAttribute("android:enabled", "false")
                    // Keep the component manager from enabling these uploaders again on startup.
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
                }
            }
        }
    }
}

@Suppress("unused")
val disableTelemetryPatch =
    bytecodePatch(
        name = "Disable telemetry",
        description = "Disables analytics upload components and Falco event logging.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, disableTelemetryComponentsPatch)

        execute {
            enableSettings("disableTelemetry")

            // Falco log methods are native. Skip their calls while preserving queue cleanup in callers.
            FalcoLogCallerFingerprint.matchAll().forEach { match ->
                match.method.apply {
                    findInstructionIndicesReversedOrThrow { isFalcoLogCall() }.forEach { index ->
                        replaceInstruction(index, "nop")
                    }
                }
            }
        }
    }
