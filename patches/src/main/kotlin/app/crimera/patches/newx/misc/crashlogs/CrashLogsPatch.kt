package app.crimera.patches.newx.misc.crashlogs

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.misc.extension.newXInitHook
import app.crimera.patches.newx.misc.postoptions.newXPostOption
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.action
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.EXTENSION_PACKAGE
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import org.w3c.dom.Element

private const val CRASH_HANDLER_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/NewXCrashHandler;"
private const val CRASH_SETTINGS_ACTION_DESCRIPTOR = "$EXTENSION_PACKAGE/settings/NewXCrashSettingsAction;"
private const val CRASH_POST_OPTION_HANDLER_DESCRIPTOR = "$EXTENSION_PACKAGE/misc/NewXCrashPostOptionHandler;"
private const val CRASH_APP_ACTION = "AppealWarning"

private val newXCrashLogsResourcePatch =
    resourcePatch(
        description = "Adds NewX crash log receivers to the Android manifest.",
    ) {
        finalize {
            document("AndroidManifest.xml").use { document ->
                val application = document.getElementsByTagName("application").item(0) as Element

                listOf(
                    "app.morphe.extension.newx.misc.NewXCrashCopyReceiver",
                ).forEach { receiverName ->
                    val receiver = document.createElement("receiver")
                    receiver.setAttribute("android:name", receiverName)
                    receiver.setAttribute("android:exported", "false")
                    application.appendChild(receiver)
                }
            }
        }
    }

@Suppress("unused")
val newXCrashLogsPatch =
    bytecodePatch(
        name = "NewX: Crash logs",
        description = "Saves crash logs and shows a notification with share and copy actions.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXExtensionPatch, newXCrashLogsResourcePatch)

        newXSettings {
            category(Categories.ADVANCED) {
                group(Groups.DEBUG_TOOLS) {
                    action(
                        id = "newx.advanced.debug_tools.crash_app",
                        strings = settingStrings("piko_newx_crash_app"),
                        order = 250,
                        handlerClassDescriptor = CRASH_SETTINGS_ACTION_DESCRIPTOR,
                    )
                    toggle(
                        id = "newx.advanced.debug_tools.crash_post_option",
                        strings = settingStrings("piko_newx_crash_post_option"),
                        order = 300,
                        defaultValue = false,
                    )
                }
            }
        }
        newXPostOption(
            handlerDescriptor = CRASH_POST_OPTION_HANDLER_DESCRIPTOR,
            actionName = CRASH_APP_ACTION,
            iconResourceName = "ic_vector_bug_stroke",
            order = 280,
        )

        execute {
            newXInitHook.fingerprint.method.apply {
                val superIndex = indexOfFirstInstruction(Opcode.INVOKE_SUPER)
                val contextRegister = getInstruction(superIndex).registersUsed[0]

                addInstruction(
                    superIndex + 1,
                    "invoke-static {v$contextRegister}, " +
                        "$CRASH_HANDLER_DESCRIPTOR->install(Landroid/content/Context;)V",
                )
            }
        }
    }
