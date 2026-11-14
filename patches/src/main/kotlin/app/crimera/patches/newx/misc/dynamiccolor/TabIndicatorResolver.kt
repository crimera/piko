package app.crimera.patches.newx.misc.dynamiccolor

import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.newx.misc.inlineactions.argumentRegister
import app.crimera.patches.newx.misc.inlineactions.controlFlowSuccessors
import app.crimera.patches.newx.misc.inlineactions.normalControlFlowSuccessors
import app.crimera.patches.newx.misc.inlineactions.provesParameterValue
import app.crimera.patches.newx.misc.inlineactions.provesValueFromInstruction
import app.crimera.patches.newx.misc.inlineactions.provesValueFromInstructionOnSourcePaths
import app.crimera.patches.newx.misc.inlineactions.reachableIndicesFrom
import app.crimera.patches.newx.misc.inlineactions.switchEntries
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val FUNCTION3_DESCRIPTOR = "Lkotlin/jvm/functions/Function3;"
private const val COMPOSE_MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val ANIMATION_SCOPE = "Landroidx/compose/animation/"
private const val COMPOSE_RUNTIME_SCOPE = "Landroidx/compose/runtime/"
private const val COMPOSE_FOUNDATION_SCOPE = "Landroidx/compose/foundation/"
private const val CONSTRUCTOR_NAME = "<init>"
private const val STATIC_INITIALIZER_NAME = "<clinit>"
private const val CALLBACK_INVOKE_NAME = "invoke"

private val INTEGRAL_TYPES = setOf("B", "S", "I")
private val SELECTOR_PUT_OPCODES = setOf(Opcode.IPUT_BYTE, Opcode.IPUT_SHORT, Opcode.IPUT)
private val SELECTOR_GET_OPCODES = setOf(Opcode.IGET_BYTE, Opcode.IGET_SHORT, Opcode.IGET)
private val FIELD_WRITE_OPCODES = setOf(
    Opcode.IPUT,
    Opcode.IPUT_WIDE,
    Opcode.IPUT_OBJECT,
    Opcode.IPUT_BOOLEAN,
    Opcode.IPUT_BYTE,
    Opcode.IPUT_CHAR,
    Opcode.IPUT_SHORT,
)
private val LITERAL_OPCODES =
    setOf(Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST, Opcode.CONST_HIGH16)
private val SWITCH_OPCODES = setOf(Opcode.PACKED_SWITCH, Opcode.SPARSE_SWITCH)
private val DIRECT_INVOKE_OPCODES = setOf(Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE)

/**
 * The single color read of the indicator callback that reaches the foundation background
 * Modifier. [loadIndex] is the wide J read, [callIndex] the background call it feeds, and
 * [colorRegister] the low word of that read. The tint is inserted right after [loadIndex].
 */
internal data class TabIndicatorTintSite(
    val method: Method,
    val loadIndex: Int,
    val callIndex: Int,
    val colorRegister: Int,
)

private data class CallbackFieldUse(val callIndex: Int, val field: FieldReference)

private data class FieldStore(val index: Int, val register: Int)

private data class WrapperConstruction(
    val allocationIndex: Int,
    val allocationRegister: Int,
    val constructorIndex: Int,
    val constructor: MethodReference,
)

private data class LambdaConstruction(
    val allocationIndex: Int,
    val constructorIndex: Int,
    val constructor: MethodReference,
    val type: String,
)

private data class SelectorStore(
    val index: Int,
    val field: FieldReference,
    val parameterIndex: Int,
)

/**
 * Resolves the callback that paints the tab indicator, starting from the tab slot renderer that
 * passes it to the framework animation. Every stage is proven with the existing provenance and
 * CFG helpers, and every stage requires exactly one candidate: the slot call, the field store, the
 * wrapper and lambda allocation, the selector contract, and the color read. Nothing relies on
 * obfuscated names, package identity, or fixed argument positions.
 *
 * [classFor] supplies the classes that hold the callback (the registry and the lambda). Production
 * passes mutable classes so that the indices reflect earlier mutations.
 */
internal fun resolveTabIndicatorTintSite(
    slotMethod: Method,
    horizon: String,
    classFor: (String) -> ClassDef?,
): TabIndicatorTintSite {
    val use = requireExactlyOne(
        "NewX tab indicator callback field use",
        slotMethod.callbackFieldUses(),
    ) { candidate -> "call ${candidate.callIndex} field ${candidate.field}" }
    val registry = classFor(use.field.definingClass)
        ?: throw PatchException("NewX tab indicator callback owner is missing: ${use.field}")
    // A mutable callback cache could be replaced outside <clinit>, so the SGET would not prove the
    // constructed callback. Only a static final field pins the initializer's value.
    val callbackField = requireExactlyOne(
        "NewX tab indicator callback field definition",
        registry.fields.filter { field ->
            field.name == use.field.name && field.type == use.field.type
        },
    )
    if (
        !AccessFlags.STATIC.isSet(callbackField.accessFlags) ||
            !AccessFlags.FINAL.isSet(callbackField.accessFlags)
    ) {
        throw PatchException("NewX tab indicator callback field is not static final: $callbackField")
    }
    val initializer = requireExactlyOne(
        "NewX tab indicator callback initializer method",
        registry.methods.filter { method -> method.name == STATIC_INITIALIZER_NAME },
    )
    val store = requireExactlyOne(
        "NewX tab indicator callback initializer",
        initializer.fieldStores(use.field),
    ) { candidate -> "index ${candidate.index}" }
    val wrapper = requireExactlyOne(
        "NewX tab indicator callback wrapper",
        initializer.wrapperConstructions(store),
    ) { candidate -> "allocation ${candidate.allocationIndex} constructor ${candidate.constructorIndex}" }
    val lambda = requireExactlyOne(
        "NewX tab indicator callback lambda",
        initializer.lambdaConstructions(wrapper, classFor),
    ) { candidate -> "allocation ${candidate.allocationIndex} type ${candidate.type}" }
    val lambdaClass = classFor(lambda.type)
        ?: throw PatchException("NewX tab indicator callback lambda class is missing: ${lambda.type}")
    val lambdaInit = requireExactlyOne(
        "NewX tab indicator lambda constructor",
        lambdaClass.methods.filter { candidate ->
            candidate.name == CONSTRUCTOR_NAME &&
                candidate.parameterTypes.map(CharSequence::toString) ==
                lambda.constructor.parameterTypes.map(CharSequence::toString)
        },
    )
    val invoke = requireExactlyOne(
        "NewX tab indicator callback invoke",
        lambdaClass.methods.filter { candidate ->
            candidate.name == CALLBACK_INVOKE_NAME &&
                candidate.parameterTypes.map(CharSequence::toString) ==
                listOf(OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR) &&
                candidate.returnType == OBJECT_DESCRIPTOR
        },
    )
    val selector = requireAtMostOne(
        "NewX tab indicator lambda selector store",
        lambdaInit.selectorStores(),
    ) { candidate -> candidate.field.toString() }

    // Stateless lambdas have no selector contract; their single color site must be the whole body.
    val selectedEntry: Int
    val otherEntries: Set<Int>
    if (selector == null) {
        // Only a constructor without integral parameters can be stateless; an integral parameter
        // whose store is unproven must not fall back to the whole-body scope.
        if (lambdaInit.parameterTypes.any { parameter -> parameter.toString() in INTEGRAL_TYPES }) {
            throw PatchException("NewX tab indicator lambda selector store is unproven: $lambdaInit")
        }
        // Without a selector contract the whole body is the scope, so any switch means the case
        // is unproven and the body cannot be treated as one site.
        if (invoke.instructionList().any { instruction -> instruction.opcode in SWITCH_OPCODES }) {
            throw PatchException("NewX tab indicator stateless lambda contains a switch: $invoke")
        }
        selectedEntry = 0
        otherEntries = emptySet()
    } else {
        // A constructor value only proves the invoked case while the selector cannot change later.
        val selectorField = requireExactlyOne(
            "NewX tab indicator lambda selector field",
            lambdaClass.fields.filter { field ->
                field.name == selector.field.name && field.type == selector.field.type
            },
        )
        if (
            !AccessFlags.FINAL.isSet(selectorField.accessFlags) ||
                AccessFlags.STATIC.isSet(selectorField.accessFlags)
        ) {
            throw PatchException("NewX tab indicator lambda selector field is not an instance final field: $selectorField")
        }
        // Any second write to the selector, constant or not, would make the constructor value
        // stale; selectorStores only sees parameter-derived writes, so count every IPUT here.
        val selectorWrite = requireExactlyOne(
            "NewX tab indicator lambda selector constructor write",
            lambdaInit.instructionList().withIndex().filter { (_, instruction) ->
                instruction.opcode in FIELD_WRITE_OPCODES &&
                    instruction.getReference<FieldReference>()?.toString() == selector.field.toString()
            },
        ) { candidate -> "index ${candidate.index}" }
        lambdaInit.requireSelectorWriteDominatesReturns(selectorWrite.index)
        lambdaInit.requireNoSelfDelegation(lambda.type)
        val selectorValue = initializer.selectorValue(
            consumerIndex = lambda.constructorIndex,
            consumerRegister = initializer.instructionAt(lambda.constructorIndex)
                .argumentRegister(lambda.constructor, selector.parameterIndex)
                ?: throw PatchException("NewX tab indicator selector argument is missing: $lambda"),
            fieldType = selector.field.type,
        )
        val switchTuple = requireExactlyOne(
            "NewX tab indicator selector switch",
            invoke.selectorSwitches(selector.field),
        ) { candidate -> "get ${candidate.first} switch ${candidate.second}" }
        val entries = invoke.switchEntries(switchTuple.second)
            ?: throw PatchException("NewX tab indicator selector switch is unsupported: $invoke")
        selectedEntry = entries.byKey[selectorValue] ?: entries.fallthrough
        otherEntries = (entries.byKey.values + entries.fallthrough).toSet() - selectedEntry
    }

    // Case exclusivity must hold over exception edges too: a catch handler reached from another
    // case would otherwise enter the selected color read.
    val successors = invoke.controlFlowSuccessors()
        ?: throw PatchException("NewX tab indicator callback has unsupported control flow: $invoke")
    val selectedReach = successors.reachableIndicesFrom(selectedEntry)
        ?: throw PatchException("NewX tab indicator control flow is invalid from $selectedEntry: $invoke")
    if (otherEntries.any { entry -> entry in selectedReach }) {
        throw PatchException("NewX tab indicator selected case shares its scope with another case: $invoke")
    }
    val otherReach = otherEntries.flatMapTo(mutableSetOf()) { entry ->
        successors.reachableIndicesFrom(entry)
            ?: throw PatchException("NewX tab indicator control flow is invalid from $entry: $invoke")
    }
    val reachable = selectedReach - otherReach
    return requireExactlyOne(
        "NewX tab indicator color site in $invoke for ${use.field}",
        invoke.tabIndicatorColorSites(reachable, horizon),
    ) { candidate -> "load ${candidate.loadIndex} call ${candidate.callIndex}" }
}

private fun Method.instructionList(): List<Instruction> = implementation?.instructions?.toList().orEmpty()

private fun Method.instructionAt(index: Int): Instruction = instructionList()[index]

private fun Instruction.fieldReadDestination(): Int? =
    (this as? OneRegisterInstruction)?.registerA ?: (this as? TwoRegisterInstruction)?.registerA

private fun MethodReference.isAnimationCallbackCall(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return definingClass.startsWith(ANIMATION_SCOPE) &&
        returnType == "V" &&
        FUNCTION3_DESCRIPTOR in parameters &&
        "Landroidx/compose/runtime/Composer;" in parameters &&
        COMPOSE_MODIFIER_DESCRIPTOR in parameters
}

/** Static framework animation calls whose Function3 argument is proven to come from an SGET_OBJECT. */
private fun Method.callbackFieldUses(): List<CallbackFieldUse> {
    val code = instructionList()
    val uses = mutableListOf<CallbackFieldUse>()
    code.forEachIndexed { callIndex, call ->
        if (call.opcode != Opcode.INVOKE_STATIC && call.opcode != Opcode.INVOKE_STATIC_RANGE) return@forEachIndexed
        val reference = call.getReference<MethodReference>() ?: return@forEachIndexed
        if (!reference.isAnimationCallbackCall()) return@forEachIndexed
        val callbackParameter = requireExactlyOne(
            "NewX tab indicator animation callback parameter",
            reference.parameterTypes.indices.filter { parameter ->
                reference.parameterTypes[parameter].toString() == FUNCTION3_DESCRIPTOR
            },
        )
        val callbackRegister = call.argumentRegister(reference, callbackParameter) ?: return@forEachIndexed
        code.forEachIndexed { sourceIndex, source ->
            if (source.opcode != Opcode.SGET_OBJECT) return@forEachIndexed
            val field = source.getReference<FieldReference>() ?: return@forEachIndexed
            val sourceRegister = source.fieldReadDestination() ?: return@forEachIndexed
            if (provesValueFromInstruction(sourceIndex, sourceRegister, callIndex, callbackRegister)) {
                uses += CallbackFieldUse(callIndex, field)
            }
        }
    }
    return uses
}

private fun Method.fieldStores(field: FieldReference): List<FieldStore> =
    instructionList().mapIndexedNotNull { index, instruction ->
        if (instruction.opcode != Opcode.SPUT_OBJECT) return@mapIndexedNotNull null
        if (instruction.getReference<FieldReference>()?.toString() != field.toString()) {
            return@mapIndexedNotNull null
        }
        FieldStore(index, (instruction as OneRegisterInstruction).registerA)
    }

/** Compose runtime wrappers allocated in the initializer and proven to reach [store]. */
private fun Method.wrapperConstructions(store: FieldStore): List<WrapperConstruction> {
    val code = instructionList()
    val results = mutableListOf<WrapperConstruction>()
    for (allocation in code.indices) {
        if (code[allocation].opcode != Opcode.NEW_INSTANCE) continue
        val type = code[allocation].getReference<TypeReference>()?.type ?: continue
        if (!type.startsWith(COMPOSE_RUNTIME_SCOPE)) continue
        val register = (code[allocation] as? OneRegisterInstruction)?.registerA ?: continue
        if (!provesValueFromInstruction(allocation, register, store.index, store.register)) continue
        // The wrapper must be fully built before the static store that publishes it.
        for (constructor in allocation + 1 until store.index) {
            if (code[constructor].opcode !in DIRECT_INVOKE_OPCODES) continue
            val reference = code[constructor].getReference<MethodReference>() ?: continue
            if (reference.name != CONSTRUCTOR_NAME || reference.definingClass != type) continue
            if (reference.parameterTypes.map(CharSequence::toString) != listOf("I", "Z", OBJECT_DESCRIPTOR)) {
                continue
            }
            val receiver = code[constructor].registersUsed.firstOrNull() ?: continue
            if (provesValueFromInstruction(allocation, register, constructor, receiver)) {
                results += WrapperConstruction(allocation, register, constructor, reference)
            }
        }
    }
    return results
}

/** Function3 lambdas allocated and constructed into the wrapper's Object argument. */
private fun Method.lambdaConstructions(
    wrapper: WrapperConstruction,
    classFor: (String) -> ClassDef?,
): List<LambdaConstruction> {
    val code = instructionList()
    val objectParameter = wrapper.constructor.parameterTypes.indexOfFirst { parameter ->
        parameter.toString() == OBJECT_DESCRIPTOR
    }
    val objectRegister = code[wrapper.constructorIndex].argumentRegister(wrapper.constructor, objectParameter)
        ?: return emptyList()
    val results = mutableListOf<LambdaConstruction>()
    for (allocation in code.indices) {
        if (code[allocation].opcode != Opcode.NEW_INSTANCE) continue
        val type = code[allocation].getReference<TypeReference>()?.type ?: continue
        val register = (code[allocation] as? OneRegisterInstruction)?.registerA ?: continue
        if (!provesValueFromInstruction(allocation, register, wrapper.constructorIndex, objectRegister)) continue
        if (classFor(type)?.interfaces?.any { it.toString() == FUNCTION3_DESCRIPTOR } != true) continue
        // Lambda allocation, its constructor, then the wrapper capture: that order is the contract.
        for (constructor in allocation + 1 until wrapper.constructorIndex) {
            if (code[constructor].opcode !in DIRECT_INVOKE_OPCODES) continue
            val reference = code[constructor].getReference<MethodReference>() ?: continue
            if (reference.name != CONSTRUCTOR_NAME || reference.definingClass != type) continue
            val receiver = code[constructor].registersUsed.firstOrNull() ?: continue
            if (provesValueFromInstruction(allocation, register, constructor, receiver)) {
                results += LambdaConstruction(allocation, constructor, reference, type)
            }
        }
    }
    return results
}

/** Parameter register of [index] in this method, counting wide parameters as two words. */
private fun Method.parameterRegister(index: Int): Int {
    val first = p0Register + if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
    return first + parameterTypes.take(index).sumOf { parameter ->
        if (parameter.toString() == "J" || parameter.toString() == "D") 2 else 1
    }
}

/**
 * Constructor stores of an integral parameter into a field of `this`. Stateless lambdas have none;
 * a store whose value is not provably one parameter is a contract violation and fails closed.
 */
private fun Method.selectorStores(): List<SelectorStore> {
    val code = instructionList()
    return code.indices.mapNotNull { index ->
        val store = code[index]
        if (store.opcode !in SELECTOR_PUT_OPCODES) return@mapNotNull null
        val field = store.getReference<FieldReference>() ?: return@mapNotNull null
        val pair = store as? TwoRegisterInstruction ?: return@mapNotNull null
        val value = pair.registerA
        if (field.type !in INTEGRAL_TYPES) return@mapNotNull null
        val receiver = pair.registerB
        if (!provesParameterValue(index, receiver, p0Register)) return@mapNotNull null
        val parameter = requireAtMostOne(
            "NewX tab indicator lambda selector parameter",
            parameterTypes.indices.filter { parameter ->
                parameterTypes[parameter].toString() in INTEGRAL_TYPES &&
                    provesParameterValue(index, value, parameterRegister(parameter))
            },
        )
        parameter?.let { SelectorStore(index, field, it) }
    }
}

/** The integral literal the registry initializer passes to the lambda selector constructor argument. */
private fun Method.selectorValue(consumerIndex: Int, consumerRegister: Int, fieldType: String): Int {
    val code = instructionList()
    val literals = code.indices.filter { index ->
        code[index].opcode in LITERAL_OPCODES &&
            (code[index] as? OneRegisterInstruction)?.registerA?.let { register ->
                provesValueFromInstruction(index, register, consumerIndex, consumerRegister)
            } == true
    }
    val literal = requireExactlyOne("NewX tab indicator selector literal", literals)
    val value = (code[literal] as NarrowLiteralInstruction).narrowLiteral
    return when (fieldType) {
        "B" -> value.toByte().toInt()
        "S" -> value.toShort().toInt()
        else -> value
    }
}

/**
 * The selector write must be reachable from the constructor entry and must dominate every
 * RETURN_VOID: removing the write from the successor table cannot leave a return reachable.
 */
private fun Method.requireSelectorWriteDominatesReturns(writeIndex: Int) {
    // The normal-edge graph cannot prove dominance through caught exceptions, so a constructor
    // with try blocks is rejected here even when the shared CFG later models exception edges.
    if (implementation?.tryBlocks?.isNotEmpty() != false) {
        throw PatchException("NewX tab indicator lambda constructor has try blocks or no body: $this")
    }
    val code = instructionList()
    val successors = normalControlFlowSuccessors()
        ?: throw PatchException("NewX tab indicator lambda constructor has unsupported control flow: $this")
    val reachable = successors.reachableIndicesFrom(0)
        ?: throw PatchException("NewX tab indicator lambda constructor control flow is invalid: $this")
    if (writeIndex !in reachable) {
        throw PatchException("NewX tab indicator lambda selector write is unreachable: $this")
    }
    val beforeWrite = successors.mapIndexed { index, next -> if (index == writeIndex) emptyList() else next }
    val unwritten = beforeWrite.reachableIndicesFrom(0)
        ?: throw PatchException("NewX tab indicator lambda constructor control flow is invalid: $this")
    if (unwritten.any { index -> code[index].opcode == Opcode.RETURN_VOID }) {
        throw PatchException("NewX tab indicator lambda constructor has a return before its selector write: $this")
    }
}

/** A constructor that delegates to another constructor of its own class on `this` could overwrite the selector. */
private fun Method.requireNoSelfDelegation(lambdaType: String) {
    val code = instructionList()
    val delegations = code.indices.filter { index ->
        code[index].opcode in DIRECT_INVOKE_OPCODES &&
            code[index].getReference<MethodReference>()?.let { reference ->
                reference.name == CONSTRUCTOR_NAME && reference.definingClass == lambdaType
            } == true &&
            code[index].registersUsed.firstOrNull()?.let { receiver ->
                provesParameterValue(index, receiver, p0Register)
            } == true
    }
    if (delegations.isNotEmpty()) {
        throw PatchException("NewX tab indicator lambda constructor delegates to itself at $delegations: $this")
    }
}

/** (get, switch) pairs where a get of [field] on `this` feeds a switch selector. */
private fun Method.selectorSwitches(field: FieldReference): List<Pair<Int, Int>> {
    val code = instructionList()
    val thisRegister = p0Register
    val results = mutableListOf<Pair<Int, Int>>()
    for (get in code.indices) {
        val instruction = code[get]
        if (instruction.opcode !in SELECTOR_GET_OPCODES) continue
        if (instruction.getReference<FieldReference>()?.toString() != field.toString()) continue
        val receiver = (instruction as? TwoRegisterInstruction)?.registerB ?: continue
        if (!provesParameterValue(get, receiver, thisRegister)) continue
        val destination = instruction.fieldReadDestination() ?: continue
        for (switch in code.indices) {
            if (code[switch].opcode !in SWITCH_OPCODES) continue
            val selector = (code[switch] as? OneRegisterInstruction)?.registerA ?: continue
            if (provesValueFromInstruction(get, destination, switch, selector)) results += get to switch
        }
    }
    return results
}

private fun Instruction.isColorRead(horizon: String): Boolean =
    (opcode == Opcode.IGET_WIDE || opcode == Opcode.SGET_WIDE) &&
        getReference<FieldReference>()?.let { field ->
            field.type == "J" &&
                field.definingClass != horizon &&
                FRAMEWORK_OWNER_PREFIXES.none(field.definingClass::startsWith)
        } == true

private fun MethodReference.isFoundationBackground(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return definingClass.startsWith(COMPOSE_FOUNDATION_SCOPE) &&
        parameters.firstOrNull() == COMPOSE_MODIFIER_DESCRIPTOR &&
        "J" in parameters &&
        returnType == COMPOSE_MODIFIER_DESCRIPTOR
}

/**
 * Wide color reads inside [reachable] whose low and high words both reach the J argument of a
 * foundation background call along every path from the read.
 */
private fun Method.tabIndicatorColorSites(
    reachable: Set<Int>,
    horizon: String,
): List<TabIndicatorTintSite> {
    val code = instructionList()
    val loads = reachable.filter { code[it].isColorRead(horizon) }
    val calls = reachable.filter { code[it].getReference<MethodReference>()?.isFoundationBackground() == true }
    val sites = mutableListOf<TabIndicatorTintSite>()
    for (load in loads) {
        val low = code[load].fieldReadDestination() ?: continue
        for (call in calls) {
            val reference = code[call].getReference<MethodReference>() ?: continue
            val colorParameter = requireExactlyOne(
                "NewX tab indicator background color parameter",
                reference.parameterTypes.indices.filter { parameter ->
                    reference.parameterTypes[parameter].toString() == "J"
                },
            )
            val colorRegister = code[call].argumentRegister(reference, colorParameter) ?: continue
            if (
                provesValueFromInstructionOnSourcePaths(load, low, call, colorRegister) &&
                provesValueFromInstructionOnSourcePaths(load, low + 1, call, colorRegister + 1)
            ) {
                sites += TabIndicatorTintSite(this, load, call, low)
            }
        }
    }
    return sites
}
