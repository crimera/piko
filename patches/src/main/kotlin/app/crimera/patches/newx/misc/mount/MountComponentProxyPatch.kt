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

/*
 * Root mount support for extension-owned components.
 *
 * A mount keeps the stock manifest registered with the package manager, so the activities and
 * receivers the patches add to the manifest do not exist for the system. The extension class
 * `MountComponentProxy` routes launches of those components through a registered stock component
 * and this patch makes the app's AppComponentFactory instantiate the real extension class in its
 * place. Standard installs are unaffected: their components are registered and never rerouted.
 */

private const val MOUNT_PROXY_DESCRIPTOR = "$EXTENSION_PACKAGE/mount/MountComponentProxy;"
private const val EXTENSION_ROOT = "Lapp/morphe/extension/"

private const val INTENT_DESCRIPTOR = "Landroid/content/Intent;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val CLASS_LOADER_DESCRIPTOR = "Ljava/lang/ClassLoader;"

private val routeMethod =
    methodReference("$MOUNT_PROXY_DESCRIPTOR->route(${INTENT_DESCRIPTOR}Landroid/content/Context;)V")

/** The framework base class every `android:appComponentFactory` must extend. */
internal const val FRAMEWORK_FACTORY_DESCRIPTOR = "Landroid/app/AppComponentFactory;"

/**
 * An `AppComponentFactory` callback the hook rewrites and its `MountComponentProxy` resolver,
 * `(String className, Intent) -> String`. The resolver encodes the component kind in its name so the
 * hook needs no scratch register: compiled factory overrides can have no free locals.
 */
internal data class FactoryCallback(
    val name: String,
    val returnType: String,
    val resolverName: String,
) {
    /** `(ClassLoader, String className, Intent)`: p1, p2, p3 of the instance method. */
    val parameters = listOf(CLASS_LOADER_DESCRIPTOR, STRING_DESCRIPTOR, INTENT_DESCRIPTOR)

    val resolver: MethodReference =
        methodReference("$MOUNT_PROXY_DESCRIPTOR->$resolverName($STRING_DESCRIPTOR$INTENT_DESCRIPTOR)$STRING_DESCRIPTOR")
}

internal val factoryCallbacks =
    listOf(
        FactoryCallback("instantiateActivity", "Landroid/app/Activity;", "resolveActivityClassName"),
        FactoryCallback("instantiateReceiver", "Landroid/content/BroadcastReceiver;", "resolveReceiverClassName"),
    )

/**
 * Contract: inserts at index 0 of an existing callback implementation
 * `invoke-static {p2, p3}, callback.resolver; move-result-object p2` (className = p2, intent = p3).
 * Uses no scratch register.
 */
internal fun MutableMethod.insertResolveClassNameHook(callback: FactoryCallback) {
    val classNameRegister = p0Register + 2
    val intentRegister = p0Register + 3
    insertHook(
        index = 0,
        // A branch back to the entry would only repeat the idempotent class name rewrite.
        relocateBranchTargets = false,
    ) {
        invokeStatic(callback.resolver, classNameRegister, intentRegister)
        moveResult(classNameRegister, STRING_DESCRIPTOR)
    }
}

/**
 * Contract: adds `public <callback>(ClassLoader, String, Intent)<Ret>` to [this] factory class, which
 * declares no such method. Body: the resolve hook, then
 * `invoke-super {p0, p1, p2, p3}, <superclass>-><callback>(...)`, `move-result-object`, `return-object`.
 * [superclass] is this class's direct superclass. Returns the added method.
 */
internal fun MutableClass.addFactoryCallbackOverride(
    callback: FactoryCallback,
    superclass: String,
): MutableMethod {
    if (methods.any { method -> method.matches(callback.name, callback.parameters, callback.returnType) }) {
        throw PatchException("$type already declares ${callback.name}; it cannot be added again")
    }

    // No locals: p0..p3 = this, ClassLoader, String, Intent.
    // The four argument registers are the whole invoke-super operand list.
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
        // The placeholder return-void carries no labels, so there are no branch targets to relocate.
        relocateBranchTargets = false,
    ) {
        invokeStatic(callback.resolver, p0 + 2, p0 + 3)
        moveResult(p0 + 2, STRING_DESCRIPTOR)
        invokeSuper(superMethod, p0, p0 + 1, p0 + 2, p0 + 3)
        // p1 (the ClassLoader) is dead after the super call, so it holds the result: no scratch register.
        moveResult(p0 + 1, callback.returnType)
        returnObject(p0 + 1)
    }
    // The hook ends with return-object, so the placeholder is now the unreachable last instruction.
    val lastIndex = implementation.instructions.lastIndex
    if (implementation.instructions.last().opcode != Opcode.RETURN_VOID) {
        throw PatchException("Mount proxy override ${callback.name} lost its placeholder return-void")
    }
    implementation.removeInstruction(lastIndex)
    return method
}

/** The manifest's `android:appComponentFactory`, as a type descriptor, or null when it has none. */
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

/**
 * Rewrites the class name at the start of the factory callbacks. The factory is the only process
 * side entry point that sees both the component class name and the launching intent.
 */
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

/**
 * Hooks the most derived implementation of [callback] in the factory's dex chain, or adds an
 * override to the factory itself when the chain reaches the framework factory without one.
 */
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

/**
 * Calls the router after every `new Intent(Context, Class)` in extension code, the only shape the
 * extensions use to address their own components. The router leaves registered components alone.
 */
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
            // Insert from the end so earlier indices stay valid.
            constructions.sortedByDescending(IntentConstruction::index).forEach { construction ->
                method.insertHook(
                    index = construction.index + 1,
                    // A jump to the next instruction skips the reroute; the intent then keeps its
                    // original component, which is the standard install behaviour.
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
