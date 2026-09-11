/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.navigation

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val LIST = "Ljava/util/List;"
private const val OBJECT = "Ljava/lang/Object;"
private const val STRING = "Ljava/lang/String;"
private const val EXTENSION = "$PATCHES_DESCRIPTOR/navigation/NavigationBarPatch;"
private const val INTENT = "Landroid/content/Intent;"
private const val STARTUP_TAB_EXTRA = "MainActivityAccountHelper.STARTUP_TAB"
private const val FRAGMENT_NAME_BRIDGE = "navigationFragmentName"

private object NavigationBarFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf(USER_SESSION_CLASS, "Z"),
    returnType = LIST,
    filters =
        OpcodesFilter.opcodesToFilters(
            Opcode.IF_EQZ,
            Opcode.INVOKE_STATIC,
            Opcode.MOVE_RESULT_OBJECT,
            Opcode.RETURN_OBJECT,
        ),
)

private object NavigationEnumFingerprint : Fingerprint(
    strings = listOf("FEED", "fragment_feed", "SEARCH", "fragment_search"),
    accessFlags =
        listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.CONSTRUCTOR),
)

private object EnumConstructorFingerprint : Fingerprint(name = "<init>")

private object StartupFingerprint : Fingerprint(
    strings = listOf(STARTUP_TAB_EXTRA),
    parameters = listOf(INTENT, USER_SESSION_CLASS, "Z"),
    returnType = "V",
)

private object InitialTabPositionFingerprint : Fingerprint(
    strings = listOf("feed_viewpager_empty_tabs"),
    returnType = "V",
)

context(patchContext: BytecodePatchContext)
private fun installInitialTabPosition(enumType: String) {
    val method = InitialTabPositionFingerprint.matchAll(0..Int.MAX_VALUE)
        .singleOrNull()?.method
        ?: throw PatchException("Expected one navigation pager initializer")
    if (!AccessFlags.STATIC.isSet(method.accessFlags) ||
        method.parameterTypes.map(CharSequence::toString) !=
        listOf(method.definingClass, enumType, STRING, "Z", "Z")
    ) throw PatchException("Unexpected navigation pager initializer signature")

    val instructions = method.instructions
    val positionCall = instructions.withIndex().filter { (_, instruction) ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
        reference?.definingClass == "Landroidx/viewpager2/widget/ViewPager2;" &&
            reference.parameterTypes.map(CharSequence::toString) == listOf("I", "Z") &&
            reference.returnType == "V"
    }.singleOrNull()?.index
        ?: throw PatchException("Expected one initial navigation pager position call")
    val gateIndex = positionCall - 5
    if (gateIndex < 2 || instructions.subList(gateIndex - 2, positionCall + 1).map { it.opcode } !=
        listOf(Opcode.INVOKE_INTERFACE, Opcode.MOVE_RESULT, Opcode.IF_EQZ,
            Opcode.IGET_OBJECT, Opcode.INVOKE_INTERFACE, Opcode.MOVE_RESULT,
            Opcode.IF_LEZ, Opcode.INVOKE_VIRTUAL)
    ) throw PatchException("Unexpected initial navigation pager position flow")

    val flagCall = (instructions[gateIndex - 2] as ReferenceInstruction).reference as? MethodReference
    val indexCall = (instructions[positionCall - 3] as ReferenceInstruction).reference as? MethodReference
    val gate = instructions[gateIndex] as BuilderOffsetInstruction
    val boundsCheck = instructions[positionCall - 1] as BuilderOffsetInstruction
    val positionRegister = instructions[positionCall - 2].registersUsed.single()
    if (flagCall?.definingClass != "Lcom/facebook/mobileconfig/factory/MobileConfigUnsafeContext;" ||
        flagCall.parameterTypes.map(CharSequence::toString) != listOf("J") || flagCall.returnType != "Z" ||
        indexCall?.definingClass != LIST || indexCall.name != "indexOf" ||
        indexCall.parameterTypes.map(CharSequence::toString) != listOf(OBJECT) || indexCall.returnType != "I" ||
        gate.target.location != boundsCheck.target.location ||
        instructions[gateIndex - 1].registersUsed != gate.registersUsed ||
        boundsCheck.registersUsed != listOf(positionRegister) ||
        instructions[positionCall].registersUsed.getOrNull(1) != positionRegister
    ) throw PatchException("Unable to verify native initial tab position lookup")

    // FEED -> FEED skips normal tab changes, so a reordered pager must be positioned during initialization.
    method.replaceInstruction(gateIndex, "nop")
}

private fun registerWidth(type: CharSequence) =
    if (type.toString() == "J" || type.toString() == "D") 2 else 1

private fun sameMethod(reference: MethodReference, method: MutableMethod) =
    reference.definingClass == method.definingClass &&
        reference.name == method.name &&
        reference.parameterTypes.map(CharSequence::toString) ==
        method.parameterTypes.map(CharSequence::toString) &&
        reference.returnType == method.returnType

private fun fragmentParameter(
    initializer: MutableMethod,
    constructor: MutableMethod,
    literal: String,
): Int {
    val literalMatch =
        initializer.instructions.withIndex().filter { (_, instruction) ->
            ((instruction as? ReferenceInstruction)?.reference as? StringReference)
                ?.string == literal
        }.singleOrNull()
            ?: throw PatchException("Expected one $literal navigation enum literal")
    val literalRegister =
        literalMatch.value.registersUsed.singleOrNull()
            ?: throw PatchException("Expected one register for $literal")
    val call =
        initializer.instructions.withIndex().firstOrNull { (index, instruction) ->
            if (index <= literalMatch.index ||
                (instruction.opcode != Opcode.INVOKE_DIRECT &&
                    instruction.opcode != Opcode.INVOKE_DIRECT_RANGE)
            ) {
                return@firstOrNull false
            }
            val reference =
                (instruction as? ReferenceInstruction)?.reference as? MethodReference
            reference != null && sameMethod(reference, constructor)
        } ?: throw PatchException("No enum constructor follows $literal")

    val registers = call.value.registersUsed
    if (registers.size != 1 + constructor.parameterTypes.sumOf(::registerWidth)) {
        throw PatchException("Unexpected navigation enum constructor registers")
    }
    var registerIndex = 1
    val matches = mutableListOf<Int>()
    constructor.parameterTypes.forEachIndexed { parameterIndex, type ->
        if (registers[registerIndex] == literalRegister) matches += parameterIndex
        registerIndex += registerWidth(type)
    }
    return matches.singleOrNull()
        ?: throw PatchException("Unable to identify the $literal constructor parameter")
}

private fun discoverFragmentField(
    initializer: MutableMethod,
    constructor: MutableMethod,
): FieldReference {
    if (initializer.definingClass != constructor.definingClass ||
        constructor.name != "<init>" ||
        AccessFlags.STATIC.isSet(constructor.accessFlags)
    ) {
        throw PatchException("Unexpected navigation enum constructor")
    }
    val parameterIndex =
        listOf("fragment_feed", "fragment_search")
            .map { fragmentParameter(initializer, constructor, it) }
            .distinct()
            .singleOrNull()
            ?: throw PatchException("Navigation fragment literals use different parameters")
    if (constructor.parameterTypes.getOrNull(parameterIndex)?.toString() != STRING) {
        throw PatchException("Navigation fragment parameter is not a String")
    }

    val parameterRegister =
        constructor.p0Register + 1 +
            constructor.parameterTypes.take(parameterIndex).sumOf(::registerWidth)
    return constructor.instructions.mapNotNull { instruction ->
        if (instruction.opcode != Opcode.IPUT_OBJECT) return@mapNotNull null
        val registers = instruction.registersUsed
        if (registers.size != 2) {
            throw PatchException("Expected two navigation fragment field assignment registers")
        }
        val reference =
            (instruction as? ReferenceInstruction)?.reference as? FieldReference
                ?: return@mapNotNull null
        reference.takeIf {
            registers[0] == parameterRegister &&
                registers[1] == constructor.p0Register &&
                it.definingClass == constructor.definingClass &&
                it.type == STRING
        }
    }.singleOrNull()
        ?: throw PatchException("Expected one navigation fragment field assignment")
}

private fun discoverAllCandidates(method: MutableMethod): MethodReference =
    method.instructions.mapNotNull { instruction ->
        if (instruction.opcode != Opcode.INVOKE_STATIC) return@mapNotNull null
        val reference =
            (instruction as? ReferenceInstruction)?.reference as? MethodReference
                ?: return@mapNotNull null
        reference.takeIf {
            it.definingClass == method.definingClass &&
                it.parameterTypes.map(CharSequence::toString) == listOf(USER_SESSION_CLASS) &&
                it.returnType == LIST
        }
    }.singleOrNull()
        ?: throw PatchException("Expected one navigation all-candidates call")

private fun requireLocalRegisters(method: MutableMethod, registers: List<Int>) {
    if (registers.distinct().size != registers.size ||
        registers.any { it !in 0 until method.p0Register || it > 0xf }
    ) {
        throw PatchException("Navigation bridge has no safe local registers: $registers")
    }
}

private fun createFragmentNameBridge(fragmentField: FieldReference): MutableMethod {
    val implementation =
        MethodImplementationBuilder(1).apply {
            addInstruction("check-cast v0, ${fragmentField.definingClass}".toInstruction())
            addInstruction("iget-object v0, v0, $fragmentField".toInstruction())
            addInstruction("return-object v0".toInstruction())
        }.methodImplementation
    return MutableMethod(
        ImmutableMethod(
            EXTENSION,
            FRAGMENT_NAME_BRIDGE,
            listOf(ImmutableMethodParameter(OBJECT, emptySet(), null)),
            STRING,
            AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            implementation,
        ),
    )
}

context(patchContext: BytecodePatchContext)
private fun installFragmentNameBridge(fragmentField: FieldReference) {
    val extensionClass = patchContext.mutableClassDefBy(EXTENSION)
    val bridgeCandidates =
        extensionClass.methods.filter { method ->
            method.name == FRAGMENT_NAME_BRIDGE &&
                method.parameterTypes.map(CharSequence::toString) == listOf(OBJECT) &&
                method.returnType == STRING
        }
    if (bridgeCandidates.size != 1) {
        throw PatchException(
            "Expected one navigation fragment-name bridge, found ${bridgeCandidates.size}",
        )
    }
    val bridgeStub = bridgeCandidates.single()
    if (!AccessFlags.PRIVATE.isSet(bridgeStub.accessFlags) ||
        !AccessFlags.STATIC.isSet(bridgeStub.accessFlags) ||
        !AccessFlags.NATIVE.isSet(bridgeStub.accessFlags)
    ) {
        throw PatchException("Navigation fragment-name bridge has an invalid signature")
    }

    extensionClass.methods.remove(bridgeStub)
    extensionClass.methods.add(createFragmentNameBridge(fragmentField))
}

private fun installTransform(
    method: MutableMethod,
    allCandidates: MethodReference,
) {
    if (AccessFlags.STATIC.isSet(method.accessFlags) ||
        method.parameterTypes.map(CharSequence::toString) != listOf(USER_SESSION_CLASS, "Z") ||
        method.returnType != LIST
    ) {
        throw PatchException("Unexpected navigation tabs method signature")
    }
    val returnInstruction =
        method.instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_OBJECT }
            .singleOrNull()
            ?: throw PatchException("Expected one navigation tabs return")
    val tabsRegister =
        returnInstruction.value.registersUsed.singleOrNull()
            ?: throw PatchException("Expected one navigation tabs return register")
    val candidatesRegister = method.findFreeRegister(returnInstruction.index, tabsRegister)
    requireLocalRegisters(method, listOf(candidatesRegister))
    val sessionRegister = method.p0Register + 1
    if (tabsRegister !in 0..0xf || sessionRegister !in 0..0xf) {
        throw PatchException("Navigation transform requires 4-bit registers")
    }

    method.addInstructionsAtControlFlowLabel(
        returnInstruction.index,
        """
            invoke-static {v$sessionRegister}, $allCandidates
            move-result-object v$candidatesRegister
            invoke-static {v$tabsRegister, v$candidatesRegister}, $EXTENSION->transformNavigationTabs(Ljava/util/List;Ljava/util/List;)Ljava/util/List;
            move-result-object v$tabsRegister
        """.trimIndent(),
    )
}

private fun installStartup(
    method: MutableMethod,
    allCandidates: MethodReference,
) {
    val parameters = method.parameterTypes.map(CharSequence::toString)
    if (parameters != listOf(INTENT, USER_SESSION_CLASS, "Z") || method.returnType != "V") {
        throw PatchException("Unexpected navigation startup method signature")
    }
    val firstParameter =
        method.p0Register + if (AccessFlags.STATIC.isSet(method.accessFlags)) 0 else 1
    val intentRegister = firstParameter
    val sessionRegister = firstParameter + 1
    val coldRegister = firstParameter + 2
    val candidatesRegister =
        method.findFreeRegister(0, listOf(intentRegister, sessionRegister, coldRegister))
    requireLocalRegisters(method, listOf(candidatesRegister))
    if (listOf(intentRegister, sessionRegister, coldRegister).any { it !in 0..0xf }) {
        throw PatchException("Navigation startup requires 4-bit parameter registers")
    }

    val implementation =
        method.implementation ?: throw PatchException("Navigation startup has no implementation")
    val nativeStart =
        method.instructions.firstOrNull()
            ?: throw PatchException("Navigation startup has no first instruction")
    val resumeNative = nativeStart.location.addNewLabel()
    val candidateStart = "invoke-static {v$sessionRegister}, $allCandidates".toInstruction(method)
    val candidateEnd =
        "invoke-static {v$intentRegister, v$coldRegister, v$candidatesRegister}, $EXTENSION->applyStartupTab(Landroid/content/Intent;ZLjava/util/List;)V"
            .toInstruction(method)
    val candidateFailure = "move-exception v$candidatesRegister".toInstruction(method)
    method.addInstructions(
        0,
        listOf(
            "invoke-static {v$intentRegister, v$coldRegister}, $EXTENSION->preflightStartup(Landroid/content/Intent;Z)Z"
                .toInstruction(method),
            "move-result v$candidatesRegister".toInstruction(method),
            BuilderInstruction21t(Opcode.IF_EQZ, candidatesRegister, resumeNative),
            candidateStart,
            "move-result-object v$candidatesRegister".toInstruction(method),
            candidateEnd,
            BuilderInstruction10t(Opcode.GOTO, resumeNative),
            candidateFailure,
            BuilderInstruction10t(Opcode.GOTO, resumeNative),
        ),
    )
    implementation.addCatch(
        candidateStart.location.addNewLabel(),
        candidateEnd.location.addNewLabel(),
        candidateFailure.location.addNewLabel(),
    )
}

@Suppress("unused")
val navigationBarPatch =
    bytecodePatch(
        name = "Customize navigation bar",
        description = "Choose which tabs appear in the bottom navigation bar and reorder them",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)

        execute {
            val navigationMatch =
                NavigationBarFingerprint.matchAll(0..Int.MAX_VALUE).singleOrNull()
                    ?: throw PatchException("Expected one navigation tabs initializer")
            val enumMatch =
                NavigationEnumFingerprint.matchAll(0..Int.MAX_VALUE).singleOrNull()
                    ?: throw PatchException("Expected one navigation enum initializer")
            val constructorMatch =
                EnumConstructorFingerprint.matchAll(enumMatch.classDef, 0..Int.MAX_VALUE)
                    .singleOrNull()
                    ?: throw PatchException("Expected one navigation enum constructor")
            val fragmentField =
                discoverFragmentField(enumMatch.method, constructorMatch.method)
            val allCandidates = discoverAllCandidates(navigationMatch.method)
            val providerDefinitions =
                navigationMatch.classDef.methods.filter { sameMethod(allCandidates, it) }
            val provider = providerDefinitions.singleOrNull()
                ?: throw PatchException("Expected one navigation candidates provider")
            if (!AccessFlags.PUBLIC.isSet(provider.accessFlags) ||
                !AccessFlags.STATIC.isSet(provider.accessFlags)
            ) {
                throw PatchException("Navigation candidates provider is not public static")
            }
            val startupMatch =
                StartupFingerprint.matchAll(0..Int.MAX_VALUE).singleOrNull()
                    ?: throw PatchException("Expected one navigation startup method")

            installFragmentNameBridge(fragmentField)
            installStartup(startupMatch.method, allCandidates)
            installTransform(navigationMatch.method, allCandidates)
            installInitialTabPosition(enumMatch.classDef.type)
            enableSettings("hideNavigationButtons")
        }
    }
