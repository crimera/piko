package app.crimera.patches.newx.misc.mount

import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.utils.Constants.EXTENSION_PACKAGE
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import org.w3c.dom.Element

private const val MOUNT_PROXY_DESCRIPTOR = "$EXTENSION_PACKAGE/mount/MountComponentProxy;"
private const val EXTENSION_ROOT = "Lapp/morphe/extension/"

private const val INTENT_DESCRIPTOR = "Landroid/content/Intent;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val CLASS_LOADER_DESCRIPTOR = "Ljava/lang/ClassLoader;"

private val routeMethod =
    methodReference("$MOUNT_PROXY_DESCRIPTOR->route(${INTENT_DESCRIPTOR}Landroid/content/Context;)V")

internal const val FRAMEWORK_FACTORY_DESCRIPTOR = "Landroid/app/AppComponentFactory;"

internal data class FactoryCallback(
    val name: String,
    val returnType: String,
    val resolverName: String,
) {
    val parameters = listOf(CLASS_LOADER_DESCRIPTOR, STRING_DESCRIPTOR, INTENT_DESCRIPTOR)

    val resolver: MethodReference =
        methodReference("$MOUNT_PROXY_DESCRIPTOR->$resolverName($STRING_DESCRIPTOR$INTENT_DESCRIPTOR)$STRING_DESCRIPTOR")
}

internal val factoryCallbacks =
    listOf(
        FactoryCallback("instantiateActivity", "Landroid/app/Activity;", "resolveActivityClassName"),
        FactoryCallback("instantiateReceiver", "Landroid/content/BroadcastReceiver;", "resolveReceiverClassName"),
    )

internal fun MutableMethod.insertResolveClassNameHook(callback: FactoryCallback) {
    val classNameRegister = p0Register + 2
    val intentRegister = p0Register + 3
    insertHook(
        index = 0,
        relocateBranchTargets = false,
    ) {
        invokeStatic(callback.resolver, classNameRegister, intentRegister)
        moveResult(classNameRegister, STRING_DESCRIPTOR)
    }
}

internal fun MutableClass.addFactoryCallbackOverride(
    callback: FactoryCallback,
    superclass: String,
): MutableMethod {
    if (methods.any { method -> method.matches(callback.name, callback.parameters, callback.returnType) }) {
        throw PatchException("$type already declares ${callback.name}; it cannot be added again")
    }

    val placeholder =
        MethodImplementationBuilder(4).apply {
            addInstruction("return-void".toInstruction())
        }.methodImplementation
    val method =
        MutableMethod(
            ImmutableMethod(
                type,
                callback.name,
                callback.parameters.map { parameter -> ImmutableMethodParameter(parameter, emptySet(), null) },
                callback.returnType,
                AccessFlags.PUBLIC.value,
                emptySet(),
                emptySet(),
                placeholder,
            ),
        )
    methods.add(method)
    val implementation =
        method.implementation
            ?: throw PatchException("Mount proxy override ${callback.name} has no implementation")

    val p0 = method.p0Register
    val superMethod =
        ImmutableMethodReference(superclass, callback.name, callback.parameters, callback.returnType)
    method.insertHook(
        index = 0,
        relocateBranchTargets = false,
    ) {
        invokeStatic(callback.resolver, p0 + 2, p0 + 3)
        moveResult(p0 + 2, STRING_DESCRIPTOR)
        invokeSuper(superMethod, p0, p0 + 1, p0 + 2, p0 + 3)
        moveResult(p0 + 1, callback.returnType)
        returnObject(p0 + 1)
    }
    val lastIndex = implementation.instructions.lastIndex
    if (implementation.instructions.last().opcode != Opcode.RETURN_VOID) {
        throw PatchException("Mount proxy override ${callback.name} lost its placeholder return-void")
    }
    implementation.removeInstruction(lastIndex)
    return method
}

private var appComponentFactoryDescriptor: String? = null

private val mountComponentProxyResourcePatch =
    resourcePatch {
        execute {
            document("AndroidManifest.xml").use { document ->
                val manifest = document.documentElement
                val application =
                    manifest.getElementsByTagName("application").item(0) as? Element
                        ?: throw PatchException("Mount proxy could not find the manifest application element")
                val factory = application.getAttribute("android:appComponentFactory").trim()
                appComponentFactoryDescriptor =
                    if (factory.isEmpty()) {
                        null
                    } else {
                        val className =
                            if (factory.startsWith(".")) manifest.getAttribute("package") + factory else factory
                        "L${className.replace('.', '/')};"
                    }
            }
        }
    }

internal val newXMountComponentProxyPatch =
    bytecodePatch(
        description = "Lets extension activities and receivers run when the patched app is root mounted.",
    ) {
        dependsOn(newXExtensionPatch, mountComponentProxyResourcePatch)

        execute {
            hookAppComponentFactory()
            routeExtensionIntents()
        }
    }

context(context: BytecodePatchContext)
private fun hookAppComponentFactory() {
    val factory =
        appComponentFactoryDescriptor
            ?: throw PatchException(
                "The app declares no android:appComponentFactory, so extension activities and receivers " +
                    "cannot run on a root mount",
            )
    if (context.classDefByOrNull(factory) == null) {
        throw PatchException("The app's AppComponentFactory $factory is not in the app dex")
    }

    factoryCallbacks.forEach { callback -> hookFactoryCallback(factory, callback) }
}

context(context: BytecodePatchContext)
private fun hookFactoryCallback(
    factory: String,
    callback: FactoryCallback,
) {
    val chain = mutableListOf<String>()
    var type = factory
    while (type != FRAMEWORK_FACTORY_DESCRIPTOR) {
        if (type in chain) {
            throw PatchException("AppComponentFactory superclass chain is cyclic: ${chain + type}")
        }
        chain += type
        val classDef =
            context.classDefByOrNull(type)
                ?: throw PatchException(
                    "AppComponentFactory superclass chain leaves the app dex before reaching " +
                        "$FRAMEWORK_FACTORY_DESCRIPTOR: ${chain.joinToString(" -> ")}",
                )
        val declared =
            requireAtMostOne(
                label = "AppComponentFactory ${callback.name} declaration in $type",
                candidates =
                    classDef.methods.filter { method ->
                        method.matches(callback.name, callback.parameters, callback.returnType)
                    },
            )
        if (declared != null) {
            if (declared.implementation == null || AccessFlags.ABSTRACT.isSet(declared.accessFlags)) {
                throw PatchException("$type declares ${callback.name} without an implementation")
            }
            requireExactlyOne(
                label = "AppComponentFactory ${callback.name} mutable declaration in $type",
                candidates =
                    context.mutableClassDefBy(type).methods.filter { method ->
                        method.matches(callback.name, callback.parameters, callback.returnType)
                    },
            ).insertResolveClassNameHook(callback)
            return
        }
        type = classDef.superclass
            ?: throw PatchException("AppComponentFactory chain ends without a superclass: ${chain.joinToString(" -> ")}")
    }

    val factoryClass = context.mutableClassDefBy(factory)
    val directSuperclass =
        factoryClass.superclass
            ?: throw PatchException("$factory has no superclass")
    factoryClass.addFactoryCallbackOverride(callback, directSuperclass)
}

private fun com.android.tools.smali.dexlib2.iface.Method.matches(
    name: String,
    parameters: List<String>,
    returnType: String,
): Boolean =
    this.name == name &&
        this.returnType == returnType &&
        parameterTypes.map(CharSequence::toString) == parameters

private data class IntentConstruction(
    val index: Int,
    val intentRegister: Int,
    val contextRegister: Int,
)

context(context: BytecodePatchContext)
private fun routeExtensionIntents() {
    val owners = mutableListOf<String>()
    context.classDefForEach { classDef ->
        if (!classDef.type.startsWith(EXTENSION_ROOT) || classDef.type == MOUNT_PROXY_DESCRIPTOR) {
            return@classDefForEach
        }
        val constructsIntent =
            classDef.methods.any { method ->
                method.implementation?.instructions?.any { instruction ->
                    instruction.isIntentClassConstructor()
                } == true
            }
        if (constructsIntent) owners += classDef.type
    }

    var routed = 0
    owners.forEach { owner ->
        context.mutableClassDefBy(owner).methods.forEach { method ->
            val constructions = method.intentConstructions()
            constructions.sortedByDescending(IntentConstruction::index).forEach { construction ->
                method.insertHook(
                    index = construction.index + 1,
                    relocateBranchTargets = false,
                ) {
                    invokeStatic(routeMethod, construction.intentRegister, construction.contextRegister)
                }
                routed++
            }
        }
    }

    if (routed == 0) {
        throw PatchException("Mount proxy found no extension intent addressed to an extension component")
    }
}

private fun com.android.tools.smali.dexlib2.iface.instruction.Instruction.isIntentClassConstructor(): Boolean {
    if (opcode != Opcode.INVOKE_DIRECT && opcode != Opcode.INVOKE_DIRECT_RANGE) return false
    val reference = getReference<MethodReference>() ?: return false
    return reference.definingClass == INTENT_DESCRIPTOR &&
        reference.name == "<init>" &&
        reference.returnType == "V" &&
        reference.parameterTypes.map(CharSequence::toString) ==
        listOf("Landroid/content/Context;", "Ljava/lang/Class;")
}

private fun MutableMethod.intentConstructions(): List<IntentConstruction> {
    if (implementation == null) return emptyList()
    return instructions.mapIndexedNotNull { index, instruction ->
        if (!instruction.isIntentClassConstructor()) return@mapIndexedNotNull null
        val registers =
            when (instruction) {
                is FiveRegisterInstruction ->
                    listOf(instruction.registerC, instruction.registerD, instruction.registerE)
                is RegisterRangeInstruction ->
                    List(3) { offset -> instruction.startRegister + offset }
                else -> throw PatchException("Unexpected Intent constructor encoding in $this at $index")
            }
        IntentConstruction(index = index, intentRegister = registers[0], contextRegister = registers[1])
    }
}
