package app.morphe.shared.misc.extension

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.iface.Method
import java.net.URLDecoder
import java.util.jar.JarFile

internal const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/morphe/extension/shared/Utils;"

/**
 * A patch to extend with an extension shared with multiple patches.
 *
 * @param extensionName The name of the extension to extend with.
 */
fun sharedExtensionPatch(
    extensionName: String,
    vararg hooks: ExtensionHook,
) = bytecodePatch {
    dependsOn(sharedExtensionPatch(*hooks))

    extendWith("extensions/$extensionName.mpe")
}

/**
 * A patch to extend with the "shared" extension.
 *
 * @param hooks The hooks to get the application context for use in the extension,
 * commonly for the onCreate method of exported activities.
 */
fun sharedExtensionPatch(vararg hooks: ExtensionHook) =
    bytecodePatch {
        extendWith("extensions/shared.mpe")

        execute {
            if (classDefByOrNull(EXTENSION_CLASS_DESCRIPTOR) == null) {
                throw PatchException(
                    "Shared extension class is not found. " +
                        "This patch can not succeed without it.",
                )
            }
        }

        finalize {
            // The hooks are made in finalize to ensure that the context is hooked before any other patches.
            hooks.forEach { hook -> hook(EXTENSION_CLASS_DESCRIPTOR) }

            // Modify Utils method to include the patches release version.
            MorpheUtilsPatchesVersionFingerprint.method.apply {
                /**
                 * @return The file path for the jar this classfile is contained inside.
                 */
                fun getCurrentJarFilePath(): String {
                    val className =
                        object {}::class.java.enclosingClass.name
                            .replace('.', '/') + ".class"
                    val classUrl = object {}::class.java.classLoader?.getResource(className)
                    if (classUrl != null) {
                        val urlString = classUrl.toString()

                        if (urlString.startsWith("jar:file:")) {
                            val end = urlString.lastIndexOf('!')

                            return URLDecoder.decode(urlString.substring("jar:file:".length, end), "UTF-8")
                        }
                    }
                    throw IllegalStateException("Not running from inside a JAR file.")
                }

                /**
                 * @return The value for the manifest entry,
                 *         or "Unknown" if the entry does not exist or is blank.
                 */
                @Suppress("SameParameterValue")
                fun getPatchesManifestEntry(attributeKey: String) =
                    JarFile(getCurrentJarFilePath()).use { jarFile ->
                        jarFile.manifest.mainAttributes.entries
                            .firstOrNull { it.key.toString() == attributeKey }
                            ?.value
                            ?.toString()
                            ?: "Unknown"
                    }

                val manifestValue = getPatchesManifestEntry("Version")
                returnEarly(manifestValue)
            }
        }
    }

class ExtensionHook internal constructor(
    internal val fingerprint: Fingerprint,
    private val insertIndexResolver: BytecodePatchContext.(Method) -> Int,
    private val contextRegisterResolver: BytecodePatchContext.(Method) -> String,
) {
    context(BytecodePatchContext)
    operator fun invoke(extensionClassDescriptor: String) {
        val insertIndex = insertIndexResolver(fingerprint.method)
        val contextRegister = contextRegisterResolver(fingerprint.method)

        fingerprint.method.addInstruction(
            insertIndex,
            "invoke-static/range { $contextRegister .. $contextRegister }, " +
                "$extensionClassDescriptor->setContext(Landroid/content/Context;)V",
        )
    }
}

fun extensionHook(
    insertIndexResolver: BytecodePatchContext.(Method) -> Int = { 0 },
    contextRegisterResolver: BytecodePatchContext.(Method) -> String = { "p0" },
    fingerprint: Fingerprint,
) = ExtensionHook(fingerprint, insertIndexResolver, contextRegisterResolver)
