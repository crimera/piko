/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.wireguard

import app.morphe.patcher.Patcher
import app.morphe.patcher.PatcherConfig
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.resource.CpuArchitecture
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Runs the real resource patch and resource encoder, without an X APK or device. */
class WireGuardPackagingTest {
    @get:Rule val temporary = TemporaryFolder()

    private val sdk: File
        get() = File(requireNotNull(System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")) {
            "Set ANDROID_HOME to run native packaging tests"
        })
    private val aapt2: File get() = sdk.resolve("build-tools/36.0.0/aapt2")

    private fun command(vararg args: String): String {
        val process = ProcessBuilder(*args).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
        return output
    }

    private fun fixture(abis: List<String>, split: Boolean = false): File {
        val manifest = temporary.newFile("AndroidManifest.xml")
        manifest.writeText(
            """<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.wireguardfixture" android:versionCode="1" android:versionName="1.0"
                ${if (split) "split=\"config.arm64_v8a\"" else ""}>
                <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/>
                <application android:hasCode="false" android:extractNativeLibs="false"/>
            </manifest>""",
        )
        val resources = temporary.root.resolve("res/values").apply { mkdirs() }
        resources.resolve("strings.xml").writeText("<resources><string name=\"fixture\">Packaging test</string></resources>")
        val compiledResources = temporary.root.resolve("resources.zip")
        command(aapt2.path, "compile", "--dir", resources.parent, "-o", compiledResources.path)
        val compiled = temporary.root.resolve("compiled.apk")
        command(aapt2.path, "link", "-I", sdk.resolve("platforms/android-36/android.jar").path,
            "--manifest", manifest.path, "-o", compiled.path, compiledResources.path)
        val input = temporary.root.resolve("input.apk")
        ZipOutputStream(input.outputStream()).use { output ->
            ZipFile(compiled).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    output.putNextEntry(ZipEntry(entry.name))
                    zip.getInputStream(entry).use { it.copyTo(output) }
                    output.closeEntry()
                }
            }
            abis.forEach { abi ->
                output.putNextEntry(ZipEntry("lib/$abi/libfixture.so"))
                output.write(byteArrayOf(1, 2, 3)) // Directory marker; never installed or loaded.
                output.closeEntry()
            }
        }
        return input
    }

    private fun patch(input: File, keep: Set<CpuArchitecture> = emptySet()): File = runBlocking {
        Patcher(PatcherConfig(input, temporary.root.resolve("patch-work"), keepArchitectures = keep)).use { patcher ->
            patcher += setOf(wireguardResourcesPatch)
            patcher().collect { result -> result.exception?.let { throw it } }
            val result = patcher.get()
            requireNotNull(result.resources.resourcesApk).copyTo(temporary.root.resolve("output.apk"))
        }
    }

    @Test fun nativePayloadAndManifestSurviveActualResourceEncoding() {
        val output = patch(fixture(listOf("arm64-v8a", "armeabi-v7a")))
        ZipFile(output).use { zip ->
            for (abi in listOf("arm64-v8a", "armeabi-v7a")) {
                val actual = zip.getInputStream(requireNotNull(zip.getEntry("lib/$abi/libwg-go.so"))).use { it.readBytes() }
                val expected = requireNotNull(javaClass.getResourceAsStream("/wireguard/native/$abi/libwg-go.so")).use { it.readBytes() }
                assertArrayEquals(expected, actual)
            }
            assertNull("Must not introduce another process ABI", zip.getEntry("lib/x86_64/libwg-go.so"))
            assertNotNull(zip.getEntry("assets/piko-wireguard/NOTICE"))
            assertNotNull(zip.getEntry("assets/piko-wireguard/MIT-wireguard-go.txt"))
        }
        val manifest = command(aapt2.path, "dump", "xmltree", "--file", "AndroidManifest.xml", output.path)
        assertTrue(manifest.contains("app.morphe.extension.twitter.wireguard.WireGuardVpnService"))
        assertTrue(manifest.contains("android.permission.BIND_VPN_SERVICE"))
        assertTrue(manifest.contains("android.net.VpnService.SUPPORTS_ALWAYS_ON"))
        assertTrue(manifest.contains("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"))
        assertTrue(manifest.lineSequence().any { "extractNativeLibs" in it && it.endsWith("=true") })
        assertFalse(manifest.contains("com.wireguard.android.backend.GoBackend"))
    }

    @Test fun respectsMorpheArchitectureStripping() {
        val output = patch(fixture(listOf("arm64-v8a", "armeabi-v7a")), setOf(CpuArchitecture.ARM64_V8A))
        ZipFile(output).use { zip ->
            assertNotNull(zip.getEntry("lib/arm64-v8a/libwg-go.so"))
            assertNull(zip.getEntry("lib/armeabi-v7a/libwg-go.so"))
        }
    }

    @Test fun rejectsUnmergedSplits() {
        try {
            patch(fixture(listOf("arm64-v8a"), split = true))
            fail("Individual split was accepted")
        } catch (expected: PatchException) {
            assertTrue(expected.message.orEmpty().contains("merged APK"))
        }
    }

    @Test fun rejectsUnsupportedAbi() {
        try {
            patch(fixture(listOf("mips")))
            fail("Unsupported ABI was accepted")
        } catch (expected: PatchException) {
            assertTrue(expected.message.orEmpty().contains("ABIs"))
        }
    }
}
