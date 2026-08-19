/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Document
import org.w3c.dom.Element

private const val COLLECTION_DOWNLOAD_SERVICE =
    "app.morphe.extension.instagram.patches.download.CollectionDownloadService"

val collectionDownloadResourcePatch =
    resourcePatch(
        description = "Registers the saved collection download service.",
    ) {
        finalize {
            document("AndroidManifest.xml").use { document ->
                val manifest = document.getElementsByTagName("manifest").item(0) as Element
                val application = document.getElementsByTagName("application").item(0) as Element

                ensurePermission(document, manifest, "android.permission.FOREGROUND_SERVICE")
                ensurePermission(document, manifest, "android.permission.FOREGROUND_SERVICE_DATA_SYNC")

                if (!hasService(document, COLLECTION_DOWNLOAD_SERVICE)) {
                    val service = document.createElement("service")
                    service.setAttribute("android:name", COLLECTION_DOWNLOAD_SERVICE)
                    service.setAttribute("android:exported", "false")
                    service.setAttribute("android:stopWithTask", "false")
                    service.setAttribute("android:foregroundServiceType", "dataSync")
                    application.appendChild(service)
                }
            }
        }
    }

private fun ensurePermission(document: Document, manifest: Element, permission: String) {
    val permissions = document.getElementsByTagName("uses-permission")
    for (index in 0 until permissions.length) {
        val element = permissions.item(index) as Element
        if (element.getAttribute("android:name") == permission) return
    }

    val element = document.createElement("uses-permission")
    element.setAttribute("android:name", permission)
    manifest.appendChild(element)
}

private fun hasService(document: Document, serviceName: String): Boolean {
    val services = document.getElementsByTagName("service")
    for (index in 0 until services.length) {
        val element = services.item(index) as Element
        if (element.getAttribute("android:name") == serviceName) return true
    }
    return false
}
