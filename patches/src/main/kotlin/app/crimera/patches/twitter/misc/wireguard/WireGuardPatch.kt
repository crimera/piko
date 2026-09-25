/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.wireguard

import app.crimera.patches.twitter.misc.extension.twitterInitHook
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import org.w3c.dom.Element

private const val SERVICE = "app.morphe.extension.twitter.wireguard.WireGuardVpnService"
private val payloadClass = object {}.javaClass

internal val wireguardResourcesPatch = resourcePatch {
    execute {
        document("AndroidManifest.xml").use { doc ->
            val manifest = doc.documentElement
            val application = doc.getElementsByTagName("application").item(0) as Element
            if (manifest.hasAttribute("split") || application.getAttribute("android:isSplitRequired") == "true") {
                throw PatchException("WireGuard needs a merged APK. Open the complete APKM in Morphe, not an individual split.")
            }
            // Patcher 1.6 exposes file injection but no public API for marking a new
            // entry uncompressed/aligned. Allow Android to extract the native payload.
            application.setAttribute("android:extractNativeLibs", "true")
            listOf("INTERNET", "ACCESS_NETWORK_STATE", "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_SYSTEM_EXEMPTED").forEach { name ->
                val permission = "android.permission.$name"
                val existing = doc.getElementsByTagName("uses-permission")
                if ((0 until existing.length).none { (existing.item(it) as Element).getAttribute("android:name") == permission }) {
                    manifest.appendChild(doc.createElement("uses-permission").apply {
                        setAttribute("android:name", permission)
                    })
                }
            }
            application.appendChild(doc.createElement("service").apply {
                setAttribute("android:name", SERVICE)
                setAttribute("android:permission", "android.permission.BIND_VPN_SERVICE")
                setAttribute("android:exported", "false")
                setAttribute("android:foregroundServiceType", "systemExempted")
                appendChild(doc.createElement("intent-filter").apply {
                    appendChild(doc.createElement("action").apply {
                        setAttribute("android:name", "android.net.VpnService")
                    })
                })
                appendChild(doc.createElement("meta-data").apply {
                    setAttribute("android:name", "android.net.VpnService.SUPPORTS_ALWAYS_ON")
                    setAttribute("android:value", "false")
                })
            })
        }

        // Morphe merges APKM inputs before resource decoding. Only add ABIs already
        // present in the merged target, so a 32-bit target cannot select a new 64-bit ABI.
        val supported = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        val abis = get("lib").listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()
        if (abis.isEmpty() || abis.any { it !in supported }) {
            throw PatchException("WireGuard cannot determine supported target ABIs: $abis")
        }
        abis.forEach { abi ->
            val path = "/wireguard/native/$abi/libwg-go.so"
            val source = payloadClass.getResourceAsStream(path)
                ?: throw PatchException("Missing WireGuard native payload for $abi")
            val destination = get("lib/$abi/libwg-go.so")
            if (destination.exists()) throw PatchException("Target already contains libwg-go.so")
            destination.parentFile.mkdirs()
            source.use { input -> destination.outputStream().use { input.copyTo(it) } }
        }
        listOf(
            "NOTICE", "Apache-2.0.txt", "MIT-wireguard-go.txt", "AndroidX-Apache-2.0.txt",
            "Kotlin-Apache-2.0.txt", "Go-BSD.txt", "Go-crypto-BSD.txt", "Go-net-BSD.txt", "Go-sys-BSD.txt",
        ).forEach { name ->
            val source = payloadClass.getResourceAsStream("/wireguard/licenses/$name")
                ?: throw PatchException("Missing WireGuard notice: $name")
            val destination = get("assets/piko-wireguard/$name")
            destination.parentFile.mkdirs()
            source.use { input -> destination.outputStream().use { input.copyTo(it) } }
        }
    }
}

@Suppress("unused")
val wireguardPatch = bytecodePatch(
    name = "Embedded WireGuard",
    description = "Adds an opt-in, X-only WireGuard tunnel. Requires a merged APK/APKM and Android VPN consent.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_X)
    dependsOn(settingsPatch, wireguardResourcesPatch)
    execute {
        enableSettings("wireguard")
        twitterInitHook.fingerprint.method.addInstruction(
            0,
            "invoke-static/range {p0 .. p0}, Lapp/morphe/extension/twitter/wireguard/WireGuardManager;->initialize(Landroid/content/Context;)V",
        )
    }
}
