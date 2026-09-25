package app.crimera.patches.newx.misc.dynamiccolor

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.models.resolvedNewXInlineActionModels
import app.crimera.patches.newx.models.newXInlineActionModelResolutionPatch
import app.crimera.patches.newx.models.firstParameterSlot
import app.crimera.patches.newx.models.isInlineActionEntryRenderer
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.EXTENSION_PACKAGE
import app.crimera.patches.newx.utils.requireAtMostOne
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.newx.utils.INTEGER_MOVE_OPCODES
import app.crimera.bytecode.RegisterLimit
import app.crimera.bytecode.Block
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.utils.destinationRegisterOrNull
import app.crimera.patches.newx.utils.resolveIntegerLiteralOnCurrentPath
import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderSwitchElement
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val PALETTE_CONSTRUCTOR_PARAMETERS = "ZJJJJJJJJJJJJJJJJJ"
private const val PALETTE_COLOR_COUNT = 17
private const val ACCENT_TONE_COUNT = 13
private const val COLOR_SCALE_COUNT = 10
private const val EXPECTED_FACTORY_COUNT = 3
private const val REQUIRED_FACTORY_REGISTER_COUNT = 37
private const val ACCENT_SETTINGS_SCRATCH_REGISTER_COUNT = 3
private const val ACCENT_SETTINGS_SNAPSHOT_INSTRUCTION_COUNT = 2
private const val PALETTE_CONSTRUCTOR_REGISTER_COUNT = 36
private const val FUNCTION0_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val DYNAMIC_COLOR_PALETTE_DESCRIPTOR =
    "$EXTENSION_PACKAGE/theme/DynamicColorPalette;"
private const val INLINE_ACTION_TINT_METHOD =
    "$DYNAMIC_COLOR_PALETTE_DESCRIPTOR->inlineActionTint(J)J"
private const val INLINE_ACTION_ACTIVE_TINT_METHOD =
    "$DYNAMIC_COLOR_PALETTE_DESCRIPTOR->inlineActionActiveTint(J)J"
private const val INLINE_LIKE_ANIMATION_METHOD =
    "$DYNAMIC_COLOR_PALETTE_DESCRIPTOR->inlineLikeAnimation(Z)Z"
private const val XDS_CHROME_BACKGROUND_METHOD =
    "$DYNAMIC_COLOR_PALETTE_DESCRIPTOR->xdsChromeBackground(J)J"
private const val PALETTE_IS_ENABLED_METHOD =
    "$DYNAMIC_COLOR_PALETTE_DESCRIPTOR->isEnabled()Z"
private const val PALETTE_IS_AMOLED_BLACK_METHOD =
    "$DYNAMIC_COLOR_PALETTE_DESCRIPTOR->isAmoledBlack()Z"

private val AMOLED_BACKGROUND_COLORS = mapOf(
    7 to 0xFF00000000000000UL.toLong(),
    8 to 0x8000000000000000UL.toLong(),
    9 to 0xFF00000000000000UL.toLong(),
    13 to 0xCC00000000000000UL.toLong(),
    15 to 0xFF00000000000000UL.toLong(),
)

/**
 * The classic Twitter dim surfaces, taken from NewX's own DIM palette. NewX routes both "Dim"
 * and "Lights out" to the LIGHTS_OUT factory, so the blue DIM palette is unreachable and dark
 * mode shows the near-black LIGHTS_OUT surfaces instead. These values restore the old dim look
 * for the shared dark palette when dynamic color is off and AMOLED is off.
 */
private val DIM_BACKGROUND_COLORS = mapOf(
    7 to 0xFF15202B00000000UL.toLong(),
    8 to 0xBF15202B00000000UL.toLong(),
    9 to 0xFF10192200000000UL.toLong(),
    13 to 0xCC15202B00000000UL.toLong(),
    15 to 0xFF15202B00000000UL.toLong(),
)

private enum class PaletteKind(
    val helperMethod: String,
    val isLight: Boolean,
    val themeVariantFieldName: String,
) {
    STANDARD("light", true, "STANDARD"),
    DIM("dark", false, "DIM"),
    LIGHTS_OUT("lightsOut", false, "LIGHTS_OUT"),
}

private data class ResolvedFactory(
    val kind: PaletteKind,
    val method: MutableMethod,
    val paletteAllocation: PaletteAllocation,
)

private data class ExpandedAccentConstructor(
    val method: MutableMethod,
    val scratchRegisterStart: Int,
)

private data class PaletteAllocation(
    val index: Int,
    val branchEndIndex: Int,
)

private data class PaletteConstructor(
    val index: Int,
    val instruction: RegisterRangeInstruction,
)

private data class FactoryAllocation(
    val index: Int,
    val descriptor: String,
    val selector: Int,
)

private data class ResolvedLottieRenderer(
    val index: Int,
    val instruction: Instruction,
    val method: MutableMethod,
)

@Suppress("unused")
val dynamicColorPatch =
    bytecodePatch(
        name = "NewX: Dynamic color",
        description = "Applies the system Material You palette to NewX.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXInlineActionModelResolutionPatch)

        val useAmoledBlack =
            newXSettings {
                category(Categories.APPEARANCE) {
                    group(Groups.DYNAMIC_COLORS) {
                        toggle(
                            id = "newx.theme.dynamic_color",
                            strings = settingStrings("piko_newx_dynamic_color"),
                            order = 100,
                            defaultValue = true,
                            rebootApp = true,
                        )
                        val amoledBlack =
                            toggle(
                                id = "newx.theme.amoled_black",
                                strings = settingStrings("piko_newx_dynamic_color_amoled"),
                                order = 200,
                                defaultValue = true,
                                rebootApp = true,
                            )
                        toggle(
                            id = "newx.theme.dynamic_like",
                            strings = settingStrings("piko_newx_dynamic_color_like"),
                            order = 300,
                            defaultValue = true,
                            rebootApp = true,
                        )
                        amoledBlack
                    }
                }
            }

        execute {
            val providerMatches =
                HorizonThemePaletteProviderFingerprint.scopedMatchAll()
            val provider =
                requireExactlyOne(
                    "NewX Horizon theme palette provider",
                    providerMatches,
                ).method
            val paletteDescriptor = provider.returnType
            if (!paletteDescriptor.startsWith("L")) {
                throw PatchException(
                    "NewX Horizon theme palette provider does not return an object: $provider",
                )
            }

            val constructorReference = resolvePaletteConstructorReference(paletteDescriptor)
            val cacheFieldsByKind = provider.resolvePaletteCacheFields(paletteDescriptor)
            val cacheFieldDescriptors = cacheFieldsByKind.values.map(FieldReference::toString).toSet()
            val factories =
                PaletteKind.values().map { kind ->
                    resolveFactory(
                        kind,
                        cacheFieldsByKind.getValue(kind),
                        cacheFieldDescriptors,
                        paletteDescriptor,
                        constructorReference,
                    )
                }

            if (factories.map { it.method to it.paletteAllocation.index }.distinct().size !=
                EXPECTED_FACTORY_COUNT
            ) {
                throw PatchException(
                    "Expected $EXPECTED_FACTORY_COUNT distinct NewX palette factory branches, found " +
                        factories.joinToString(),
                )
            }

            factories
                .groupBy(ResolvedFactory::method)
                .values
                .forEach { methodFactories ->
                    methodFactories.sortedByDescending { it.paletteAllocation.index }
                        .forEach { factory ->
                            factory.method.injectDynamicPalette(
                                allocation = factory.paletteAllocation,
                                kind = factory.kind,
                                paletteDescriptor = paletteDescriptor,
                                constructorReference = constructorReference,
                            )
                        }
                }

            patchDynamicAccentPalettes()
            patchInlineActionTints()
            patchTabTints(paletteDescriptor)
            patchXdsChromeBackground()
        }
    }

context(context: BytecodePatchContext)
private fun resolvePaletteConstructorReference(paletteDescriptor: String): String {
    val constructors =
        context.mutableClassDefBy(paletteDescriptor)
            .methods
            .filter { method ->
                method.name == "<init>" &&
                    method.returnType == "V" &&
                    method.parameterTypes.joinToString("") == PALETTE_CONSTRUCTOR_PARAMETERS
            }
    requireExactlyOne("NewX Horizon palette constructor", constructors)
    return "$paletteDescriptor-><init>($PALETTE_CONSTRUCTOR_PARAMETERS)V"
}

context(context: BytecodePatchContext)
private fun MutableMethod.resolvePaletteCacheFields(
    paletteDescriptor: String,
): Map<PaletteKind, FieldReference> {
    val mappingArrayFields =
        instructions.mapNotNull { instruction ->
            if (instruction.opcode != Opcode.SGET_OBJECT) return@mapNotNull null
            instruction.getReference<FieldReference>()?.takeIf { field -> field.type == "[I" }
        }.distinctBy(FieldReference::toString)
    val mappingArrayField =
        requireExactlyOne("NewX theme-variant mapping array", mappingArrayFields)
    val selectorByKind = resolveThemeVariantSelectors(mappingArrayField)
    val selectorRegister =
        instructions.singleOrNull { instruction -> instruction.opcode == Opcode.AGET }
            ?.let { instruction -> (instruction as? ThreeRegisterInstruction)?.registerA }
            ?: throw PatchException("Expected one NewX theme-variant selector read: $this")

    val branchStartBySelector = mutableMapOf<Int, Int>()
    instructions.withIndex().forEach { (index, instruction) ->
        if (instruction.opcode != Opcode.IF_EQ && instruction.opcode != Opcode.IF_NE) return@forEach
        val branch = instruction as? BuilderInstruction22t
            ?: throw PatchException("NewX theme-variant branch is not mutable: $instruction")
        val literalRegister = when (selectorRegister) {
            branch.registerA -> branch.registerB
            branch.registerB -> branch.registerA
            else -> return@forEach
        }
        val selector = instructions.resolveLatestLiteral(index, literalRegister) ?: return@forEach
        if (selector !in selectorByKind.values) return@forEach
        val branchStart =
            if (instruction.opcode == Opcode.IF_EQ) branch.target.location.index else index + 1
        if (branchStartBySelector.put(selector, branchStart) != null) {
            throw PatchException("Duplicate NewX theme-variant branch for selector $selector: $this")
        }
    }

    val fieldsByKind =
        selectorByKind.mapValues { (_, selector) ->
            val branchStart = branchStartBySelector[selector]
                ?: throw PatchException("NewX theme-variant branch $selector was not found: $this")
            paletteCacheFieldAt(branchStart, paletteDescriptor)
        }
    val cacheFields = fieldsByKind.values.toList()
    if (cacheFields.distinctBy(FieldReference::toString).size != PaletteKind.values().size) {
        throw PatchException("NewX theme variants do not resolve distinct caches: $fieldsByKind")
    }
    if (cacheFields.map { field -> field.definingClass }.distinct().size != 1) {
        throw PatchException("NewX Horizon palette caches must share one holder: $fieldsByKind")
    }
    return fieldsByKind
}

context(context: BytecodePatchContext)
private fun resolveThemeVariantSelectors(
    mappingArrayField: FieldReference,
): Map<PaletteKind, Int> {
    val initializer =
        context.mutableClassDefBy(mappingArrayField.definingClass).methods.singleOrNull { method ->
            method.name == "<clinit>" && method.parameterTypes.isEmpty() && method.returnType == "V"
        } ?: throw PatchException(
            "NewX theme-variant mapping holder has no class initializer: " +
                mappingArrayField.definingClass,
        )
    val instructions = initializer.instructions.toList()
    val mappingStores = instructions.withIndex().filter { indexed ->
        indexed.value.opcode == Opcode.SPUT_OBJECT &&
            indexed.value.getReference<FieldReference>()?.toString() == mappingArrayField.toString()
    }
    val mappingStore =
        requireExactlyOne("NewX theme-variant mapping-array store", mappingStores)
    val mappingArrayRegister =
        (mappingStore.value as? OneRegisterInstruction)?.registerA
            ?: throw PatchException(
                "NewX theme-variant mapping-array store has no source register: " +
                    mappingStore,
            )
    val themeVariantFieldNames = PaletteKind.values().map(PaletteKind::themeVariantFieldName).toSet()

    return PaletteKind.values().associateWith { kind ->
        val enumReads = instructions.withIndex().filter { indexed ->
            indexed.value.opcode == Opcode.SGET_OBJECT &&
                indexed.value.getReference<FieldReference>()?.name == kind.themeVariantFieldName
        }
        val enumRead =
            requireExactlyOne("NewX ${kind.name} theme-variant enum read", enumReads)
        val enumRegister =
            (enumRead.value as? OneRegisterInstruction)?.registerA
                ?: throw PatchException("NewX ${kind.name} enum read has no destination register: $enumRead")
        val blockEnd = instructions.withIndex()
            .firstOrNull { indexed ->
                indexed.index > enumRead.index &&
                    indexed.value.opcode == Opcode.SGET_OBJECT &&
                    indexed.value.getReference<FieldReference>()?.name in themeVariantFieldNames
            }?.index ?: mappingStore.index
        if (blockEnd <= enumRead.index) {
            throw PatchException("NewX ${kind.name} enum read has no bounded mapping block: $initializer")
        }

        val ordinalCandidates = instructions.withIndex().filter { indexed ->
            indexed.index in (enumRead.index + 1) until blockEnd &&
                indexed.value.opcode == Opcode.INVOKE_VIRTUAL &&
                indexed.value.getReference<MethodReference>()?.let { reference ->
                    reference.definingClass == "Ljava/lang/Enum;" &&
                        reference.name == "ordinal" &&
                        reference.parameterTypes.isEmpty() &&
                        reference.returnType == "I"
                } == true &&
                indexed.value.registersUsed == listOf(enumRegister) &&
                instructions.getOrNull(indexed.index + 1)?.opcode == Opcode.MOVE_RESULT
        }
        val ordinal =
            requireExactlyOne("NewX ${kind.name} enum ordinal read", ordinalCandidates)
        val ordinalResultRegister =
            (instructions[ordinal.index + 1] as? OneRegisterInstruction)?.registerA
                ?: throw PatchException(
                    "NewX ${kind.name} enum ordinal has no result register: $ordinal",
                )

        val arrayStoreCandidates = instructions.withIndex().filter { indexed ->
            indexed.index in (ordinal.index + 2) until blockEnd &&
                indexed.value.opcode == Opcode.APUT &&
                (indexed.value as? ThreeRegisterInstruction)?.let { store ->
                    store.registerB == mappingArrayRegister &&
                        store.registerC == ordinalResultRegister
                } == true
        }
        val arrayStore =
            requireExactlyOne("NewX ${kind.name} selector store", arrayStoreCandidates)
        val valueRegister =
            (arrayStore.value as? ThreeRegisterInstruction)?.registerA
                ?: throw PatchException("NewX ${kind.name} selector store has no value register")
        val selectorLiterals = instructions.withIndex().filter { indexed ->
            val instruction = indexed.value
            val oneRegister = instruction as? OneRegisterInstruction
            val literal = instruction as? NarrowLiteralInstruction
            indexed.index in (ordinal.index + 2) until arrayStore.index &&
                oneRegister != null &&
                literal != null &&
                oneRegister.registerA == valueRegister
        }
        (requireExactlyOne("NewX ${kind.name} selector literal", selectorLiterals).value as NarrowLiteralInstruction)
            .narrowLiteral
    }.also { selectorsByKind ->
        val selectors = selectorsByKind.values
        if (selectors.size != PaletteKind.values().size || selectors.toSet().size != selectors.size) {
            throw PatchException("NewX theme-variant selectors are not distinct: $selectorsByKind")
        }
    }
}

private fun List<com.android.tools.smali.dexlib2.iface.instruction.Instruction>.resolveLatestLiteral(
    beforeIndex: Int,
    register: Int,
): Int? =
    take(beforeIndex).asReversed().firstNotNullOfOrNull { instruction ->
        if (instruction !is OneRegisterInstruction || instruction !is NarrowLiteralInstruction) {
            return@firstNotNullOfOrNull null
        }
        instruction.narrowLiteral.takeIf { instruction.registerA == register }
    }

private fun MutableMethod.paletteCacheFieldAt(
    index: Int,
    paletteDescriptor: String,
): FieldReference {
    val sequence = instructions.drop(index).take(4)
    if (sequence.size != 4 ||
        sequence[0].opcode != Opcode.SGET_OBJECT ||
        sequence[1].opcode != Opcode.INVOKE_VIRTUAL ||
        sequence[2].opcode != Opcode.MOVE_RESULT_OBJECT ||
        sequence[3].opcode != Opcode.CHECK_CAST ||
        sequence[3].getReference<TypeReference>()?.type != paletteDescriptor
    ) {
        throw PatchException("NewX palette cache branch has an unexpected shape at $index: $this")
    }
    return sequence[0].getReference<FieldReference>()
        ?: throw PatchException("NewX palette cache field is missing at $index: $this")
}

context(context: BytecodePatchContext)
private fun resolveFactory(
    kind: PaletteKind,
    cacheField: FieldReference,
    cacheFieldDescriptors: Set<String>,
    paletteDescriptor: String,
    constructorReference: String,
): ResolvedFactory {
    val holder = context.mutableClassDefBy(cacheField.definingClass)
    val initializer =
        holder.methods.singleOrNull { method ->
            method.name == "<clinit>" && method.parameterTypes.isEmpty() && method.returnType == "V"
        } ?: throw PatchException("NewX palette cache holder has no class initializer: $holder")
    val stores =
        initializer.instructions.withIndex().filter { indexed ->
            indexed.value.opcode == Opcode.SPUT_OBJECT &&
                indexed.value.getReference<FieldReference>()?.toString() == cacheField.toString()
        }
    val store = requireExactlyOne("NewX $kind palette cache store", stores)
    val storeIndex = store.index
    val instructions = initializer.instructions.toList()
    val cacheStores = instructions.withIndex().filter { indexed ->
        indexed.value.opcode == Opcode.SPUT_OBJECT &&
            indexed.value.getReference<FieldReference>()?.toString() in cacheFieldDescriptors
    }
    if (cacheStores.size != EXPECTED_FACTORY_COUNT) {
        throw PatchException(
            "Expected $EXPECTED_FACTORY_COUNT NewX palette cache stores, found " +
                "${cacheStores.size}: ${cacheStores.joinToString()}",
        )
    }
    val previousStoreIndex = cacheStores.map { it.index }.filter { it < storeIndex }.maxOrNull() ?: -1
    val storeRegister =
        (store.value as? OneRegisterInstruction)?.registerA
            ?: throw PatchException("NewX $kind palette cache store has no source register: $store")
    val factoryCandidates = instructions.withIndex().mapNotNull { allocation ->
        if (allocation.index !in (previousStoreIndex + 1) until storeIndex ||
            allocation.value.opcode != Opcode.NEW_INSTANCE
        ) {
            return@mapNotNull null
        }
        val factoryDescriptor = allocation.value.getReference<TypeReference>()?.type
            ?: return@mapNotNull null
        val factoryClass = context.classDefByOrNull(factoryDescriptor) ?: return@mapNotNull null
        if (FUNCTION0_DESCRIPTOR !in factoryClass.interfaces) return@mapNotNull null
        val invokes = factoryClass.methods.filter { method ->
            method.name == "invoke" &&
                method.parameterTypes.isEmpty() &&
                method.returnType == "Ljava/lang/Object;"
        }
        if (invokes.size != 1) return@mapNotNull null
        val allocationRegister = (allocation.value as? OneRegisterInstruction)?.registerA
            ?: return@mapNotNull null
        val factoryConstructorReference = "$factoryDescriptor-><init>(I)V"
        val constructors = instructions.withIndex().filter { constructor ->
            constructor.index > allocation.index &&
                constructor.index < storeIndex &&
                constructor.value.getReference<MethodReference>()?.toString() ==
                    factoryConstructorReference &&
                constructor.value.receiverRegister() == allocationRegister
        }
        if (constructors.size != 1) return@mapNotNull null
        val constructor = constructors[0]
        val constructorInstruction = constructor.value as? FiveRegisterInstruction
            ?: return@mapNotNull null
        if (constructorInstruction.registerCount != 2) return@mapNotNull null
        val selectorLiterals = instructions.withIndex().filter { literal ->
            val instruction = literal.value
            val oneRegister = instruction as? OneRegisterInstruction
            val narrowLiteral = instruction as? NarrowLiteralInstruction
            literal.index in (allocation.index + 1) until constructor.index &&
                oneRegister != null &&
                narrowLiteral != null &&
                oneRegister.registerA == constructorInstruction.registerD
        }
        if (selectorLiterals.size != 1) return@mapNotNull null
        if (!initializer.factoryResultFeedsStore(
                constructorIndex = constructor.index,
                storeIndex = storeIndex,
                storeRegister = storeRegister,
                allocationRegister = allocationRegister,
            )
        ) {
            return@mapNotNull null
        }
        FactoryAllocation(
            index = allocation.index,
            descriptor = factoryDescriptor,
            selector = (selectorLiterals[0].value as NarrowLiteralInstruction).narrowLiteral,
        )
    }
    val factoryAllocation =
        requireExactlyOne("NewX $kind Function0 cache allocation", factoryCandidates)
    val factoryDescriptor = factoryAllocation.descriptor
    val factorySelector = factoryAllocation.selector

    val factoryClass = context.mutableClassDefBy(factoryDescriptor)
    val invokes =
        factoryClass.methods.filter { method ->
            method.name == "invoke" &&
                method.parameterTypes.isEmpty() &&
                method.returnType == "Ljava/lang/Object;"
        }
    val invoke = requireExactlyOne("NewX $kind Function0.invoke()", invokes)
    val paletteAllocation = invoke.resolvePaletteAllocationIndex(
        factorySelector = factorySelector,
        paletteDescriptor = paletteDescriptor,
        kind = kind,
        constructorReference = constructorReference,
    )
    val isLight = invoke.resolvePaletteIsLight(paletteAllocation, constructorReference)
    if (isLight != kind.isLight) {
        throw PatchException(
            "NewX ${kind.name} palette branch has unexpected isLight=$isLight: $invoke",
        )
    }
    return ResolvedFactory(kind, invoke, paletteAllocation)
}

private fun Instruction.receiverRegister(): Int? =
    when (this) {
        is FiveRegisterInstruction -> registerC.takeIf { registerCount > 0 }
        is RegisterRangeInstruction -> startRegister.takeIf { registerCount > 0 }
        else -> null
    }

private fun MutableMethod.factoryResultFeedsStore(
    constructorIndex: Int,
    storeIndex: Int,
    storeRegister: Int,
    allocationRegister: Int,
): Boolean {
    val consumers = instructions.withIndex().filter { indexed ->
        indexed.index > constructorIndex &&
            indexed.index < storeIndex &&
            indexed.value.getReference<MethodReference>()?.let { reference ->
                val functionParameter = reference.parameterTypes.indexOf(FUNCTION0_DESCRIPTOR)
                if (functionParameter < 0) return@let false
                val registers = indexed.value.registersUsed
                when (indexed.value.opcode) {
                    Opcode.INVOKE_STATIC,
                    Opcode.INVOKE_STATIC_RANGE,
                    -> {
                        reference.returnType.startsWith("L") &&
                            registers.getOrNull(functionParameter) == allocationRegister &&
                            instructions.getOrNull(indexed.index + 1)?.let { result ->
                                result.opcode == Opcode.MOVE_RESULT_OBJECT &&
                                    (result as? OneRegisterInstruction)?.registerA == storeRegister
                            } == true
                    }
                    Opcode.INVOKE_DIRECT,
                    Opcode.INVOKE_DIRECT_RANGE,
                    -> {
                        // Newer Kotlin lowers LazyKt.b(Function0) to a direct
                        // kotlin.Lazy(Function0) constructor. The Function0 is
                        // the second invoke register and the constructed Lazy
                        // object is stored immediately, so no move-result exists.
                        reference.name == "<init>" &&
                            reference.returnType == "V" &&
                            reference.parameterTypes.map(CharSequence::toString) ==
                                listOf(FUNCTION0_DESCRIPTOR) &&
                            registers.getOrNull(functionParameter + 1) == allocationRegister &&
                            registers.firstOrNull() == storeRegister &&
                            indexed.index + 1 == storeIndex
                    }
                    else -> false
                }
            } == true
    }
    return consumers.size == 1
}

private fun MutableMethod.resolvePaletteAllocationIndex(
    factorySelector: Int,
    paletteDescriptor: String,
    kind: PaletteKind,
    constructorReference: String,
): PaletteAllocation {
    val switchInstructions = instructions.withIndex().filter { indexed ->
        indexed.value.opcode == Opcode.PACKED_SWITCH
    }
    val switchInstruction =
        requireExactlyOne("NewX palette factory packed switch", switchInstructions)
            .value as? BuilderInstruction31t
        ?: throw PatchException("NewX palette factory switch is not mutable: $this")
    val payload = switchInstruction.target.location.instruction as? SwitchPayload
        ?: throw PatchException("NewX palette factory switch payload is missing: $this")
    val switchElements = payload.switchElements.map { element ->
        element as? BuilderSwitchElement
            ?: throw PatchException("NewX palette factory switch case is not mutable: $this")
    }
    val explicitCase = switchElements.singleOrNull { element -> element.key == factorySelector }
    val defaultStart = switchInstruction.location.index + 1
    val caseStart = explicitCase?.target?.location?.index ?: defaultStart
    val caseEnd = switchElements
        .mapNotNull { element -> element.target.location.index.takeIf { index -> index > caseStart } }
        .minOrNull() ?: instructions.size
    if (explicitCase == null &&
        !isProvenDefaultPaletteBranch(
            branchStart = defaultStart,
            branchEnd = caseEnd,
            paletteDescriptor = paletteDescriptor,
            constructorReference = constructorReference,
        )
    ) {
        throw PatchException(
            "NewX ${kind.name} selector $factorySelector has no explicit palette case and " +
                "its default branch is not proven to return the palette: $this",
        )
    }
    val paletteAllocations = instructions.withIndex().filter { indexed ->
        indexed.index in caseStart until caseEnd &&
            indexed.value.opcode == Opcode.NEW_INSTANCE &&
            indexed.value.getReference<TypeReference>()?.type == paletteDescriptor
    }
    if (paletteAllocations.size != 1) {
        val totalAllocations = instructions.count { instruction ->
            instruction.opcode == Opcode.NEW_INSTANCE &&
                instruction.getReference<TypeReference>()?.type == paletteDescriptor
        }
        throw PatchException(
            "Expected one ${kind.name} NewX palette allocation in selector case, found " +
                "${paletteAllocations.size} of $totalAllocations: $this",
        )
    }
    return PaletteAllocation(
        index = paletteAllocations.single().index,
        branchEndIndex = caseEnd,
    )
}

private fun MutableMethod.isProvenDefaultPaletteBranch(
    branchStart: Int,
    branchEnd: Int,
    paletteDescriptor: String,
    constructorReference: String,
): Boolean {
    if (branchStart >= branchEnd || branchEnd > instructions.size) return false
    val allocations = instructions.withIndex().filter { indexed ->
        indexed.index in branchStart until branchEnd &&
            indexed.value.opcode == Opcode.NEW_INSTANCE &&
            indexed.value.getReference<TypeReference>()?.type == paletteDescriptor
    }
    if (allocations.size != 1) return false
    val allocation = allocations.single()
    val allocationRegister =
        (allocation.value as? OneRegisterInstruction)?.registerA ?: return false
    val constructors = instructions.withIndex().filter { indexed ->
        indexed.index > allocation.index &&
            indexed.index < branchEnd &&
            indexed.value.getReference<MethodReference>()?.toString() == constructorReference &&
            indexed.value.receiverRegister() == allocationRegister
    }
    if (constructors.size != 1) return false
    val returns = instructions.withIndex().filter { indexed ->
        indexed.index > constructors.single().index &&
            indexed.index < branchEnd &&
            indexed.value.opcode == Opcode.RETURN_OBJECT &&
            (indexed.value as? OneRegisterInstruction)?.registerA == allocationRegister
    }
    return returns.size == 1
}

private fun MutableMethod.resolvePaletteIsLight(
    allocation: PaletteAllocation,
    constructorReference: String,
): Boolean {
    val constructor = resolvePaletteConstructor(allocation, constructorReference)
    val isLightRegister = constructor.instruction.startRegister + 1
    val isLightLiterals = instructions.withIndex().filter { indexed ->
        val instruction = indexed.value
        val oneRegister = instruction as? OneRegisterInstruction
        val narrowLiteral = instruction as? NarrowLiteralInstruction
        indexed.index in (allocation.index + 1) until constructor.index &&
            oneRegister != null &&
            narrowLiteral != null &&
            oneRegister.registerA == isLightRegister &&
            narrowLiteral.narrowLiteral in 0..1
    }
    requireExactlyOne("NewX palette isLight literal in selected branch", isLightLiterals)
    val isLightLiteral = isLightLiterals.single().value as NarrowLiteralInstruction
    return isLightLiteral.narrowLiteral == 1
}

private fun MutableMethod.resolvePaletteConstructor(
    allocation: PaletteAllocation,
    constructorReference: String,
): PaletteConstructor {
    val constructorCandidates = instructions.withIndex().filter { indexed ->
        indexed.index > allocation.index &&
            indexed.index < allocation.branchEndIndex &&
            indexed.value.getReference<MethodReference>()?.toString() == constructorReference &&
            indexed.value.receiverRegister() ==
                (instructions[allocation.index] as? OneRegisterInstruction)?.registerA
    }
    val constructor =
        requireExactlyOne(
            "NewX palette constructor in selected allocation branch",
            constructorCandidates,
        )
    val range = constructor.value as? RegisterRangeInstruction
        ?: throw PatchException("NewX palette constructor is not an invoke-range: $this")
    val allocationRegister =
        (instructions[allocation.index] as? OneRegisterInstruction)?.registerA
            ?: throw PatchException("NewX palette allocation has no destination register: $this")
    if (allocationRegister != range.startRegister) {
        throw PatchException(
            "NewX palette allocation v$allocationRegister does not match constructor receiver " +
                "v${range.startRegister}: $this",
        )
    }
    if (range.registerCount != PALETTE_CONSTRUCTOR_REGISTER_COUNT) {
        throw PatchException(
            "NewX palette constructor needs $PALETTE_CONSTRUCTOR_REGISTER_COUNT registers, " +
                "found ${range.registerCount}: $this",
        )
    }
    return PaletteConstructor(constructor.index, range)
}

context(context: BytecodePatchContext)
private fun patchDynamicAccentPalettes() {
    val providerMatches =
        NewXDynamicColorPaletteProviderFingerprint.scopedMatchAll()
    val provider =
        requireExactlyOne("NewX dynamic color-scale provider", providerMatches).method
    val paletteInterfaceDescriptor = provider.returnType
    if (!paletteInterfaceDescriptor.startsWith("L")) {
        throw PatchException(
            "NewX dynamic color-scale provider does not return an object: $provider",
        )
    }

    val implementationCasts =
        provider.instructions
            .filter { instruction -> instruction.opcode == Opcode.CHECK_CAST }
            .mapNotNull { instruction -> instruction.getReference<TypeReference>()?.type }
            .filter { descriptor ->
                paletteInterfaceDescriptor in context.mutableClassDefBy(descriptor).interfaces
            }
    if (implementationCasts.size != 3) {
        throw PatchException(
            "Expected three NewX dynamic palette casts, found ${implementationCasts.size}: " +
                implementationCasts.joinToString(),
        )
    }

    val castsByDescriptor = implementationCasts.groupingBy { descriptor -> descriptor }.eachCount()
    val standardDescriptor =
        castsByDescriptor.entries.singleOrNull { entry -> entry.value == 1 }?.key
            ?: throw PatchException(
                "Expected one standard NewX dynamic palette cast: $castsByDescriptor",
            )
    val darkDescriptor =
        castsByDescriptor.entries.singleOrNull { entry -> entry.value == 2 }?.key
            ?: throw PatchException(
                "Expected two shared dark NewX dynamic palette casts: $castsByDescriptor",
            )

    val standardConstructor = resolveNoArgConstructor(standardDescriptor)
    val darkConstructor = resolveNoArgConstructor(darkDescriptor)
    val standardAccentFields =
        resolveAccentRamp(
            standardConstructor.staticColorFields(),
            darkConstructor.staticColorFields(),
        )

    if (standardAccentFields.distinctBy(FieldReference::toString).size != ACCENT_TONE_COUNT) {
        throw PatchException(
            "Expected $ACCENT_TONE_COUNT distinct standard NewX accent fields, found " +
                standardAccentFields.joinToString(),
        )
    }
    if (standardAccentFields.map { field -> field.definingClass }.distinct().size != 1) {
        throw PatchException(
            "NewX accent fields must share one static palette: " +
                standardAccentFields.joinToString(),
        )
    }

    val tonesByField =
        standardAccentFields
            .mapIndexed { tone, field -> field.toString() to tone }
            .toMap()
    val expandedStandard =
        standardConstructor.expandForDynamicAccentTones(standardDescriptor)
    val expandedDark =
        darkConstructor.expandForDynamicAccentTones(darkDescriptor)
    expandedStandard.method.injectDynamicAccentTones(
        tonesByField,
        expandedStandard.scratchRegisterStart,
    )
    expandedDark.method.injectDynamicAccentTones(
        tonesByField,
        expandedDark.scratchRegisterStart,
    )
}

context(context: BytecodePatchContext)
private fun resolveNoArgConstructor(descriptor: String): MutableMethod {
    val constructors =
        context.mutableClassDefBy(descriptor)
            .methods
            .filter { method ->
                method.name == "<init>" &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == "V"
            }
    return requireExactlyOne(
        "NewX no-argument dynamic palette constructor for $descriptor",
        constructors,
    )
}

context(context: BytecodePatchContext)
private fun MutableMethod.expandForDynamicAccentTones(
    ownerDescriptor: String,
): ExpandedAccentConstructor {
    val originalRegisterCount =
        implementation?.registerCount
            ?: throw PatchException("NewX dynamic accent constructor has no implementation: $this")
    val expandedMethod =
        cloneMutable(
            additionalRegisters =
                numberOfParameterRegisters + ACCENT_SETTINGS_SCRATCH_REGISTER_COUNT,
        )
    val owner = context.mutableClassDefBy(ownerDescriptor)
    owner.methods.remove(this)
    owner.methods.add(expandedMethod)
    return ExpandedAccentConstructor(
        method = expandedMethod,
        scratchRegisterStart = originalRegisterCount,
    )
}

private fun MutableMethod.staticColorFields(): List<FieldReference> =
    instructions.mapNotNull { instruction ->
        if (instruction.opcode != Opcode.SGET_WIDE) return@mapNotNull null
        instruction.getReference<FieldReference>()
    }

private fun resolveAccentRamp(
    standardFields: List<FieldReference>,
    darkFields: List<FieldReference>,
): List<FieldReference> {
    val expectedColorCount = ACCENT_TONE_COUNT * COLOR_SCALE_COUNT
    if (standardFields.size != expectedColorCount || darkFields.size != expectedColorCount) {
        throw PatchException(
            "Expected $expectedColorCount NewX color-scale fields per palette, found " +
                "standard=${standardFields.size}, dark=${darkFields.size}",
        )
    }
    val standardScales = standardFields.chunked(ACCENT_TONE_COUNT)
    val darkScales = darkFields.chunked(ACCENT_TONE_COUNT)
    val mismatchedScales =
        standardScales.indices.filter { scale ->
            darkScales[scale].map(FieldReference::toString) !=
                standardScales[scale].asReversed().map(FieldReference::toString)
        }
    if (mismatchedScales.isNotEmpty()) {
        throw PatchException("NewX dark color scales are not reversed at $mismatchedScales")
    }
    // Palette fields are emitted in semantic scale order; the first scale is the accent ramp.
    // newx-resolver-lint: allow raw-first because palette order selects the accent ramp
    return standardScales.first()
}

context(context: BytecodePatchContext)
private fun patchInlineActionTints() {
    val models = resolvedNewXInlineActionModels()
    val inlineActionEntryClass = context.mutableClassDefBy(models.inlineActionEntryDescriptor)
    val actionTypeField = models.inlineActionTypeField
    val enabledField = models.inlineActionEnabledField
    val actionTypeDescriptor = models.postActionTypeDescriptor
    val entryMatches =
        Fingerprint(
            returnType = "V",
            custom = { method, _ ->
                method.isInlineActionEntryRenderer(inlineActionEntryClass.type)
            },
            filters = listOf(
                fieldAccess(opcode = Opcode.IGET_OBJECT, reference = actionTypeField),
                fieldAccess(opcode = Opcode.IGET_BOOLEAN, reference = enabledField),
            ),
        ).scopedMatchAll()
    val entryMethod =
        requireExactlyOne("NewX inline action entry renderer", entryMatches).method
    val tintReference =
        entryMethod.instructions.mapNotNull { instruction ->
            if (instruction.opcode != Opcode.INVOKE_STATIC &&
                instruction.opcode != Opcode.INVOKE_STATIC_RANGE
            ) {
                return@mapNotNull null
            }
            instruction.getReference<MethodReference>()
    }.singleOrNull { reference ->
            reference.parameterTypes.firstOrNull() == actionTypeDescriptor &&
                reference.returnType == "V"
        } ?: throw PatchException("NewX inline action tint renderer call not found: $entryMethod")
    val tintMethod = tintReference.resolveMutableMethod("NewX inline action tint renderer")
    val unfavoriteReads = tintMethod.instructions.withIndex().filter { indexed ->
        indexed.value.opcode == Opcode.SGET_OBJECT &&
            indexed.value.getReference<FieldReference>()?.let { field ->
                field.definingClass == actionTypeDescriptor && field.name == "Unfavorite"
            } == true
    }
    val unfavoriteRead = requireExactlyOne("NewX Unfavorite enum read", unfavoriteReads)
    val likeComposableConstructors =
        tintMethod.instructions.drop(unfavoriteRead.index + 1)
            .mapNotNull { instruction -> instruction.getReference<MethodReference>() }
            .filter { reference ->
                reference.name == "<init>" &&
                    reference.parameterTypes.let { parameters ->
                        when (parameters.size) {
                            5 ->
                                parameters[0] == "Ljava/lang/String;" &&
                                    parameters[1] == "Z" &&
                                    parameters[2] == "Ljava/lang/Long;" &&
                                    parameters[3] == "F" &&
                                    parameters[4].toString()
                                        .startsWith("Landroidx/compose/runtime/")
                            6 ->
                                parameters[0] == "Ljava/lang/String;" &&
                                    parameters[1] == "Z" &&
                                    parameters[2] == "Ljava/lang/Long;" &&
                                    parameters[3] == "F" &&
                                    parameters[4] == "Lkotlin/jvm/functions/Function1;" &&
                                    parameters[5].toString()
                                        .startsWith("Landroidx/compose/runtime/")
                            else -> false
                        }
                    } &&
                    reference.definingClass.hasComposableLambdaInvoke()
            }.distinctBy(MethodReference::toString)
    val likeComposableConstructor =
        requireExactlyOne("NewX like icon composable constructor", likeComposableConstructors)

    // First wide long slot (the tint timestamp); Compose inserts auxiliary params between
    // releases, so resolve the slot instead of hardcoding p2. The renderer is static, so the
    // parameter slot is `p0` plus the slot index.
    if (!AccessFlags.STATIC.isSet(entryMethod.accessFlags)) {
        throw PatchException("NewX inline action entry renderer is unexpectedly instance: $entryMethod")
    }
    val tintSlot = entryMethod.firstParameterSlot("J")
    val tintRegister = entryMethod.p0Register + tintSlot

    entryMethod.insertHook(
        index = 0,
        // The old insertion never moved labels off the first instruction, so a branch that
        // reached the method head kept skipping the tint and has to keep doing so.
        relocateBranchTargets = false,
    ) {
        invokeStatic(methodReference(INLINE_ACTION_TINT_METHOD), tintRegister, tintRegister + 1)
        moveResult(tintRegister, "J")
    }
    val activeLikeField = tintMethod.injectActivatedLikeTint(unfavoriteRead.index)
    patchLikeIconComposable(likeComposableConstructor.definingClass, activeLikeField)
}

private const val TAB_TINT_METHOD = "$DYNAMIC_COLOR_PALETTE_DESCRIPTOR->tabTint(J)J"
private const val TAB_SECONDARY_TINT_METHOD =
    "$DYNAMIC_COLOR_PALETTE_DESCRIPTOR->tabSecondaryTint(J)J"
private const val TAB_RENDERER_SCOPE = "Lcom/x/ui/common/tabs/"
private const val PROFILE_INDICATOR_SCOPE = "Landroidx/compose/foundation/text/"
private const val INDICATOR_COLOR_LABEL = "indicatorColor"
private const val COMPOSE_MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val COMPOSE_RUNTIME_COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val JAVA_LIST_DESCRIPTOR = "Ljava/util/List;"
private const val COMPOSE_FOUNDATION_SCOPE = "Landroidx/compose/foundation/"

/**
 * Wide color reads grouped by owner. Owners are never named: R8 reassigns app classes (the tab
 * provider already moved across namespaces) and Compose renames single-letter helpers between
 * releases. Framework slot-table, density, and color-unpack reads are noise and excluded, as
 * are the resolved Horizon reads. Callers pass the resolved Horizon descriptor.
 */
private val FRAMEWORK_OWNER_PREFIXES = listOf(
    "Landroidx/",
    "Lkotlin/",
    "Lkotlinx/",
    "Ljava/",
    "Ljavax/",
    "Ldalvik/",
)

private fun wideReadsByOwner(
    method: Method,
    horizon: String,
): Map<String, List<IndexedValue<Instruction>>> =
    method.implementation?.instructions?.toList().orEmpty().withIndex()
        .filter { (_, instruction) ->
            instruction.opcode == Opcode.IGET_WIDE &&
                instruction.getReference<FieldReference>()?.definingClass?.let { owner ->
                    owner != horizon && FRAMEWORK_OWNER_PREFIXES.none(owner::startsWith)
                } == true
        }
        .groupBy { (_, instruction) ->
            instruction.getReference<FieldReference>()?.definingClass.orEmpty()
        }

/**
 * Tab slot colors enter through exactly two reads from one non-Horizon owner (primary first,
 * secondary second in every observed target) that flow into a String-consuming tabs-scope
 * callee rendering the labels. Hooking the source tints labels and icons together while the
 * slot's alpha-driven selection emphasis flows through untouched.
 */
private fun isTabSlotColors(method: Method, horizon: String): Boolean {
    val reads = wideReadsByOwner(method, horizon)
    if (reads.size != 1) return false
    val ownerReads = reads.values.first()
    if (ownerReads.size != 2) return false
    return method.implementation?.instructions?.toList().orEmpty().any { instruction ->
        instruction.getReference<MethodReference>()?.let { reference ->
            reference.definingClass.startsWith(TAB_RENDERER_SCOPE) &&
                "Ljava/lang/String;" in reference.parameterTypes &&
                "J" in reference.parameterTypes
        } == true
    } == true
}


private fun hasFoundationBackground(instructions: List<Instruction>): Boolean =
    instructions.any { instruction ->
        instruction.getReference<MethodReference>()?.let { reference ->
            reference.definingClass.startsWith(COMPOSE_FOUNDATION_SCOPE) &&
                reference.parameterTypes.getOrNull(0) == COMPOSE_MODIFIER_DESCRIPTOR &&
                "J" in reference.parameterTypes &&
                reference.returnType == COMPOSE_MODIFIER_DESCRIPTOR
        } == true
    }

private fun isTabIndicatorRenderer(method: Method): Boolean {
    if ("J" !in method.parameterTypes) return false
    val instructions = method.implementation?.instructions?.toList().orEmpty()
    return instructions.any { instruction ->
        instruction.opcode == Opcode.CONST_STRING &&
            instruction.getReference<StringReference>()?.string == INDICATOR_COLOR_LABEL
    } && hasFoundationBackground(instructions)
}

private fun isProfileTabIndicator(method: Method, horizon: String): Boolean {
    val instructions = method.implementation?.instructions?.toList().orEmpty()
    val reads = wideReadsByOwner(method, horizon)
    // The profile indicator stopped reading the tab package's static width in 12.27;
    // the semantic color-to-foundation-background flow remains unchanged.
    if (reads.size != 1) return false
    val ownerReads = reads.values.first()
    return ownerReads.size == 1 && hasFoundationBackground(instructions)
}

private fun tabSlotColorsFingerprint(horizon: String) = Fingerprint(
    definingClass = TAB_RENDERER_SCOPE,
    custom = { method, _ -> isTabSlotColors(method, horizon) },
)

private object NewXTabIndicatorRendererFingerprint : Fingerprint(
    definingClass = TAB_RENDERER_SCOPE,
    custom = { method, _ -> isTabIndicatorRenderer(method) },
)

private fun profileTabIndicatorFingerprint(horizon: String) = Fingerprint(
    definingClass = PROFILE_INDICATOR_SCOPE,
    returnType = "Ljava/lang/Object;",
    custom = { method, classDef ->
        // Do not include nested framework helpers such as text/selection; only the direct
        // foundation/text lambda owns the profile indicator in the supported shapes.
        classDef.type.removePrefix(PROFILE_INDICATOR_SCOPE).contains('/') == false &&
            isProfileTabIndicator(method, horizon)
    },
)

context(context: BytecodePatchContext)
private fun patchTabTints(horizon: String) {
    val slots = tabSlotColorsFingerprint(horizon).scopedMatchAllOrNull().orEmpty()
    val indicatorMatches = NewXTabIndicatorRendererFingerprint.scopedMatchAllOrNull().orEmpty()
    val profileMatches = profileTabIndicatorFingerprint(horizon).scopedMatchAllOrNull().orEmpty()
    val slotMatch = requireAtMostOne("NewX tab slot renderer", slots)
    val indicatorMatch = requireAtMostOne("NewX tab indicator renderer", indicatorMatches)
    val profileMatch = requireAtMostOne("NewX profile tab indicator", profileMatches)
    val containerReference = slotMatch?.method?.resolveTabContainerReference(horizon)
    // Releases without a dedicated profile painter share the container indicator for timeline
    // and profile tabs; that sharing is proven by profile code calling the tabs container.
    val sharingProof =
        if (profileMatch == null && containerReference != null) {
            profileSharesContainerIndicator(containerReference)
        } else {
            emptyList()
        }
    val sharingMatch =
        requireAtMostOne("NewX profile/container indicator sharing proof", sharingProof)
    val sharedIndicator = profileMatch == null && sharingMatch != null
    if (
        slotMatch != null &&
            indicatorMatch != null &&
            (profileMatch != null || sharedIndicator)
    ) {
        slotMatch.method.injectTabSlotTints(horizon)
        indicatorMatch.method.injectTabIndicatorTint()
        profileMatch?.method?.injectProfileTabIndicatorTint(horizon)
        return
    }
    throw PatchException(
        "NewX tab tint shapes are unmapped: slots=${slots.size}, " +
            "indicator=${indicatorMatches.size}, profile=${profileMatches.size} for $horizon: " +
            (slots + indicatorMatches + profileMatches).joinToString(),
    )
}

context(context: BytecodePatchContext)
private fun patchXdsChromeBackground() {
    val backgroundFields = buildList<FieldReference> {
        context.classDefForEach { classDef ->
            for (method in classDef.methods) {
                if (!method.isComposeModifierBackground()) continue
                val instructions = method.implementation?.instructions?.toList() ?: continue
                for ((index, instruction) in instructions.withIndex()) {
                    if (instruction.opcode != Opcode.IGET_WIDE) continue
                    val field = instruction.getReference<FieldReference>() ?: continue
                    if (field.type != "J") continue

                    val colorRegister =
                        (instruction as? TwoRegisterInstruction)?.registerA
                            ?: throw PatchException(
                                "NewX XDS background field read has no destination: $instruction",
                            )
                    if (instructions.hasComposeBackgroundUse(index, colorRegister)) add(field)
                }
            }
        }
    }.distinctBy(FieldReference::toString)

    val schemeResolution = mutableListOf<String>()
    val schemeBackgroundFields = backgroundFields.filter { field ->
        val schemeClass = context.classDefByOrNull(field.definingClass)
        if (schemeClass == null) {
            schemeResolution += "$field: owner missing"
            return@filter false
        }
        val constructors = schemeClass.methods.filter { method ->
            method.name == "<init>" &&
                method.returnType == "V" &&
                method.parameterTypes.firstOrNull()?.toString() == "J" &&
                method.assignsFirstColorParameter(field)
        }
        val constructor =
            requireAtMostOne(
                "NewX XDS first-color constructor for $field",
                constructors,
            ) ?: run {
                schemeResolution += "$field: no first-color constructor"
                return@filter false
            }

        val classInitializers = schemeClass.methods.filter { method ->
            method.name == "<clinit>" &&
                method.parameterTypes.isEmpty() &&
                method.returnType == "V"
        }
        val classInitializer =
            requireAtMostOne(
                "NewX XDS class initializer for ${schemeClass.type}",
                classInitializers,
            ) ?: run {
                schemeResolution += "$field: no static initializer"
                return@filter false
            }

        val darkConstructionCount = classInitializer.findDarkSchemeConstructions(
            schemeClass.type.toString(),
            constructor,
        ).size == 1
        schemeResolution += "$field: singleton shape=$darkConstructionCount"
        darkConstructionCount
    }
    if (schemeBackgroundFields.isEmpty()) {
        throw PatchException(
            "NewX XDS chrome background field has no unique dark scheme candidate: " +
                schemeResolution.joinToString(),
        )
    }
    val backgroundField =
        requireExactlyOne("NewX XDS chrome background field", schemeBackgroundFields)
    val schemeClass = context.mutableClassDefBy(backgroundField.definingClass)
    val constructor =
        requireExactlyOne(
            "NewX XDS scheme constructor for the chrome background",
            schemeClass.methods.filter { method ->
                method.name == "<init>" &&
                    method.returnType == "V" &&
                    method.parameterTypes.firstOrNull()?.toString() == "J" &&
                    method.assignsFirstColorParameter(backgroundField)
            },
        )
    val classInitializer =
        requireExactlyOne(
            "NewX XDS scheme static initializer",
            schemeClass.methods.filter { method ->
                method.name == "<clinit>" &&
                    method.parameterTypes.isEmpty() &&
                    method.returnType == "V"
            },
        )
    val darkSchemeConstructions =
        classInitializer.findDarkSchemeConstructions(schemeClass.type.toString(), constructor)
    val darkConstruction =
        requireExactlyOne(
            "NewX XDS dark scheme construction for the chrome background",
            darkSchemeConstructions,
        )

    classInitializer.insertHook(
        index = darkConstruction.index,
        // The old insertion left every incoming label on the dark scheme constructor, so the
        // hook only replaces the color for the paths that already reached it.
        relocateBranchTargets = false,
    ) {
        invokeStatic(
            methodReference(XDS_CHROME_BACKGROUND_METHOD),
            darkConstruction.colorRegister,
            darkConstruction.colorRegister + 1,
        )
        moveResult(darkConstruction.colorRegister, "J")
    }
}

private data class XdsDarkConstruction(
    val index: Int,
    val colorRegister: Int,
)

private fun Method.findDarkSchemeConstructions(
    schemeDescriptor: String,
    constructor: Method,
): List<XdsDarkConstruction> {
    val instructions = implementation?.instructions?.toList() ?: return emptyList()
    val constructorParameters = constructor.parameterTypes.map(CharSequence::toString)
    val firstMaskIndex = constructorParameters.indexOfFirst { parameter -> parameter == "I" }
    if (firstMaskIndex < 0 || !constructor.hasFirstColorDefaultMaskBit()) return emptyList()

    return instructions.withIndex().mapNotNull { indexed ->
        val instruction = indexed.value
        val reference = instruction.getReference<MethodReference>() ?: return@mapNotNull null
        if (reference.definingClass != schemeDescriptor ||
            reference.name != "<init>" ||
            reference.returnType != "V" ||
            reference.parameterTypes.map(CharSequence::toString) != constructorParameters ||
            constructorParameters.firstOrNull() != "J"
        ) {
            return@mapNotNull null
        }

        val registers = instruction as? RegisterRangeInstruction ?: return@mapNotNull null
        val expectedRegisterCount = 1 + constructorParameters.sumOf(String::registerWidth)
        if (registers.registerCount != expectedRegisterCount) return@mapNotNull null

        val maskRegister = registers.startRegister + 1 +
            constructorParameters.take(firstMaskIndex).sumOf(String::registerWidth)
        val maskValue = instructions.resolveIntegerLiteralOnCurrentPath(indexed.index, maskRegister)
            ?: return@mapNotNull null
        if ((maskValue and 1) != 0) return@mapNotNull null

        val nextInstruction = instructions.getOrNull(indexed.index + 1) ?: return@mapNotNull null
        val singletonField = nextInstruction.getReference<FieldReference>() ?: return@mapNotNull null
        if (nextInstruction.opcode != Opcode.SPUT_OBJECT ||
            singletonField.type != schemeDescriptor ||
            (nextInstruction as? OneRegisterInstruction)?.registerA != registers.startRegister
        ) {
            return@mapNotNull null
        }

        val colorRegister = registers.startRegister + 1
        val latestColorWrite = instructions.withIndex()
            .take(indexed.index)
            .lastOrNull { (_, prior) -> prior.writesWideRegisterPair(colorRegister) }
            ?.value
        if (latestColorWrite?.opcode != Opcode.SGET_WIDE ||
            (latestColorWrite as? OneRegisterInstruction)?.registerA != colorRegister
        ) {
            return@mapNotNull null
        }
        XdsDarkConstruction(indexed.index, colorRegister)
    }
}

private fun Method.hasFirstColorDefaultMaskBit(): Boolean {
    val implementation = implementation ?: return false
    val instructions = implementation.instructions.toList()
    val parameters = parameterTypes.map(CharSequence::toString)
    val firstMaskIndex = parameters.indexOfFirst { parameter -> parameter == "I" }
    if (firstMaskIndex < 0) return false
    val parameterStart = implementation.registerCount - parameters.sumOf(String::registerWidth)
    val firstMaskRegister = parameterStart +
        parameters.take(firstMaskIndex).sumOf(String::registerWidth)

    return instructions.withIndex().any { (index, instruction) ->
        if (instruction.opcode != Opcode.AND_INT_LIT8 &&
            instruction.opcode != Opcode.AND_INT_LIT16
        ) {
            return@any false
        }
        val and = instruction as? TwoRegisterInstruction ?: return@any false
        val literal = instruction as? NarrowLiteralInstruction ?: return@any false
        if (literal.narrowLiteral != 1) return@any false
        val branch = instructions.getOrNull(index + 1) ?: return@any false
        if (branch.opcode != Opcode.IF_EQZ ||
            (branch as? OneRegisterInstruction)?.registerA != and.registerA
        ) {
            return@any false
        }
        instructions.integerRegisterResolvesToParameter(index, and.registerB, firstMaskRegister)
    }
}

private fun List<Instruction>.integerRegisterResolvesToParameter(
    instructionIndex: Int,
    register: Int,
    parameterRegister: Int,
): Boolean {
    var sourceRegister = register
    for (index in instructionIndex - 1 downTo 0) {
        val instruction = this[index]
        if (instruction.opcode in INTEGER_MOVE_OPCODES) {
            val move = instruction as? TwoRegisterInstruction ?: return false
            if (move.registerA != sourceRegister) continue
            sourceRegister = move.registerB
            if (sourceRegister == parameterRegister) return true
            continue
        }
        if (instruction.destinationRegisterOrNull() == sourceRegister) return false
    }
    return sourceRegister == parameterRegister
}

private fun Method.isComposeModifierBackground(): Boolean {
    if (!AccessFlags.STATIC.isSet(accessFlags) || returnType != MODIFIER_DESCRIPTOR) return false
    val parameters = parameterTypes.map(CharSequence::toString)
    return MODIFIER_DESCRIPTOR in parameters && COMPOSER_DESCRIPTOR in parameters
}

private fun List<Instruction>.hasComposeBackgroundUse(
    fieldReadIndex: Int,
    colorRegister: Int,
): Boolean = withIndex().any { (index, instruction) ->
    index > fieldReadIndex && instruction.isComposeBackgroundInvoke(colorRegister)
}

private fun Instruction.isComposeBackgroundInvoke(colorRegister: Int): Boolean {
    val reference = getReference<MethodReference>() ?: return false
    val parameters = reference.parameterTypes.map(CharSequence::toString)
    if (reference.returnType != MODIFIER_DESCRIPTOR || MODIFIER_DESCRIPTOR !in parameters) {
        return false
    }
    val registers = invokeArgumentRegisters() ?: return false
    var registerIndex = 0
    for (parameter in parameters) {
        if (parameter == "J" && registers.getOrNull(registerIndex) == colorRegister) return true
        registerIndex += parameter.registerWidth()
    }
    return false
}

private fun Instruction.invokeArgumentRegisters(): List<Int>? = when (this) {
    is RegisterRangeInstruction ->
        (startRegister until startRegister + registerCount).toList()
    is FiveRegisterInstruction ->
        listOf(registerC, registerD, registerE, registerF, registerG).take(registerCount)
    else -> null
}

private fun Method.assignsFirstColorParameter(field: FieldReference): Boolean {
    if (AccessFlags.STATIC.isSet(accessFlags)) return false
    val implementation = implementation ?: return false
    val instructions = implementation.instructions.toList()
    val fieldWrites = instructions.withIndex().filter { (_, instruction) ->
        instruction.opcode == Opcode.IPUT_WIDE &&
            instruction.getReference<FieldReference>()?.toString() == field.toString()
    }
    if (fieldWrites.size != 1) return false

    val (index, instruction) = fieldWrites.single()
    val storedColorRegister = (instruction as? TwoRegisterInstruction)?.registerA ?: return false
    val firstParameterRegister =
        implementation.registerCount - parameterTypes.sumOf { it.toString().registerWidth() }
    if (storedColorRegister == firstParameterRegister) return true

    val latestStoredColorWrite = instructions.take(index)
        .lastOrNull { previous -> previous.writesWideRegisterPair(storedColorRegister) }
        ?: return false
    val move = latestStoredColorWrite as? TwoRegisterInstruction ?: return false
    return latestStoredColorWrite.opcode.setsWideRegister() &&
        move.registerA == storedColorRegister &&
        move.registerB == firstParameterRegister
}

private fun Instruction.writesWideRegisterPair(register: Int): Boolean {
    if (!opcode.setsRegister()) return false
    val destination = when (this) {
        is OneRegisterInstruction -> registerA
        is TwoRegisterInstruction -> registerA
        is ThreeRegisterInstruction -> registerA
        else -> return false
    }
    val destinationRegisters =
        if (opcode.setsWideRegister()) listOf(destination, destination + 1) else listOf(destination)
    return destinationRegisters.any { writtenRegister -> writtenRegister in register..register + 1 }
}

private fun String.registerWidth(): Int = if (this == "J" || this == "D") 2 else 1

private const val MODIFIER_DESCRIPTOR = "Landroidx/compose/ui/Modifier;"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"

private fun MethodReference.isTabContainerRenderer(): Boolean {
    val parameters = parameterTypes.map(CharSequence::toString)
    return definingClass.startsWith(TAB_RENDERER_SCOPE) &&
        returnType == "V" &&
        COMPOSE_MODIFIER_DESCRIPTOR in parameters &&
        COMPOSE_RUNTIME_COMPOSER_DESCRIPTOR in parameters &&
        "J" in parameters &&
        (
            parameters.firstOrNull() == JAVA_LIST_DESCRIPTOR ||
                (
                    parameters.size == 7 &&
                        parameters.firstOrNull()?.startsWith(TAB_RENDERER_SCOPE) == true &&
                        parameters.count { parameter -> parameter == "Ljava/lang/String;" } == 1 &&
                        parameters.count { parameter -> parameter == "J" } == 1 &&
                        parameters.count { parameter -> parameter == FUNCTION0_DESCRIPTOR } == 1 &&
                        parameters.count { parameter -> parameter == "I" } == 1
                    )
            )
}

private fun Instruction.firstStaticArgumentRegister(): Int? =
    if (opcode == Opcode.INVOKE_STATIC || opcode == Opcode.INVOKE_STATIC_RANGE) {
        registersUsed.firstOrNull()
    } else {
        null
    }

private fun MutableMethod.resolveTabContainerReference(horizon: String): MethodReference {
    val calls = instructions.withIndex().filter { indexed ->
        val instruction = indexed.value
        (instruction.opcode == Opcode.INVOKE_STATIC ||
            instruction.opcode == Opcode.INVOKE_STATIC_RANGE) &&
            instruction.getReference<MethodReference>()?.let { reference ->
                reference.isTabContainerRenderer() &&
                    hasTabContainerArgumentFlow(indexed.index, reference, horizon)
            } == true
    }
    val call = requireExactlyOne("NewX tabs container call in slot renderer", calls)
    return call.value.getReference<MethodReference>()
        ?: throw PatchException("NewX tabs container call reference is missing: $this")
}

private val OBJECT_MOVE_OPCODES =
    setOf(Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16)

private fun Instruction.objectDestinationRegister(): Int? =
    when {
        opcode in OBJECT_MOVE_OPCODES -> (this as? TwoRegisterInstruction)?.registerA
        opcode == Opcode.IGET_OBJECT -> (this as? TwoRegisterInstruction)?.registerA
        opcode == Opcode.AGET_OBJECT -> (this as? ThreeRegisterInstruction)?.registerA
        opcode == Opcode.NEW_INSTANCE ||
            opcode == Opcode.MOVE_RESULT_OBJECT ||
            opcode == Opcode.SGET_OBJECT ||
            opcode == Opcode.CHECK_CAST ||
            opcode == Opcode.MOVE_EXCEPTION ||
            opcode == Opcode.CONST_STRING ||
            opcode == Opcode.CONST_STRING_JUMBO ||
            opcode == Opcode.CONST_CLASS -> (this as? OneRegisterInstruction)?.registerA
        else -> null
    }

private val TAB_WIDE_MOVE_OPCODES =
    setOf(Opcode.MOVE_WIDE, Opcode.MOVE_WIDE_FROM16, Opcode.MOVE_WIDE_16)

private fun Instruction.wideDestinationRegister(): Int? =
    when {
        opcode in TAB_WIDE_MOVE_OPCODES ||
            opcode == Opcode.IGET_WIDE ->
            (this as? TwoRegisterInstruction)?.registerA
        opcode == Opcode.SGET_WIDE ||
            opcode == Opcode.MOVE_RESULT_WIDE ->
            (this as? OneRegisterInstruction)?.registerA
        else -> null
    }

private fun Instruction.wideSourceRegister(): Int? =
    if (opcode in TAB_WIDE_MOVE_OPCODES) {
        (this as? TwoRegisterInstruction)?.registerB
    } else {
        null
    }

private fun List<Instruction>.invokeArgumentRegister(
    callIndex: Int,
    reference: MethodReference,
    parameterIndex: Int,
): Int? {
    val wordOffset =
        reference.parameterTypes
            .take(parameterIndex)
            .sumOf { parameter -> if (parameter == "J" || parameter == "D") 2 else 1 }
    return getOrNull(callIndex)?.registersUsed?.getOrNull(wordOffset)
}

private fun List<Instruction>.wideArgumentFlowsFromPalette(
    callIndex: Int,
    argumentRegister: Int,
    horizon: String,
): Boolean {
    val pending = mutableListOf(callIndex to argumentRegister)
    val visited = mutableSetOf<Pair<Int, Int>>()
    while (pending.isNotEmpty()) {
        val (searchEnd, register) = pending.removeAt(pending.lastIndex)
        if (!visited.add(searchEnd to register)) continue
        val definitionIndex =
            (searchEnd - 1 downTo 0).firstOrNull { index ->
                this[index].wideDestinationRegister() == register
            } ?: continue
        val definition = this[definitionIndex]
        when (definition.opcode) {
            in TAB_WIDE_MOVE_OPCODES -> {
                val sourceRegister = definition.wideSourceRegister() ?: continue
                pending += definitionIndex to sourceRegister
            }
            Opcode.IGET_WIDE,
            Opcode.SGET_WIDE,
            -> {
                val field = definition.getReference<FieldReference>() ?: continue
                if (
                    field.type == "J" &&
                        field.definingClass != horizon &&
                        FRAMEWORK_OWNER_PREFIXES.none(field.definingClass::startsWith)
                ) {
                    return true
                }
            }
            else -> continue
        }
    }
    return false
}

private fun List<Instruction>.objectArgumentFlowsFromDescriptor(
    callIndex: Int,
    argumentRegister: Int,
    descriptor: String,
    parameterRegister: Int? = null,
): Boolean {
    val pending = mutableListOf(callIndex to argumentRegister)
    val visited = mutableSetOf<Pair<Int, Int>>()
    while (pending.isNotEmpty()) {
        val (searchEnd, register) = pending.removeAt(pending.lastIndex)
        if (!visited.add(searchEnd to register)) continue
            val definitionIndex =
            (searchEnd - 1 downTo 0).firstOrNull { index ->
                this[index].objectDestinationRegister() == register
            } ?: return parameterRegister == register
        val definition = this[definitionIndex]
        if (definition.opcode in OBJECT_MOVE_OPCODES) {
            val move = definition as? TwoRegisterInstruction ?: continue
            pending += definitionIndex to move.registerB
            continue
        }
        val producedDescriptor =
            when (definition.opcode) {
                Opcode.NEW_INSTANCE,
                Opcode.CHECK_CAST,
                -> definition.getReference<TypeReference>()?.type
                Opcode.SGET_OBJECT,
                Opcode.IGET_OBJECT,
                -> definition.getReference<FieldReference>()?.type
                Opcode.MOVE_RESULT_OBJECT ->
                    this.getOrNull(definitionIndex - 1)
                        ?.getReference<MethodReference>()
                        ?.returnType
                else -> null
            }
        if (producedDescriptor == descriptor) return true
    }
    return false
}

private fun MutableMethod.hasTabContainerArgumentFlow(
    callIndex: Int,
    reference: MethodReference,
    horizon: String,
): Boolean {
    val parameters = reference.parameterTypes.map(CharSequence::toString)
    val firstArgumentRegister =
        instructions.invokeArgumentRegister(callIndex, reference, 0) ?: return false
    if (parameters.firstOrNull() == JAVA_LIST_DESCRIPTOR) {
        return instructions.objectArgumentFlowsFromList(callIndex, firstArgumentRegister) ||
            (
                parameterTypes.firstOrNull()?.toString()?.isListDescriptor() == true &&
                    instructions.objectArgumentFlowsFromDescriptor(
                        callIndex,
                        firstArgumentRegister,
                        parameterTypes.first().toString(),
                        implementation?.registerCount?.minus(numberOfParameterRegisters),
                    )
                )
    }
    val wideParameterIndex = parameters.indexOfFirst { parameter -> parameter == "J" }
    val wideArgumentRegister =
        instructions.invokeArgumentRegister(callIndex, reference, wideParameterIndex)
            ?: return false
    return instructions.objectArgumentFlowsFromDescriptor(
        callIndex,
        firstArgumentRegister,
        parameters.firstOrNull() ?: return false,
    ) && instructions.wideArgumentFlowsFromPalette(callIndex, wideArgumentRegister, horizon)
}

private fun String.isListDescriptor(): Boolean =
    this == JAVA_LIST_DESCRIPTOR ||
        this == "Ljava/util/Collection;" ||
        this == "Ljava/util/ArrayList;" ||
        (startsWith("Ljava/util/") &&
            (endsWith("List;") || endsWith("Collection;")))

private fun List<Instruction>.listSourceDescriptor(
    index: Int,
): String? {
    val instruction = this[index]
    return when (instruction.opcode) {
        Opcode.NEW_INSTANCE,
        Opcode.CHECK_CAST,
        -> instruction.getReference<TypeReference>()?.type
        Opcode.SGET_OBJECT,
        Opcode.IGET_OBJECT,
        -> instruction.getReference<FieldReference>()?.type
        Opcode.MOVE_RESULT_OBJECT ->
            getOrNull(index - 1)?.getReference<MethodReference>()?.returnType
        else -> null
    }
}

private fun List<Instruction>.objectArgumentFlowsFromList(
    callIndex: Int,
    argumentRegister: Int,
): Boolean {
    val pending = mutableListOf(callIndex to argumentRegister)
    val visited = mutableSetOf<Pair<Int, Int>>()
    while (pending.isNotEmpty()) {
        val (searchEnd, register) = pending.removeAt(pending.lastIndex)
        if (!visited.add(searchEnd to register)) continue
        val definitionIndex = (searchEnd - 1 downTo 0).firstOrNull { index ->
            this[index].objectDestinationRegister() == register
        } ?: continue
        val definition = this[definitionIndex]
        if (definition.opcode in OBJECT_MOVE_OPCODES) {
            val move = definition as? TwoRegisterInstruction ?: continue
            pending += definitionIndex to move.registerB
            continue
        }
        if (listSourceDescriptor(definitionIndex)?.isListDescriptor() == true) return true
    }
    return false
}

context(context: BytecodePatchContext)
private fun profileSharesContainerIndicator(containerReference: MethodReference): List<Match> =
    Fingerprint(
        definingClass = "Lcom/x/profile/",
        custom = { method, _ ->
            val instructions = method.implementation?.instructions?.toList().orEmpty()
            val calls = instructions.withIndex().filter { indexed ->
                val instruction = indexed.value
                (instruction.opcode == Opcode.INVOKE_STATIC ||
                    instruction.opcode == Opcode.INVOKE_STATIC_RANGE) &&
                    instruction.getReference<MethodReference>()?.toString() ==
                        containerReference.toString()
            }
            if (calls.size != 1) {
                false
            } else {
                val call = calls[0]
                call.value.firstStaticArgumentRegister()?.let { argumentRegister ->
                    instructions.objectArgumentFlowsFromList(call.index, argumentRegister)
                } == true
            }
        },
    ).scopedMatchAllOrNull().orEmpty()
private fun MutableMethod.injectTabSlotTints(horizon: String) {
    val reads =
        requireExactlyOne(
            "NewX tab slot color owner",
            wideReadsByOwner(this, horizon).values,
        )
    if (reads.size != 2) {
        throw PatchException("NewX tab slot colors have an unexpected shape: $this")
    }
    // Source order is primary-then-secondary in every observed target; the tint methods keep
    // incoming alpha, so the slot's selection emphasis survives regardless.
    val ordered = reads.sortedBy { (index, _) -> index }
    val methods = listOf(TAB_TINT_METHOD, TAB_SECONDARY_TINT_METHOD)
    ordered.zip(methods).sortedByDescending { (read, _) -> read.index }.forEach {
        (read, tintMethod) ->
        val colorRegister =
            (read.value as? OneRegisterInstruction)?.registerA
                ?: throw PatchException("NewX tab slot color has no register: $this")
        insertHook(
            index = read.index + 1,
            // The old insertion left labels on the color read, so only the paths that read the
            // slot color are tinted, exactly as before.
            relocateBranchTargets = false,
        ) {
            invokeStatic(methodReference(tintMethod), colorRegister, colorRegister + 1)
            moveResult(colorRegister, "J")
        }
    }
}

private fun MutableMethod.injectTabIndicatorTint() {
    if (parameterTypes.count { it == "J" } != 1) {
        throw PatchException("NewX tab indicator color parameter is ambiguous: $this")
    }
    val colorParam = parameterTypes.indexOf("J")
    var colorRegister = 0
    parameterTypes.take(colorParam).forEach { parameter ->
        colorRegister += if (parameter == "J" || parameter == "D") 2 else 1
    }
    val tintRegister = p0Register + colorRegister
    // `addInstructionsAtControlFlowLabel` moved every incoming label onto the guard so that the
    // parameter is tinted once, before its first read, on all paths reaching the method head.
    insertHook(
        index = 0,
        relocateBranchTargets = true,
    ) {
        invokeStatic(methodReference(TAB_TINT_METHOD), tintRegister, tintRegister + 1)
        moveResult(tintRegister, "J")
    }
}

private fun MutableMethod.injectProfileTabIndicatorTint(horizon: String) {
    val reads = wideReadsByOwner(this, horizon)
    val brandReads = reads.values.flatten()
    val brandSites =
        brandReads.filter { (index, _) ->
            instructions.drop(index + 1).take(4).any { instruction ->
                instruction.getReference<MethodReference>()?.let { reference ->
                    reference.definingClass.startsWith(COMPOSE_FOUNDATION_SCOPE) &&
                        reference.parameterTypes.getOrNull(0) == COMPOSE_MODIFIER_DESCRIPTOR &&
                        "J" in reference.parameterTypes &&
                        reference.returnType == COMPOSE_MODIFIER_DESCRIPTOR
                } == true
            }
        }
    val brandSite = requireExactlyOne("NewX profile tab indicator tint site", brandSites)
    val colorRegister =
        (brandSite.value as? OneRegisterInstruction)?.registerA
            ?: throw PatchException("NewX profile tab indicator has no color register: $this")
    insertHook(
        index = brandSite.index + 1,
        // The old insertion left labels on the brand color read: a branch that reached the read
        // directly was not tinted before and is not tinted now.
        relocateBranchTargets = false,
    ) {
        invokeStatic(methodReference(TAB_TINT_METHOD), colorRegister, colorRegister + 1)
        moveResult(colorRegister, "J")
    }
}

context(context: BytecodePatchContext)
private fun String.hasComposableLambdaInvoke(): Boolean =
    context.mutableClassDefBy(this).methods.count { method ->
        method.name == "invoke" &&
            method.parameterTypes == listOf("Ljava/lang/Object;", "Ljava/lang/Object;") &&
            method.returnType == "Ljava/lang/Object;"
    } == 1

context(context: BytecodePatchContext)
private fun MethodReference.resolveMutableMethod(label: String): MutableMethod =
    context.mutableClassDefBy(definingClass)
        .methods.singleOrNull { method ->
            method.name == name &&
                method.parameterTypes == parameterTypes &&
                method.returnType == returnType
        } ?: throw PatchException("$label not found: $this")

private val WIDE_MOVE_OPCODES =
    setOf(Opcode.MOVE_WIDE, Opcode.MOVE_WIDE_FROM16, Opcode.MOVE_WIDE_16)

private val BOOLEAN_NORMALIZATION_OPCODES =
    setOf(
        Opcode.MOVE,
        Opcode.MOVE_FROM16,
        Opcode.MOVE_16,
        Opcode.CONST_4,
        Opcode.CONST_16,
        Opcode.CONST,
    )

private fun Instruction.wideArgumentStarts(reference: MethodReference): List<Int> {
    val registers = registersUsed
    var registerIndex =
        if (opcode == Opcode.INVOKE_STATIC || opcode == Opcode.INVOKE_STATIC_RANGE) 0 else 1
    return reference.parameterTypes.mapNotNull { parameter ->
        val startRegister = registers.getOrNull(registerIndex)
        val type = parameter.toString()
        registerIndex += if (type == "J" || type == "D") 2 else 1
        startRegister.takeIf { type == "J" }
    }
}

private fun Instruction.isComposeRendererCall(): Boolean {
    val reference = getReference<MethodReference>() ?: return false
    val parameters = reference.parameterTypes.map(CharSequence::toString)
    return reference.returnType == "V" &&
        reference.definingClass.startsWith("Lcom/") &&
        "Ljava/lang/String;" in parameters &&
        COMPOSE_MODIFIER_DESCRIPTOR in parameters &&
        "J" in parameters
}

private fun MutableMethod.wideColorFeedsComposeRenderer(
    loadIndex: Int,
    loadRegister: Int,
): Boolean {
    val aliases = mutableSetOf(loadRegister)
    val methodInstructions = instructions.toList()
    for (indexed in methodInstructions.withIndex().drop(loadIndex + 1)) {
        val instruction = indexed.value
        if (instruction.opcode in WIDE_MOVE_OPCODES) {
            val move = instruction as? TwoRegisterInstruction
            if (move != null && move.registerB in aliases) {
                aliases += move.registerA
            }
        }
        if (instruction.isComposeRendererCall()) {
            val reference = instruction.getReference<MethodReference>() ?: continue
            if (instruction.wideArgumentStarts(reference).any { it in aliases }) {
                return true
            }
        }
    }
    return false
}

private fun MutableMethod.injectActivatedLikeTint(unfavoriteIndex: Int): FieldReference {
    val methodInstructions = instructions.toList()
    val unfavoriteRead = methodInstructions.getOrNull(unfavoriteIndex)
        ?: throw PatchException("NewX Unfavorite enum read is out of bounds: $this")
    val unfavoriteRegister = (unfavoriteRead as? OneRegisterInstruction)?.registerA
        ?: throw PatchException("NewX Unfavorite enum read has no destination register: $this")

    val comparisons = methodInstructions.withIndex().mapNotNull { indexed ->
        if (indexed.value.opcode != Opcode.IF_EQ && indexed.value.opcode != Opcode.IF_NE) {
            return@mapNotNull null
        }
        if (unfavoriteRegister !in indexed.value.registersUsed) return@mapNotNull null
        val branch = indexed.value as? BuilderOffsetInstruction
            ?: throw PatchException("NewX Unfavorite comparison is not mutable: ${indexed.value}")
        val targetIndex = branch.target.location.index
        if (targetIndex <= indexed.index) return@mapNotNull null
        indexed.index to targetIndex
    }

    val colorCandidates = comparisons.flatMap { (comparisonIndex, comparisonTarget) ->
        methodInstructions.withIndex().flatMap { unary ->
            if (unary.index <= comparisonTarget || unary.value.opcode != Opcode.IF_EQZ) {
                return@flatMap emptyList()
            }
            val unaryBranch = unary.value as? BuilderOffsetInstruction
                ?: throw PatchException("NewX activated-like unary branch is not mutable: ${unary.value}")
            val activeEnd = unaryBranch.target.location.index
            if (activeEnd <= unary.index) return@flatMap emptyList()
            val booleanRegister = unary.value.registersUsed.singleOrNull()
                ?: return@flatMap emptyList()

            val mergeGotos = methodInstructions.withIndex().filter { indexed ->
                indexed.index in (comparisonIndex + 1) until unary.index &&
                    indexed.value.opcode in setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32) &&
                    (indexed.value as? BuilderOffsetInstruction)?.target?.location?.index == unary.index
            }
            if (mergeGotos.size != 1) return@flatMap emptyList()
            val mergeGotoIndex = mergeGotos.single().index
            val fallthroughEnd = minOf(comparisonTarget, mergeGotoIndex)
            val targetEnd =
                if (comparisonTarget < mergeGotoIndex) mergeGotoIndex else unary.index
            if (fallthroughEnd <= comparisonIndex + 1 || targetEnd <= comparisonTarget) {
                return@flatMap emptyList()
            }

            val fallthroughWrites = methodInstructions.withIndex()
                .filter { indexed ->
                    indexed.index in (comparisonIndex + 1) until fallthroughEnd &&
                        indexed.value.opcode in BOOLEAN_NORMALIZATION_OPCODES &&
                        (indexed.value as? OneRegisterInstruction)?.registerA == booleanRegister
                }
            val targetWrites = methodInstructions.withIndex()
                .filter { indexed ->
                    indexed.index in comparisonTarget until targetEnd &&
                        indexed.value.opcode in BOOLEAN_NORMALIZATION_OPCODES &&
                        (indexed.value as? OneRegisterInstruction)?.registerA == booleanRegister
                }
            if (fallthroughWrites.size != 1 || targetWrites.size != 1) return@flatMap emptyList()

            val comparison = methodInstructions[comparisonIndex]
            val equalWrites =
                if (comparison.opcode == Opcode.IF_NE) fallthroughWrites else targetWrites
            val nonEqualWrites =
                if (comparison.opcode == Opcode.IF_NE) targetWrites else fallthroughWrites
            val equalValue = methodInstructions.resolveBooleanNormalizationValue(equalWrites[0])
            val nonEqualValue = methodInstructions.resolveBooleanNormalizationValue(nonEqualWrites[0])
            if (equalValue != 1 || nonEqualValue != 0) return@flatMap emptyList()

            methodInstructions.withIndex().filter { indexed ->
                indexed.index in (unary.index + 1) until activeEnd &&
                    indexed.value.opcode == Opcode.SGET_WIDE &&
                    indexed.value.getReference<FieldReference>()?.type == "J"
            }.filter { indexed ->
                val colorRegister = (indexed.value as? OneRegisterInstruction)?.registerA
                    ?: return@filter false
                wideColorFeedsComposeRenderer(indexed.index, colorRegister)
            }
        }
    }
    val colorLoad =
        requireExactlyOne("NewX activated-like tint load in the active branch", colorCandidates)
    val activeLikeField = colorLoad.value.getReference<FieldReference>()
        ?: throw PatchException("NewX activated-like tint field is missing: $this")
    val colorRegister = (colorLoad.value as? OneRegisterInstruction)?.registerA
        ?: throw PatchException("NewX activated-like tint is not a one-register wide load: $this")
    insertHook(
        index = colorLoad.index + 1,
        // The old insertion left labels on the active-branch color load, so the tint still covers
        // exactly the paths that load the field.
        relocateBranchTargets = false,
    ) {
        invokeStatic(
            methodReference(INLINE_ACTION_ACTIVE_TINT_METHOD),
            colorRegister,
            colorRegister + 1,
        )
        moveResult(colorRegister, "J")
    }
    return activeLikeField
}

private fun List<Instruction>.resolveBooleanNormalizationValue(
    write: IndexedValue<Instruction>,
): Int? {
    val instruction = write.value
    val literal = instruction as? NarrowLiteralInstruction
    if (literal != null && instruction is OneRegisterInstruction) {
        return literal.narrowLiteral
    }
    val move = instruction as? TwoRegisterInstruction ?: return null
    if (instruction.opcode !in setOf(Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16)) {
        return null
    }
    return resolveLatestLiteral(write.index, move.registerB)
}

context(context: BytecodePatchContext)
private fun MutableMethod.resolveLottieRenderer(): ResolvedLottieRenderer {
    val candidates = instructions.withIndex().filter { indexed ->
        val reference = indexed.value.getReference<MethodReference>() ?: return@filter false
        if (reference.parameterTypes.size !in 8..9 || reference.returnType != "V") return@filter false
        val parameters = reference.parameterTypes
        parameters[0] == "Z" &&
            parameters[2] == "Z" &&
            parameters[3] == FUNCTION0_DESCRIPTOR &&
            parameters[4] == COMPOSE_MODIFIER_DESCRIPTOR &&
            parameters[5] == "Ljava/lang/String;" &&
            parameters[6] == "Landroidx/compose/runtime/Composer;" &&
            parameters[7] == "I" &&
            (parameters.size == 8 || parameters[8] == "I")
    }
    val candidate = requireExactlyOne("NewX like Lottie renderer call", candidates)
    val reference = candidate.value.getReference<MethodReference>()
        ?: throw PatchException("NewX like Lottie renderer reference is missing: $this")
    return ResolvedLottieRenderer(
        index = candidate.index,
        instruction = candidate.value,
        method = reference.resolveMutableMethod("NewX Lottie renderer"),
    )
}

context(context: BytecodePatchContext)
private fun patchLikeIconComposable(
    descriptor: String,
    activeLikeField: FieldReference,
) {
    val composable =
        context.mutableClassDefBy(descriptor).methods.singleOrNull { method ->
            method.name == "invoke" &&
                method.parameterTypes == listOf("Ljava/lang/Object;", "Ljava/lang/Object;") &&
                method.returnType == "Ljava/lang/Object;"
        } ?: throw PatchException("NewX like icon composable invoke method not found: $descriptor")
    val lottieRenderer = composable.resolveLottieRenderer()
    val rangeInstruction = lottieRenderer.instruction as? RegisterRangeInstruction
        ?: throw PatchException("NewX like Lottie renderer is not an invoke-range: $composable")
    val animationRegister = rangeInstruction.startRegister + 2

    composable.insertHook(
        index = lottieRenderer.index,
        // The old insertion left labels on the renderer call: a branch straight to the call keeps
        // its untinted animation flag, matching the previous behavior.
        relocateBranchTargets = false,
    ) {
        invokeStatic(methodReference(INLINE_LIKE_ANIMATION_METHOD), animationRegister)
        moveResult(animationRegister, "Z")
    }
    lottieRenderer.method.injectLottieFallbackTint(activeLikeField)
}

private fun MutableMethod.injectLottieFallbackTint(activeLikeField: FieldReference) {
    val colorLoads =
        instructions.withIndex().filter { (_, instruction) ->
            instruction.opcode == Opcode.SGET_WIDE &&
                instruction.getReference<FieldReference>()?.toString() == activeLikeField.toString()
        }
    val colorLoad = requireExactlyOne("NewX Lottie fallback tint", colorLoads)
    val colorRegister = (colorLoad.value as? OneRegisterInstruction)?.registerA
        ?: throw PatchException("NewX Lottie fallback tint has no wide register: $this")
    insertHook(
        index = colorLoad.index + 1,
        // The old insertion left labels on the fallback color load, so the tint still covers
        // exactly the paths that load it.
        relocateBranchTargets = false,
    ) {
        invokeStatic(
            methodReference(INLINE_ACTION_ACTIVE_TINT_METHOD),
            colorRegister,
            colorRegister + 1,
        )
        moveResult(colorRegister, "J")
    }
}

private fun MutableMethod.injectDynamicAccentTones(
    tonesByField: Map<String, Int>,
    scratchRegisterStart: Int,
) {
    val toneLoads =
        instructions.withIndex().mapNotNull { indexed ->
            if (indexed.value.opcode != Opcode.SGET_WIDE) return@mapNotNull null
            val field = indexed.value.getReference<FieldReference>() ?: return@mapNotNull null
            val tone = tonesByField[field.toString()] ?: return@mapNotNull null
            Triple(indexed.index, indexed.value, tone)
        }
    if (toneLoads.size != ACCENT_TONE_COUNT) {
        throw PatchException(
            "Expected $ACCENT_TONE_COUNT NewX accent loads in $this, found ${toneLoads.size}; " +
                "tones=${toneLoads.map { (_, _, tone) -> tone }}, " +
                "fields=${toneLoads.map { (_, instruction, _) -> instruction.getReference<FieldReference>() }}",
        )
    }
    val observedTones = toneLoads.map { (_, _, tone) -> tone }
    val observedFields = toneLoads.map { (_, instruction, _) ->
        instruction.getReference<FieldReference>()?.toString().orEmpty()
    }
    val expectedTones = (0 until ACCENT_TONE_COUNT).toList()
    if (observedTones.sorted() != expectedTones || observedFields.toSet().size != ACCENT_TONE_COUNT) {
        throw PatchException(
            "NewX accent tone coverage is ambiguous in $this: " +
                "tones=$observedTones, fields=$observedFields",
        )
    }

    val enabledRegister = scratchRegisterStart + 2
    val registerCount =
        implementation?.registerCount
            ?: throw PatchException("NewX dynamic palette constructor has no implementation: $this")
    if (enabledRegister >= registerCount) {
        throw PatchException(
            "NewX dynamic accent settings snapshot register v$enabledRegister is out of bounds " +
                "for $this with $registerCount registers",
        )
    }

    val firstToneIndex = toneLoads.minOf { (index, _, _) -> index }
    insertHook(
        index = firstToneIndex,
        // The old insertion left labels on the first tone load, so a branch straight to that load
        // keeps reading the snapshot register without the settings refresh, as before.
        relocateBranchTargets = false,
    ) {
        invokeStatic(methodReference(PALETTE_IS_ENABLED_METHOD))
        moveResult(enabledRegister, "Z")
    }

    toneLoads.asReversed().forEach { (index, instruction, tone) ->
        val colorRegister =
            (instruction as? OneRegisterInstruction)?.registerA
                ?: throw PatchException("NewX accent load has no destination register: $instruction")
        if (colorRegister !in 0..14) {
            throw PatchException(
                "NewX accent color needs a low wide register, found v$colorRegister: $this",
            )
        }
        insertHook(
            index = index + ACCENT_SETTINGS_SNAPSHOT_INSTRUCTION_COUNT + 1,
            // The old insertion left labels on the instruction after the tone load, so only the
            // paths that load the tone get the settings-aware value.
            relocateBranchTargets = false,
        ) {
            move(scratchRegisterStart, colorRegister, "J")
            invokeStatic(
                methodReference("$DYNAMIC_COLOR_PALETTE_DESCRIPTOR->accentTone$tone(JZ)J"),
                scratchRegisterStart,
                scratchRegisterStart + 1,
                enabledRegister,
            )
            moveResult(colorRegister, "J")
        }
    }
}

private fun MutableMethod.injectDynamicPalette(
    allocation: PaletteAllocation,
    kind: PaletteKind,
    paletteDescriptor: String,
    constructorReference: String,
) {
    val originalAllocation = instructions.getOrNull(allocation.index)
        ?: throw PatchException(
            "NewX $kind palette allocation index is out of bounds: ${allocation.index}",
        )
    if (originalAllocation.opcode != Opcode.NEW_INSTANCE ||
        originalAllocation.getReference<TypeReference>()?.type != paletteDescriptor
    ) {
        throw PatchException("NewX $kind palette allocation changed before mutation: $this")
    }
    val registerCount = implementation?.registerCount
        ?: throw PatchException("NewX $kind palette factory has no implementation: $this")
    if (registerCount < REQUIRED_FACTORY_REGISTER_COUNT) {
        throw PatchException(
            "NewX $kind palette factory needs $REQUIRED_FACTORY_REGISTER_COUNT registers, " +
                "found $registerCount: $this",
        )
    }

    if (kind == PaletteKind.LIGHTS_OUT) {
        injectDarkBackgrounds(
            resolvePaletteConstructor(allocation, constructorReference),
        )
    }

    // The typed hook relocates every incoming label from the original new-instance onto the API
    // guard, exactly like the helper it replaces: every path that reached the allocation runs the
    // guard first. Its trailing no-op falls through into the byte-for-byte original allocation
    // sequence, so API < 31 and dynamic-off keep allocating the native palette.
    insertHook(
        index = allocation.index,
        relocateBranchTargets = true,
    ) {
        emitDynamicPaletteGuard(kind, paletteDescriptor, constructorReference)
    }
}

private fun Block.emitDynamicPaletteGuard(
    kind: PaletteKind,
    paletteDescriptor: String,
    constructorReference: String,
) {
    val originalLabel = "piko_newx_dynamic_color_original_${kind.name.lowercase()}"
    invokeStatic(methodReference(PALETTE_IS_ENABLED_METHOD))
    moveResult(36, "Z")
    ifEqz(36, Target.Local(originalLabel))
    if (kind == PaletteKind.LIGHTS_OUT) {
        invokeStatic(methodReference(PALETTE_IS_AMOLED_BLACK_METHOD))
        moveResult(36, "Z")
    }
    newInstance(0, paletteDescriptor)
    constInt(1, if (kind.isLight) 1 else 0)
    repeat(PALETTE_COLOR_COUNT) { token ->
        val colorRegister = 2 + token * 2
        constInt(colorRegister, token)
        if (kind == PaletteKind.LIGHTS_OUT) {
            move(colorRegister + 1, 36, "Z")
            invokeStatic(
                methodReference("$DYNAMIC_COLOR_PALETTE_DESCRIPTOR->${kind.helperMethod}(IZ)J"),
                colorRegister,
                colorRegister + 1,
            )
        } else {
            invokeStatic(
                methodReference("$DYNAMIC_COLOR_PALETTE_DESCRIPTOR->${kind.helperMethod}(I)J"),
                colorRegister,
            )
        }
        moveResult(colorRegister, "J")
    }
    invokeDirect(
        methodReference(constructorReference),
        *IntArray(PALETTE_CONSTRUCTOR_REGISTER_COUNT) { register -> register },
    )
    returnObject(0)
    label(originalLabel)
    nop()
}

/**
 * Runs only on the original dark palette path, which dynamic color bypasses with an early
 * `return-object`. Defaults the shared dark surfaces to the classic dim values and lets AMOLED
 * overwrite them with pure black, so dynamic-off + AMOLED-off restores dim while AMOLED keeps
 * its independent pure-black behavior.
 */
private fun MutableMethod.injectDarkBackgrounds(constructor: PaletteConstructor) {
    // The AMOLED flag must not live in the constructor's argument registers: the block rewrites
    // the color arguments of the call it sits in front of.
    val constructorRegisters =
        (constructor.instruction.startRegister until
            constructor.instruction.startRegister + constructor.instruction.registerCount).toList()
    insertHook(
        index = constructor.index,
        excludedRegisters = constructorRegisters,
        // The old insertion kept incoming labels on the constructor call: a branch that reached
        // the call straight away kept the native surfaces and must keep them.
        relocateBranchTargets = false,
    ) {
        // `move-result` (11x) and `if-eqz` (21t) both encode a byte register.
        val amoledRegister = scratchRegister(RegisterLimit.BYTE)
        invokeStatic(methodReference(PALETTE_IS_AMOLED_BLACK_METHOD))
        moveResult(amoledRegister, "Z")
        appendBackgroundColors(constructor, DIM_BACKGROUND_COLORS)
        // Not AMOLED: keep the dim surfaces and fall through into the untouched constructor call.
        ifEqz(amoledRegister, Target.Original)
        appendBackgroundColors(constructor, AMOLED_BACKGROUND_COLORS)
    }
}

private fun Block.appendBackgroundColors(
    constructor: PaletteConstructor,
    colors: Map<Int, Long>,
) {
    colors.toSortedMap().forEach { (token, color) ->
        val colorRegister = constructor.instruction.startRegister + 2 + token * 2
        constLong(colorRegister, color)
    }
}
