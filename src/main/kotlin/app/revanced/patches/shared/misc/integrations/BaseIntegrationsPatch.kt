package app.revanced.patches.shared.misc.integrations

import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstruction
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.fingerprint.MethodFingerprint
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchException
import app.revanced.patches.shared.misc.integrations.BaseIntegrationsPatch.IntegrationsFingerprint.IRegisterResolver
import app.revanced.util.resultOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import crimera.patches.twitter.misc.integrations.fingerprints.ReVancedUtilsPatchesVersionFingerprint
import java.util.jar.JarFile

abstract class BaseIntegrationsPatch(
    private val hooks: Set<IntegrationsFingerprint>,
) : BytecodePatch(hooks + setOf(ReVancedUtilsPatchesVersionFingerprint)) {

    @Deprecated(
        "Use the constructor without the integrationsDescriptor parameter",
        ReplaceWith("BaseIntegrationsPatch(hooks)"),
    )
    @Suppress("UNUSED_PARAMETER")
    constructor(
        integrationsDescriptor: String,
        hooks: Set<IntegrationsFingerprint>,
    ) : this(hooks)

    override fun execute(context: BytecodeContext) {
        if (context.findClass(INTEGRATIONS_CLASS_DESCRIPTOR) == null) {
            throw PatchException(
                "Integrations have not been merged yet. This patch can not succeed without merging the integrations.",
            )
        }

        hooks.forEach { hook ->
            hook.invoke(INTEGRATIONS_CLASS_DESCRIPTOR)
        }

        // Modify Utils method to include the patches release version version.
        ReVancedUtilsPatchesVersionFingerprint.resultOrThrow().mutableMethod.apply {
            val manifestValue = getPatchesManifestEntry("Version")

            addInstructions(
                0,
                """
                    const-string v0, "$manifestValue"
                    return-object v0
                """,
            )
        }
    }

    /**
     * @return The value for the manifest entry,
     *         or "Unknown" if the entry does not exist or is blank.
     */
    @Suppress("SameParameterValue")
    private fun getPatchesManifestEntry(attributeKey: String) = JarFile(getCurrentJarFilePath()).use { jarFile ->
        jarFile.manifest.mainAttributes.entries.firstOrNull { it.key.toString() == attributeKey }?.value?.toString()
            ?: "Unknown"
    }

    /**
     * @return The file path for the jar this classfile is contained inside.
     */
    private fun getCurrentJarFilePath(): String {
        val className = object {}::class.java.enclosingClass.name.replace('.', '/') + ".class"
        val classUrl = object {}::class.java.classLoader.getResource(className)
        if (classUrl != null) {
            val urlString = classUrl.toString()

            if (urlString.startsWith("jar:file:")) {
                val end = urlString.indexOf('!')
                return urlString.substring("jar:file:".length, end)
            }
        }
        throw IllegalStateException("Not running from inside a JAR file.")
    }

    /**
     * [MethodFingerprint] for integrations.
     *
     * @param contextRegisterResolver A [IRegisterResolver] to get the register.
     * @see MethodFingerprint
     */
    abstract class IntegrationsFingerprint(
        returnType: String? = null,
        accessFlags: Int? = null,
        parameters: Iterable<String>? = null,
        opcodes: Iterable<Opcode?>? = null,
        strings: Iterable<String>? = null,
        customFingerprint: ((methodDef: Method, classDef: ClassDef) -> Boolean)? = null,
        private val insertIndexResolver: ((Method) -> Int) = object : IHookInsertIndexResolver {},
        private val contextRegisterResolver: (Method) -> Int = object : IRegisterResolver {},
    ) : MethodFingerprint(
        returnType,
        accessFlags,
        parameters,
        opcodes,
        strings,
        customFingerprint,
    ) {
        @Deprecated(
            "Previous constructor that is missing the insert index." +
                    "Here only for binary compatibility, " +
                    "and this can be removed after the next major version update.",
        )
        constructor(
            returnType: String? = null,
            accessFlags: Int? = null,
            parameters: Iterable<String>? = null,
            opcodes: Iterable<Opcode?>? = null,
            strings: Iterable<String>? = null,
            customFingerprint: ((methodDef: Method, classDef: ClassDef) -> Boolean)? = null,
            contextRegisterResolver: (Method) -> Int = object : IRegisterResolver {},
        ) : this(
            returnType,
            accessFlags,
            parameters,
            opcodes,
            strings,
            customFingerprint,
            object : IHookInsertIndexResolver {},
            contextRegisterResolver,
        )

        fun invoke(integrationsDescriptor: String) {
            result?.mutableMethod?.let { method ->
                val insertIndex = insertIndexResolver(method)
                val contextRegister = contextRegisterResolver(method)

                method.addInstruction(
                    insertIndex,
                    "invoke-static/range { v$contextRegister .. v$contextRegister }, " +
                            "$integrationsDescriptor->setContext(Landroid/content/Context;)V",
                )
            } ?: throw PatchException("Could not find hook target fingerprint.")
        }

        interface IHookInsertIndexResolver : (Method) -> Int {
            override operator fun invoke(method: Method) = 0
        }

        interface IRegisterResolver : (Method) -> Int {
            override operator fun invoke(method: Method) = method.implementation!!.registerCount - 1
        }
    }

    internal companion object {
        internal const val INTEGRATIONS_CLASS_DESCRIPTOR = "Lapp/revanced/integrations/shared/Utils;"
    }
}