/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm

import app.crimera.patches.instagram.misc.extension.hooks.instagramInitHook
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

private const val EXTENSION_CLASS = "$PATCHES_DESCRIPTOR/dm/InboxLock;"

// NotificationManagerCompat.notify(tag, id, notification), which Instagram posts its notifications through.
internal object NotifyFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/String;", "I", "Landroid/app/Notification;"),
    filters =
        listOf(
            string("android.support.useSideChannel"),
        ),
)

// The banner Instagram shows over the app for a message that arrives while it is open; it lives in its own window.
internal object InAppNotificationFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(
            string("InAppNotificationWindow:"),
        ),
)

// Runs every time a banner is about to be shown; the banner window itself is created only once and reused.
internal object InAppNotificationShowFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(
            string("no foreground activity to render in-app notification"),
        ),
)

@Suppress("unused")
val inboxLockPatch =
    bytecodePatch(
        name = "Inbox lock",
        description = "Asks for your fingerprint, face or screen lock before the inbox is shown.",
        default = false,
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            instagramInitHook.fingerprint.method.apply {
                val superIndex = instructions.indexOfFirst { it.opcode == Opcode.INVOKE_SUPER }
                addInstruction(superIndex + 1, "invoke-static {}, $EXTENSION_CLASS->init()V")
            }

            NotifyFingerprint.method.apply {
                // Parameter positions are found by type because Meta reorders them between builds.
                val thisOffset = if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
                val notification = parameters.indexOfFirst { it.type == "Landroid/app/Notification;" } + thisOffset
                addInstructions(
                    0,
                    """
                    invoke-static {p$notification}, $EXTENSION_CLASS->hideNotification(Landroid/app/Notification;)Landroid/app/Notification;
                    move-result-object p$notification
                    """,
                )
            }

            // The window is hidden instead of skipped because Instagram removes it again later, and it is reused for
            // every banner, so its visibility is decided again each time one is shown.
            InAppNotificationFingerprint.method.apply {
                val thisOffset = if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
                val window = parameters.indexOfFirst { it.type == "Landroid/view/View;" } + thisOffset
                addInstruction(0, "invoke-static {p$window}, $EXTENSION_CLASS->setBanner(Landroid/view/View;)V")
            }
            InAppNotificationShowFingerprint.method.addInstruction(
                0,
                "invoke-static {}, $EXTENSION_CLASS->updateBanner()V",
            )

            enableSettings("inboxLock")
        }
    }
