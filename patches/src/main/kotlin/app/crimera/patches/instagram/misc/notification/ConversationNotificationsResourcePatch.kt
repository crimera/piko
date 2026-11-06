/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.notification

import app.morphe.patcher.patch.resourcePatch
import app.morphe.util.ResourceGroup
import app.morphe.util.copyResources
import org.w3c.dom.Element

/** Declares Android Auto messaging support and registers the receivers of its mark as read action and of swipes. */
internal val conversationNotificationsResourcePatch =
    resourcePatch {
        execute {
            copyResources(
                "instagram/conversations",
                ResourceGroup("xml", "piko_automotive_app_desc.xml"),
            )
        }

        finalize {
            document("AndroidManifest.xml").use { document ->
                val application = document.getElementsByTagName("application").item(0) as Element

                val carApplication = document.createElement("meta-data")
                carApplication.setAttribute("android:name", "com.google.android.gms.car.application")
                carApplication.setAttribute("android:resource", "@xml/piko_automotive_app_desc")
                application.appendChild(carApplication)

                val receiver = document.createElement("receiver")
                receiver.setAttribute(
                    "android:name",
                    "app.morphe.extension.instagram.patches.notification.MarkAsReadReceiver",
                )
                receiver.setAttribute("android:exported", "false")
                application.appendChild(receiver)

                val dismissedReceiver = document.createElement("receiver")
                dismissedReceiver.setAttribute(
                    "android:name",
                    "app.morphe.extension.instagram.patches.notification.NotificationDismissedReceiver",
                )
                dismissedReceiver.setAttribute("android:exported", "false")
                application.appendChild(dismissedReceiver)

                // Serves cached message images to the system UI through the grant of a notification.
                val imageProvider = document.createElement("provider")
                imageProvider.setAttribute(
                    "android:name",
                    "app.morphe.extension.instagram.patches.notification.NotificationImageProvider",
                )
                imageProvider.setAttribute("android:authorities", "com.instagram.android.piko.notificationimages")
                imageProvider.setAttribute("android:exported", "false")
                imageProvider.setAttribute("android:grantUriPermissions", "true")
                application.appendChild(imageProvider)
            }
        }
    }
