package app.crimera.patches.newx.premium

import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.bytecode.insertHook
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.OBJECT_MOVE_OPCODES
import app.crimera.patches.common.INTEGER_MOVE_OPCODES
import app.crimera.patches.common.scopedMatchAll
import app.crimera.patches.common.scopedMatchAllOrNull
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private fun Method.subscriptionCalls(): List<Int> =
    implementation!!.instructions.toList().mapIndexedNotNull { index, instruction ->
        val reference = instruction.getReference<MethodReference>()
        index.takeIf {
            instruction.opcode.toString().startsWith("INVOKE_") &&
                reference?.definingClass?.startsWith("Lcom/x/subscriptions/") == true &&
                reference.returnType == "Z"
        }
    }

private fun MutableMethod.subscriptionResults(): List<Int> = subscriptionCalls().map { index ->
    if (instructions.getOrNull(index + 1)?.opcode != Opcode.MOVE_RESULT) {
        throw PatchException("NewX subscription invoke has no boolean result in $this at $index")
    }
    index + 1
}

// Plans contain original result indexes; no method is edited until capability validation succeeds.
private fun MutableMethod.forceSubscriptionFeatureResults(results: List<Int>) {
    results.sortedDescending().forEach { resultIndex ->
        val resultRegister = instructions[resultIndex].registersUsed[0]
        // The old insertion left every label on the instruction after move-result, so a branch
        // that reaches it directly keeps skipping the hook, exactly as before.
        insertHook(resultIndex + 1, relocateBranchTargets = false) {
            constInt(resultRegister, 1)
        }
    }
}

/** Small intraprocedural proof, scoped to the matched handler and its captured coroutine. */
private class DownloadFlow(
    val method: Method,
    private val selectedDispatchOnly: Boolean = false,
    private val hostParameterType: String? = null,
) {
    private val implementation = method.implementation
        ?: throw PatchException("NewX download proof method has no implementation: $method")
    val instructions = implementation.instructions.toList()
    private val offsets = instructions.runningFold(0) { offset, instruction -> offset + instruction.codeUnits }
    private val indexes = offsets.withIndex().associate { it.value to it.index }
    val receiver = implementation.registerCount - method.parameterTypes.sumOf {
        if (it.toString() in setOf("J", "D")) 2 else 1
    } - if (AccessFlags.STATIC.isSet(method.accessFlags)) 0 else 1

    init {
        if ((AccessFlags.STATIC.isSet(method.accessFlags) &&
                (hostParameterType == null || method.parameterTypes.firstOrNull()?.toString() != hostParameterType)) ||
            (!selectedDispatchOnly && implementation.tryBlocks.isNotEmpty())
        ) {
            throw PatchException("Unsupported NewX download proof method: $method (static or exception edges)")
        }
    }

    fun target(index: Int): Int = indexes[offsets[index] + (instructions[index] as OffsetInstruction).codeOffset]
        ?: throw PatchException("Invalid NewX download branch in $method at $index")

    fun successors(index: Int): List<Int> {
        val instruction = instructions[index]
        val opcode = instruction.opcode
        if (opcode.toString().startsWith("RETURN") || opcode == Opcode.THROW || instruction is SwitchPayload) return emptyList()
        if (opcode in setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)) return listOf(target(index))
        if (opcode == Opcode.PACKED_SWITCH || opcode == Opcode.SPARSE_SWITCH) {
            val payload = instructions[target(index)] as SwitchPayload
            return payload.switchElements.map { element ->
                indexes.getValue(offsets[index] + element.offset)
            } + (index + 1)
        }
        if (opcode.toString().startsWith("IF_")) return listOf(target(index), index + 1)
        return listOf(index + 1).filter { it < instructions.size }
    }

    private val predecessors by lazy {
        Array(instructions.size) { mutableListOf<Int>() }.also { predecessors ->
            instructions.indices.forEach { index -> successors(index).forEach { predecessors[it].add(index) } }
        }
    }

    private val normalReachable by lazy {
        val pending = mutableListOf(0)
        buildSet {
            while (pending.isNotEmpty()) {
                val index = pending.removeAt(pending.lastIndex)
                if (add(index)) pending.addAll(successors(index))
            }
        }
    }
    private var selectedReachable: Set<Int>? = null
    private val reachable: Set<Int> get() = selectedReachable ?: normalReachable
    val proofIndexes: Set<Int> get() = reachable

    private fun requireUnprotected(index: Int) {
        val offset = offsets[index]
        val overlapping = implementation.tryBlocks.filter { block ->
            offset >= block.startCodeAddress && offset < block.startCodeAddress + block.codeUnitCount ||
                block.exceptionHandlers.any { it.handlerCodeAddress == offset }
        }
        if (overlapping.isNotEmpty()) {
            throw PatchException(
                "NewX selected coroutine path overlaps exception region in $method at code-unit $offset: " +
                    overlapping.joinToString { "${it.startCodeAddress}..${it.startCodeAddress + it.codeUnitCount}" },
            )
        }
    }

    // Negative origins encode entry registers; nonnegative origins identify real definitions.
    fun origin(index: Int, register: Int): Int? {
        if (index !in reachable || register !in 0 until implementation.registerCount) return null
        val pending = mutableListOf(index to register)
        val visited = mutableSetOf<Pair<Int, Int>>()
        val origins = mutableSetOf<Int>()
        while (pending.isNotEmpty()) {
            val edge = pending.removeAt(pending.lastIndex)
            if (!visited.add(edge)) continue
            val (before, tracked) = edge
            if (before == 0) origins.add(-tracked - 1)
            for (previous in predecessors[before]) {
                if (previous !in reachable) continue
                val instruction = instructions[previous]
                val destination = (instruction as? OneRegisterInstruction)?.registerA
                // Both halves are definitions; a scalar/capture proof cannot use either half.
                if (instruction.opcode.setsWideRegister() &&
                    (destination == tracked || destination == tracked - 1)
                ) return null
                if (destination != tracked || !instruction.opcode.setsRegister()) {
                    pending.add(previous to tracked)
                    continue
                }
                if (instruction.opcode in OBJECT_MOVE_OPCODES || instruction.opcode in INTEGER_MOVE_OPCODES) {
                    pending.add(previous to (instruction as TwoRegisterInstruction).registerB)
                } else if (instruction.opcode == Opcode.CHECK_CAST) {
                    pending.add(previous to tracked)
                } else {
                    origins.add(previous)
                }
            }
        }
        // This is a finite reaching-definition worklist, not a linear backward guess. Cycles
        // contribute all external definitions; a closed cycle without an entry yields none.
        return origins.singleOrNull()
    }

    fun literal(index: Int, register: Int): Int? = origin(index, register)?.takeIf { it >= 0 }
        ?.let { instructions[it] as? NarrowLiteralInstruction }?.narrowLiteral

    fun fieldOrigin(index: Int, register: Int): Pair<Int, FieldReference>? {
        val source = origin(index, register)?.takeIf { it >= 0 } ?: return null
        if (instructions[source].opcode !in setOf(Opcode.IGET_OBJECT, Opcode.IGET_BYTE, Opcode.IGET)) return null
        return instructions[source].getReference<FieldReference>()?.let { source to it }
    }

    fun readsReceiver(index: Int): Boolean =
        origin(index, (instructions[index] as TwoRegisterInstruction).registerB) == -receiver - 1

    fun selectedDispatch(field: FieldReference, value: Int): Set<Int> {
        var selected = false
        val pending = mutableListOf(0)
        val visited = mutableSetOf<Int>()
        while (pending.isNotEmpty()) {
            val index = pending.removeAt(pending.lastIndex)
            if (!visited.add(index)) continue
            // Merged coroutines may contain unrelated protected cases. Only the derived selector's
            // prefix and complete normal path matter; any protected instruction on that path fails.
            requireUnprotected(index)
            val instruction = instructions[index]
            if (instruction.opcode in setOf(Opcode.PACKED_SWITCH, Opcode.SPARSE_SWITCH) &&
                fieldOrigin(index, (instruction as OneRegisterInstruction).registerA)?.let {
                    it.second.toString() == field.toString() && readsReceiver(it.first)
                } == true
            ) {
                selected = true
                val payload = instructions[target(index)] as SwitchPayload
                val cases = payload.switchElements.filter { it.key == value }
                val case = requireExactlyOne("NewX captured coroutine dispatch $method selector=$value", cases)
                pending.add(indexes.getValue(offsets[index] + case.offset))
                continue
            }
            pending.addAll(successors(index))
        }
        if (!selected) throw PatchException("No captured discriminator dispatch in NewX coroutine $method")
        // Origin queries after selection must not pick definitions from another merged case.
        selectedReachable = visited
        return visited
    }
}

private fun downloadMethod(reference: MethodReference, classByType: (String) -> ClassDef?): Method = requireExactlyOne(
    "NewX download callee $reference",
    classByType(reference.definingClass)?.methods?.toList().orEmpty().filter { it.toString() == reference.toString() },
)

private fun Method.hasDownloadString(value: String): Boolean = implementation?.instructions?.any {
    it.getReference<StringReference>()?.string == value
} == true

/** The quota policy must return Allowed immediately for the resolved two-tier premium result. */
private fun Method.isPremiumOfflinePolicy(offlinePremium: MethodReference): Boolean {
    if (!hasDownloadString("android_x_lite_offline_free_saves_enabled") ||
        !hasDownloadString("android_x_lite_offline_free_saves_limit")
    ) return false
    val flow = DownloadFlow(this)
    val checks = subscriptionCalls()
    val gate = requireExactlyOne("NewX offline quota premium gate in $this", checks)
    val instructions = flow.instructions
    if (instructions[gate].getReference<MethodReference>()?.toString() != offlinePremium.toString() ||
        instructions.getOrNull(gate + 1)?.opcode != Opcode.MOVE_RESULT ||
        instructions.getOrNull(gate + 2)?.opcode != Opcode.IF_EQZ ||
        flow.origin(gate + 2, (instructions[gate + 2] as OneRegisterInstruction).registerA) != gate + 1
    ) throw PatchException("Unexpected NewX offline quota premium branch in $this")
    val allowed = instructions.getOrNull(gate + 3)?.getReference<FieldReference>()
    val returned = instructions.getOrNull(gate + 4)
    if (instructions.getOrNull(gate + 3)?.opcode != Opcode.SGET_OBJECT || allowed?.name != "Allowed" ||
        allowed.type != returnType || returned?.opcode != Opcode.RETURN_OBJECT ||
        flow.origin(gate + 4, (returned as OneRegisterInstruction).registerA) != gate + 3 ||
        flow.target(gate + 2) <= gate + 4
    ) throw PatchException("NewX premium quota branch does not return Allowed in $this")
    return true
}

private fun Method.delegatesToOfflinePolicy(offlinePremium: MethodReference, classByType: (String) -> ClassDef?): Boolean {
    if (subscriptionCalls().isNotEmpty()) throw PatchException("Unexpected direct subscription gate in NewX offline policy adapter $this")
    val flow = DownloadFlow(this)
    val delegates = flow.instructions.indices.filter { index ->
        val instruction = flow.instructions[index]
        val reference = instruction.getReference<MethodReference>() ?: return@filter false
        if (!instruction.opcode.toString().startsWith("INVOKE_VIRTUAL") || reference.returnType == "V") return@filter false
        val receiver = instruction.registersUsed[0]
        val field = flow.fieldOrigin(index, receiver) ?: return@filter false
        if (!flow.readsReceiver(field.first)) return@filter false
        val callee = downloadMethod(reference, classByType)
        if (!callee.isPremiumOfflinePolicy(offlinePremium)) return@filter false
        val result = flow.instructions.getOrNull(index + 1)
        val returned = flow.instructions.getOrNull(index + 2)
        result?.opcode == Opcode.MOVE_RESULT_OBJECT && returned?.opcode == Opcode.RETURN_OBJECT &&
            flow.origin(index + 2, (returned as OneRegisterInstruction).registerA) == index + 1
    }
    return requireAtMostOne("NewX offline premium policy delegation in $this", delegates) != null
}

private fun Method.hasTimelineOfflinePolicy(
    hostType: String,
    offlinePremium: MethodReference,
    classByType: (String) -> ClassDef?,
): Boolean {
    if (subscriptionCalls().isNotEmpty()) throw PatchException("Unexpected subscription gate in NewX timeline policy $this")
    val flow = DownloadFlow(this, hostParameterType = hostType)
    val policies = flow.proofIndexes.filter { index ->
        val instruction = flow.instructions[index]
        val reference = instruction.getReference<MethodReference>() ?: return@filter false
        if (!instruction.opcode.toString().startsWith("INVOKE_VIRTUAL") || reference.returnType != "Ljava/lang/Enum;" ||
            reference.parameterTypes.map(CharSequence::toString) != listOf("Ljava/lang/String;", "Lkotlin/coroutines/jvm/internal/ContinuationImpl;")
        ) return@filter false
        val field = flow.fieldOrigin(index, instruction.registersUsed[0]) ?: return@filter false
        field.second.definingClass == hostType && flow.readsReceiver(field.first) &&
            downloadMethod(reference, classByType).delegatesToOfflinePolicy(offlinePremium, classByType)
    }
    return requireAtMostOne("NewX timeline host offline policy in $this", policies) != null
}

private fun proveDownstreamOfflinePremium(
    handler: MutableMethod,
    offlinePremium: MethodReference,
    classByType: (String) -> ClassDef?,
    timelineConstructor: Int? = null,
    hostField: FieldReference? = null,
) {
    val flow = DownloadFlow(handler)
    val instructions = flow.instructions
    val offlineEvent = requireExactlyOne("NewX offline save event in $handler", instructions.indices.filter {
        instructions[it].getReference<StringReference>()?.string == "download_video_to_offline"
    })
    val ordinaryEvent = requireExactlyOne("NewX offline proof boundary in $handler", instructions.indices.filter {
        instructions[it].getReference<StringReference>()?.string == if (hostField == null) "download_video" else "remove_video_from_offline"
    })
    if (ordinaryEvent <= offlineEvent) throw PatchException("Unexpected NewX download event layout in $handler")

    fun capturesHost(index: Int, register: Int): Boolean {
        if (hostField == null) return flow.origin(index, register) == -flow.receiver - 1
        val source = flow.fieldOrigin(index, register) ?: return false
        return source.second.toString() == hostField.toString() && flow.readsReceiver(source.first)
    }
    val hostType = hostField?.type ?: handler.definingClass
    val constructors = (offlineEvent until ordinaryEvent).filter { index ->
        if (timelineConstructor != null && index != timelineConstructor) return@filter false
        val reference = instructions[index].getReference<MethodReference>() ?: return@filter false
        reference.name == "<init>" &&
            classByType(reference.definingClass)?.superclass == "Lkotlin/coroutines/jvm/internal/SuspendLambda;" &&
            instructions[index].registersUsed.drop(1).any { capturesHost(index, it) }
    }
    val constructorIndex = requireExactlyOne("NewX offline coroutine capturing $handler", constructors)
    val constructorCall = instructions[constructorIndex]
    val constructor = downloadMethod(constructorCall.getReference<MethodReference>()!!, classByType)
    val constructorFlow = DownloadFlow(constructor)
    val arguments = constructorCall.registersUsed
    val instance = flow.origin(constructorIndex, arguments[0])
        ?: throw PatchException("Unproven NewX offline coroutine instance in $handler")
    if (instance < 0 || instructions[instance].opcode != Opcode.NEW_INSTANCE ||
        instructions[instance].getReference<TypeReference>()?.type != constructor.definingClass
    ) throw PatchException("NewX offline coroutine receiver is not its allocated instance in $handler")
    requireExactlyOne("NewX offline coroutine launch in $handler", (constructorIndex + 1 until ordinaryEvent).filter { index ->
        val reference = instructions[index].getReference<MethodReference>() ?: return@filter false
        if (!instructions[index].opcode.toString().startsWith("INVOKE_STATIC") ||
            !reference.definingClass.startsWith("Lkotlinx/coroutines/")
        ) return@filter false
        val functionParameters = reference.parameterTypes.withIndex().filter { it.value.toString() == "Lkotlin/jvm/functions/Function2;" }
        functionParameters.size == 1 && instructions[index].registersUsed.getOrNull(functionParameters.single().index)?.let {
            flow.origin(index, it) == instance
        } == true
    })
    val capture = requireExactlyOne("NewX offline coroutine handler capture in $constructor", constructorFlow.instructions.indices.filter {
        val instruction = constructorFlow.instructions[it]
        if (instruction.opcode != Opcode.IPUT_OBJECT || !constructorFlow.readsReceiver(it)) return@filter false
        val origin = constructorFlow.origin(it, (instruction as TwoRegisterInstruction).registerA) ?: return@filter false
        val argument = -origin - 1 - constructorFlow.receiver
        origin < 0 && argument in arguments.indices && capturesHost(constructorIndex, arguments[argument])
    })
    val captureField = constructorFlow.instructions[capture].getReference<FieldReference>()!!
    val discriminatorWrite = requireExactlyOne("NewX offline coroutine discriminator in $constructor", constructorFlow.instructions.indices.filter {
        constructorFlow.instructions[it].opcode == Opcode.IPUT_BYTE && constructorFlow.readsReceiver(it)
    })
    val discriminator = constructorFlow.instructions[discriminatorWrite].getReference<FieldReference>()!!
    val discriminatorRegister = (constructorFlow.instructions[discriminatorWrite] as TwoRegisterInstruction).registerA
    val discriminatorOrigin = constructorFlow.origin(discriminatorWrite, discriminatorRegister)
        ?: throw PatchException("Unproven NewX coroutine discriminator in $constructor")
    val selector = if (discriminatorOrigin >= 0) constructorFlow.literal(discriminatorWrite, discriminatorRegister) else {
        val argument = -discriminatorOrigin - 1 - constructorFlow.receiver
        arguments.getOrNull(argument)?.let { flow.literal(constructorIndex, it) }
    } ?: throw PatchException("Nonconstant NewX offline coroutine discriminator in $handler")

    val coroutine = classByType(constructor.definingClass)!!
    val create = requireExactlyOne("NewX offline coroutine create in ${coroutine.type}", coroutine.methods.filter { it.name == "create" })
    val createFlow = DownloadFlow(create, selectedDispatchOnly = true)
    val createPath = createFlow.selectedDispatch(discriminator, selector)
    val clone = requireExactlyOne("NewX offline coroutine selected clone in $create", createPath.filter {
        createFlow.instructions[it].getReference<MethodReference>()?.toString() == constructor.toString()
    })
    val cloneArguments = createFlow.instructions[clone].registersUsed
    val discriminatorParameter = -discriminatorOrigin - 1 - constructorFlow.receiver
    val captureParameter = -constructorFlow.origin(capture, (constructorFlow.instructions[capture] as TwoRegisterInstruction).registerA)!! - 1 - constructorFlow.receiver
    val cloneCapture = createFlow.fieldOrigin(clone, cloneArguments[captureParameter])
    if ((discriminatorOrigin < 0 && createFlow.literal(clone, cloneArguments[discriminatorParameter]) != selector) ||
        cloneCapture == null || cloneCapture.second.toString() != captureField.toString() || !createFlow.readsReceiver(cloneCapture.first)
    ) throw PatchException("NewX offline coroutine clone loses handler/discriminator in $create")
    val cloneInstance = createFlow.origin(clone, cloneArguments[0])
    if (cloneInstance == null || cloneInstance < 0 || createFlow.instructions[cloneInstance].opcode != Opcode.NEW_INSTANCE) {
        throw PatchException("NewX offline coroutine clone is not allocated in $create")
    }
    requireExactlyOne("NewX offline coroutine returned clone in $create", createPath.filter {
        createFlow.instructions[it].opcode == Opcode.RETURN_OBJECT &&
            createFlow.origin(it, (createFlow.instructions[it] as OneRegisterInstruction).registerA) == cloneInstance
    })

    val dispatch = requireExactlyOne("NewX offline coroutine suspend dispatch in ${coroutine.type}", coroutine.methods.filter { it.name == "invokeSuspend" })
    val invoke = requireExactlyOne("NewX offline coroutine invoke in ${coroutine.type}", coroutine.methods.filter { it.name == "invoke" })
    val invokeFlow = DownloadFlow(invoke, selectedDispatchOnly = true)
    val invokePath = invokeFlow.selectedDispatch(discriminator, selector)
    val createCall = requireExactlyOne("NewX offline coroutine invoked clone in $invoke", invokePath.filter {
        invokeFlow.instructions[it].getReference<MethodReference>()?.toString() == create.toString() &&
            invokeFlow.origin(it, invokeFlow.instructions[it].registersUsed[0]) == -invokeFlow.receiver - 1
    })
    if (invokeFlow.instructions.getOrNull(createCall + 1)?.opcode != Opcode.MOVE_RESULT_OBJECT) {
        throw PatchException("Missing NewX coroutine clone result in $invoke")
    }
    requireExactlyOne("NewX offline coroutine resumed clone in $invoke", invokePath.filter {
        invokeFlow.instructions[it].getReference<MethodReference>()?.toString() == dispatch.toString() &&
            invokeFlow.origin(it, invokeFlow.instructions[it].registersUsed[0]) == createCall + 1
    })
    val dispatchFlow = DownloadFlow(dispatch, selectedDispatchOnly = true)
    val helpers = dispatchFlow.selectedDispatch(discriminator, selector).mapNotNull { index ->
        val reference = dispatchFlow.instructions[index].getReference<MethodReference>() ?: return@mapNotNull null
        if (!dispatchFlow.instructions[index].opcode.toString().startsWith("INVOKE_DIRECT") ||
            reference.definingClass != coroutine.type || reference.name == "<init>" ||
            dispatchFlow.origin(index, dispatchFlow.instructions[index].registersUsed[0]) != -dispatchFlow.receiver - 1
        ) return@mapNotNull null
        downloadMethod(reference, classByType)
    }.distinctBy { it.toString() }
    val helperFlows = helpers.map { DownloadFlow(it) } + if (hostField != null) listOf(dispatchFlow) else emptyList()
    val policyPaths = helperFlows.filter { helperFlow ->
        val helper = helperFlow.method
        if (helper.subscriptionCalls().any { it in helperFlow.proofIndexes }) throw PatchException("Unexpected subscription gate in NewX offline coroutine $helper")
        val calls = helperFlow.proofIndexes.filter { index ->
            val instruction = helperFlow.instructions[index]
            val reference = instruction.getReference<MethodReference>() ?: return@filter false
            if (hostField != null && instruction.opcode.toString().startsWith("INVOKE_STATIC") &&
                reference.definingClass == hostType && reference.parameterTypes.firstOrNull()?.toString() == hostType &&
                reference.returnType == "Ljava/lang/Object;" &&
                reference.parameterTypes.lastOrNull()?.toString() == "Lkotlin/coroutines/jvm/internal/ContinuationImpl;"
            ) {
                val host = helperFlow.fieldOrigin(index, instruction.registersUsed[0]) ?: return@filter false
                return@filter host.second.toString() == captureField.toString() && helperFlow.readsReceiver(host.first) &&
                    downloadMethod(reference, classByType).hasTimelineOfflinePolicy(hostType, offlinePremium, classByType)
            }
            if (!instruction.opcode.toString().startsWith("INVOKE_VIRTUAL") || reference.returnType != "Ljava/lang/Enum;" ||
                reference.parameterTypes.map(CharSequence::toString) != listOf("Ljava/lang/String;", "Lkotlin/coroutines/jvm/internal/ContinuationImpl;")
            ) return@filter false
            val policyField = helperFlow.fieldOrigin(index, instruction.registersUsed[0]) ?: return@filter false
            if (policyField.second.definingClass != hostType) return@filter false
            val handlerOrigin = helperFlow.fieldOrigin(policyField.first, (helperFlow.instructions[policyField.first] as TwoRegisterInstruction).registerB)
                ?: return@filter false
            handlerOrigin.second.toString() == captureField.toString() && helperFlow.readsReceiver(handlerOrigin.first) &&
                downloadMethod(reference, classByType).delegatesToOfflinePolicy(offlinePremium, classByType)
        }
        requireAtMostOne("NewX captured offline policy invocation in $helper", calls) != null
    }
    requireExactlyOne("NewX captured offline coroutine reaching premium quota policy for $handler", policyPaths) { it.method.toString() }
}

/** Pure preflight entry: synthetic DEX fixtures supply the same indexed class lookup as production. */
internal fun resolveVideoDownloadSubscriptionResults(
    handler: MutableMethod,
    premium: MethodReference,
    offlinePremium: MethodReference,
    offlineFeature: MethodReference,
    classByType: (String) -> ClassDef?,
): List<Int> {
    val results = handler.subscriptionResults()
    val references = results.map { handler.instructions[it - 1].getReference<MethodReference>()!!.toString() }
    val known = setOf(premium.toString(), offlinePremium.toString(), offlineFeature.toString())
    if (references.any { it !in known }) throw PatchException("Unknown NewX video subscription gate in $handler: $references")
    for (required in listOf(premium, offlineFeature)) {
        requireExactlyOne("NewX video capability $required in $handler", references.filter { it == required.toString() })
    }
    val directOffline = requireAtMostOne("NewX direct offline premium capability in $handler", references.filter { it == offlinePremium.toString() })
    if (directOffline == null) proveDownstreamOfflinePremium(handler, offlinePremium, classByType)
    return results
}

/** Covers each offline event arm, not a release-specific total of subscription calls. */
internal fun resolveTimelineDownloadSubscriptionResults(
    handler: MutableMethod,
    premium: MethodReference,
    offlinePremium: MethodReference,
    offlineFeature: MethodReference,
    classByType: (String) -> ClassDef?,
): List<Int> {
    val results = handler.subscriptionResults()
    val flow = DownloadFlow(handler)
    val instructions = flow.instructions
    val known = setOf(premium.toString(), offlinePremium.toString(), offlineFeature.toString())
    val references = results.associate { it - 1 to instructions[it - 1].getReference<MethodReference>()!!.toString() }
    if (references.values.any { it !in known }) throw PatchException("Unknown NewX timeline subscription gate in $handler: $references")
    requireExactlyOne("NewX timeline all-tier premium gate in $handler", references.filterValues { it == premium.toString() }.keys)
    if (references.values.none { it == offlineFeature.toString() }) throw PatchException("Missing NewX timeline offline feature capability in $handler")

    val hosts = references.keys.map { index ->
        val subscription = flow.fieldOrigin(index, instructions[index].registersUsed[0])
            ?: throw PatchException("Unproven NewX timeline subscription receiver in $handler at $index")
        val host = flow.fieldOrigin(subscription.first, (instructions[subscription.first] as TwoRegisterInstruction).registerB)
            ?: throw PatchException("Unproven NewX timeline host in $handler at $index")
        if (!flow.readsReceiver(host.first) || host.second.type != subscription.second.definingClass) {
            throw PatchException("NewX timeline gate is not on the captured host in $handler at $index")
        }
        host.second
    }.distinctBy { it.toString() }
    val host = requireExactlyOne("NewX timeline captured download host in $handler", hosts)
    val offline = requireExactlyOne("NewX timeline offline event marker in $handler", instructions.indices.filter {
        instructions[it].getReference<StringReference>()?.string == "download_video_to_offline"
    })
    val removal = requireExactlyOne("NewX timeline offline removal marker in $handler", instructions.indices.filter {
        instructions[it].getReference<StringReference>()?.string == "remove_video_from_offline"
    })
    fun isEventTest(index: Int): Boolean = instructions[index].opcode == Opcode.INSTANCE_OF &&
        flow.origin(index, (instructions[index] as TwoRegisterInstruction).registerB) == -flow.receiver - 2
    // newx-resolver-lint: allow instruction-order raw-last because the event dispatcher places each type test before its own scribe arm.
    val firstArm = (0 until offline).lastOrNull(::isEventTest)
        ?: throw PatchException("Missing NewX timeline offline event type test in $handler")
    // newx-resolver-lint: allow instruction-order raw-last because removal's type test terminates the adjacent offline-save event arms.
    val removalArm = (offline + 1 until removal).lastOrNull(::isEventTest)
        ?: throw PatchException("Missing NewX timeline offline removal type test in $handler")
    val arms = (firstArm until removalArm).filter(::isEventTest)
    if (arms.isEmpty()) throw PatchException("No NewX timeline offline-save event arms in $handler")
    val coveredDirectGates = mutableSetOf<Int>()
    arms.forEachIndexed { ordinal, start ->
        val end = arms.getOrNull(ordinal + 1) ?: removalArm
        val direct = requireAtMostOne("NewX timeline offline premium gate in $handler arm $start", references.keys.filter {
            it in start until end && references[it] == offlinePremium.toString()
        })
        if (direct != null) {
            coveredDirectGates.add(direct)
            return@forEachIndexed
        }
        val constructors = (start until end).filter { index ->
            val reference = instructions[index].getReference<MethodReference>() ?: return@filter false
            if (reference.name != "<init>" || classByType(reference.definingClass)?.superclass != "Lkotlin/coroutines/jvm/internal/SuspendLambda;") return@filter false
            instructions[index].registersUsed.drop(1).any { register ->
                flow.fieldOrigin(index, register)?.let { it.second.toString() == host.toString() && flow.readsReceiver(it.first) } == true
            }
        }
        val constructor = requireExactlyOne("NewX timeline offline coroutine in $handler arm $start", constructors)
        proveDownstreamOfflinePremium(handler, offlinePremium, classByType, constructor, host)
    }
    val unclaimed = references.filter { (index, reference) -> reference == offlinePremium.toString() && index !in coveredDirectGates }
    if (unclaimed.isNotEmpty()) throw PatchException("NewX timeline offline gates outside proven save arms in $handler: $unclaimed")
    return results
}

private fun forceBooleanResult(match: Match) {
    // The old insertion left every label on the method's first instruction, so a branch that
    // reaches it directly keeps skipping the hook, exactly as before.
    match.method.insertHook(0, relocateBranchTargets = false) {
        constInt(0, 1)
        returnValue(0)
    }
}

private fun forceMediaClassDownloadable(match: Match) {
    val booleanFields =
        match.method.instructions
            .mapNotNull { it.getReference<FieldReference>() }
            .filter { it.type == "Z" }
            .distinctBy(FieldReference::toString)
    if (booleanFields.size != 1) {
        throw PatchException(
            "Expected one NewX downloadable boolean field in ${match.originalMethod}, found " +
                "${booleanFields.size}: ${booleanFields.joinToString()}",
        )
    }
    val downloadableField = booleanFields.single()

    forceBooleanResult(match)

    val constructors = match.classDef.methods.filter { it.name == "<init>" }
    if (constructors.isEmpty()) {
        throw PatchException("NewX downloadable media class has no constructors: ${match.originalClassDef.type}")
    }

    var patchedConstructorWrites = 0
    for (constructor in constructors) {
        val matchingIputs =
            constructor.instructions
                .filter { instruction ->
                    instruction.opcode == Opcode.IPUT_BOOLEAN &&
                        instruction.getReference<FieldReference>()?.toString() == downloadableField.toString()
                }.reversed()

        for (iput in matchingIputs) {
            val register =
                (iput as? TwoRegisterInstruction)?.registerA
                    ?: throw PatchException(
                        "NewX downloadable field write has an unexpected instruction shape: $iput",
                    )
            // The old insertion left every label on the field write, so a branch that reaches it
            // directly keeps skipping the hook, exactly as before.
            constructor.insertHook(iput.location.index, relocateBranchTargets = false) {
                constInt(register, 1)
            }
            patchedConstructorWrites++
        }
    }
    if (patchedConstructorWrites == 0) {
        throw PatchException(
            "NewX downloadable field is never initialized in a constructor: $downloadableField",
        )
    }
}

private fun requireMatches(
    label: String,
    matches: Collection<Match>,
    expectedCount: Int,
): Collection<Match> {
    if (matches.size == expectedCount) return matches
    throw PatchException(
        "Expected $expectedCount $label matches, found ${matches.size}: " +
            matches.joinToString { it.originalMethod.toString() },
    )
}

private fun requireMatches(
    label: String,
    matches: Collection<Match>,
    expectedCounts: IntRange,
): Collection<Match> {
    if (matches.size in expectedCounts) return matches
    throw PatchException(
        "Expected ${expectedCounts.first}..${expectedCounts.last} $label matches, " +
            "found ${matches.size}: " + matches.joinToString { it.originalMethod.toString() },
    )
}

@Suppress("unused")
val newXDownloadPatch =
    bytecodePatch(
        name = "NewX: Unlock downloads",
        description = "Unlocks media downloads and offline video saving in NewX.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        execute {
            val premium = requireExactlyOne(
                "NewX premium subscription checker",
                SubscriptionsFeaturesHasAnyPremiumFingerprint.scopedMatchAll(),
            )
            val offlinePremium = requireExactlyOne(
                "NewX offline-video premium checker",
                SubscriptionsFeaturesOfflinePremiumFingerprint.scopedMatchAll(),
            )
            val offlineFeature = requireExactlyOne(
                "NewX offline-video feature gate",
                SubscriptionsFeaturesOfflineVideoEnabledFingerprint.scopedMatchAll(),
            )
            val videoDownloadMatches =
                NewXVideoTabDownloadHandlerFingerprint.scopedMatchAllOrNull().orEmpty()
            val videoPlans = if (videoDownloadMatches.isNotEmpty()) {
                requireMatches(
                    "NewX video download handler",
                    videoDownloadMatches,
                    expectedCounts = 1..2,
                ).map { match ->
                    match.method to resolveVideoDownloadSubscriptionResults(
                        match.method,
                        premium.originalMethod,
                        offlinePremium.originalMethod,
                        offlineFeature.originalMethod,
                        { descriptor -> classDefByOrNull(descriptor) },
                    )
                }
            } else emptyList()

            val timelineHandler = requireExactlyOne(
                "NewX timeline download handler",
                NewXDownloadEventHandlerFingerprint.scopedMatchAll(),
            )
            val timelineResults = resolveTimelineDownloadSubscriptionResults(
                timelineHandler.method,
                premium.originalMethod,
                offlinePremium.originalMethod,
                offlineFeature.originalMethod,
                { descriptor -> classDefByOrNull(descriptor) },
            )

            videoPlans.forEach { (method, results) -> method.forceSubscriptionFeatureResults(results) }
            timelineHandler.method.forceSubscriptionFeatureResults(timelineResults)
            listOf(premium, offlinePremium, offlineFeature).forEach(::forceBooleanResult)

            requireMatches(
                "NewX video media downloadability method",
                MediaContentVideoIsDownloadableFingerprint.scopedMatchAll(),
                expectedCount = 1,
            ).forEach(::forceMediaClassDownloadable)
            requireMatches(
                "NewX GIF media downloadability method",
                MediaContentGifIsDownloadableFingerprint.scopedMatchAll(),
                expectedCount = 1,
            ).forEach(::forceMediaClassDownloadable)
            requireMatches(
                "NewX image media downloadability method",
                MediaContentImageIsDownloadableFingerprint.scopedMatchAll(),
                expectedCount = 1,
            ).forEach(::forceMediaClassDownloadable)
        }
    }
