/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.settings

import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import org.w3c.dom.Element

private const val AUTHENTICATOR = "android.accounts.AccountAuthenticator"
private const val SERVICE = "Landroid/app/Service;"
private const val INTENT = "Landroid/content/Intent;"
private const val SETTINGS_RESTART = "Lapp/morphe/extension/instagram/settings/SettingsRestart;"

private fun resolveTaskServiceName(manifest: Element): String {
    val application = manifest.getElementsByTagName("application").item(0) as? Element
        ?: throw PatchException("Missing application for the task removal service")
    val services = application.getElementsByTagName("service")
    val service = (0 until services.length).map { services.item(it) as Element }.singleOrNull {
        val actions = it.getElementsByTagName("action")
        (0 until actions.length).any { index ->
            (actions.item(index) as Element).getAttribute("android:name") == AUTHENTICATOR
        }
    } ?: throw PatchException("Expected exactly one account authenticator service")

    val packageName = manifest.getAttribute("package")
    fun processName(value: String) = if (value.startsWith(':')) packageName + value else value
    val appProcess = processName(application.getAttribute("android:process").ifEmpty { packageName })
    val serviceProcess = processName(service.getAttribute("android:process").ifEmpty { appProcess })
    val metadata = service.getElementsByTagName("meta-data")
    if (packageName.isEmpty() || appProcess.startsWith('@') || appProcess.startsWith('?') ||
        service.getAttribute("android:exported") != "false" ||
        application.getAttribute("android:enabled") !in listOf("", "true") ||
        service.getAttribute("android:enabled") !in listOf("", "true") ||
        listOf("stopWithTask", "isolatedProcess", "externalService").any {
            service.getAttribute("android:$it") !in listOf("", "false")
        } || serviceProcess != appProcess ||
        (0 until metadata.length).none {
            (metadata.item(it) as Element).getAttribute("android:name") == AUTHENTICATOR
        }
    ) {
        throw PatchException("Account authenticator must be enabled, private and in-process with task callbacks")
    }

    val name = service.getAttribute("android:name")
    if (name.isEmpty() || name.startsWith('@') || name.startsWith('?')) {
        throw PatchException("Missing concrete account authenticator service name")
    }
    return when {
        name.startsWith('.') -> packageName + name
        '.' !in name -> "$packageName.$name"
        else -> name
    }
}

private lateinit var taskServiceName: String

private val taskRemovalServiceResourcePatch = resourcePatch {
    execute {
        // Resolve the Android registration, not Instagram's class name or onBind implementation.
        document("AndroidManifest.xml").use {
            taskServiceName = resolveTaskServiceName(it.documentElement)
        }
    }
}

internal val settingsTaskRemovalPatch = bytecodePatch {
    dependsOn(sharedExtensionPatch, taskRemovalServiceResourcePatch)
    execute {
        val service = mutableClassDefBy("L${taskServiceName.replace('.', '/')};")
        if (service.superclass != SERVICE || service.methods.any {
                it.name == "onStartCommand" || it.name == "onTaskRemoved"
            }
        ) {
            throw PatchException("Authentication service has unverified task lifecycle behavior")
        }

        Fingerprint(
            definingClass = SETTINGS_RESTART,
            name = "taskServiceClassName",
            returnType = "Ljava/lang/String;",
            parameters = listOf()
        ).method.replaceInstruction(
            0,
            "const-string v0, \"$taskServiceName\""
        )

        // Reuse a registered, in-process bound service so mount installs need no manifest change.
        // Its constructor, account-authenticator creation and binder remain untouched.
        service.methods.add(
            ImmutableMethod(
                service.type,
                "onStartCommand",
                listOf(INTENT, "I", "I").map { ImmutableMethodParameter(it, emptySet(), null) },
                "I",
                AccessFlags.PUBLIC.value,
                emptySet(),
                emptySet(),
                MutableMethodImplementation(5),
            ).toMutable().apply {
                addInstructionsWithLabels(
                    0,
                    """
                    invoke-static {p1}, $SETTINGS_RESTART->isTaskServiceIntent($INTENT)Z
                    move-result v0
                    if-eqz v0, :native_start
                    const/4 v0, 0x2
                    return v0
                    :native_start
                    invoke-super {p0, p1, p2, p3}, $SERVICE->onStartCommand(${INTENT}II)I
                    move-result v0
                    return v0
                    """.trimIndent(),
                )
            },
        )
        service.methods.add(
            ImmutableMethod(
                service.type,
                "onTaskRemoved",
                listOf(ImmutableMethodParameter(INTENT, emptySet(), null)),
                "V",
                AccessFlags.PUBLIC.value,
                emptySet(),
                emptySet(),
                MutableMethodImplementation(2),
            ).toMutable().apply {
                addInstructions(
                    0,
                    """
                    invoke-super {p0, p1}, $SERVICE->onTaskRemoved($INTENT)V
                    invoke-static {}, $SETTINGS_RESTART->onTaskRemoved()V
                    return-void
                    """.trimIndent(),
                )
            },
        )
    }
}
