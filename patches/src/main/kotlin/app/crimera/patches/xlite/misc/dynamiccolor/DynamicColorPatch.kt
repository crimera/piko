package app.crimera.patches.xlite.misc.dynamiccolor

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.Groups
import app.crimera.patches.xlite.settings.group
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.toggle
import app.crimera.patches.xlite.settings.xLiteSettings
import app.crimera.patches.xlite.timeline.fieldForToStringLabel
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.xlite.utils.Constants.EXTENSION_PACKAGE
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.string
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction22t
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val PALETTE_CONSTRUCTOR_PARAMETERS = "ZJJJJJJJJJJJJJJJJJ"
private const val PALETTE_COLOR_COUNT = 17
private const val ACCENT_TONE_COUNT = 13
private const val COLOR_SCALE_COUNT = 10
private const val EXPECTED_FACTORY_COUNT = 3
private const val REQUIRED_FACTORY_REGISTER_COUNT = 37
private const val FUNCTION0_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val DYNAMIC_COLOR_PALETTE_DESCRIPTOR =
    "$EXTENSION_PACKAGE/theme/DynamicColorPalette;"

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
    val paletteAllocationIndex: Int,
)

@Suppress("unused")
val dynamicColorPatch =
    bytecodePatch(
        name = "X-Lite: Dynamic color",
        description = "Applies the system Material You palette to X-Lite.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        val useAmoledBlack =
            xLiteSettings {
                category(Categories.APPEARANCE) {
                    group(Groups.DYNAMIC_COLORS) {
                        toggle(
                            id = "xlite.theme.dynamic_color",
                            strings = settingStrings("piko_xlite_dynamic_color"),
                            order = 100,
                            defaultValue = true,
                            rebootApp = true,
                        )
                        val amoledBlack =
                            toggle(
                                id = "xlite.theme.amoled_black",
                                strings = settingStrings("piko_xlite_dynamic_color_amoled"),
                                order = 200,
                                defaultValue = true,
                                rebootApp = true,
                            )
                        toggle(
                            id = "xlite.theme.dynamic_like",
                            strings = settingStrings("piko_xlite_dynamic_color_like"),
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
            requireExactlyOne("X-Lite Horizon theme palette provider", providerMatches)
            val provider = providerMatches.single().method
            val paletteDescriptor = provider.returnType
            if (!paletteDescriptor.startsWith("L")) {
                throw PatchException(
                    "X-Lite Horizon theme palette provider does not return an object: $provider",
                )
            }

            val constructorReference = resolvePaletteConstructorReference(paletteDescriptor)
            val cacheFieldsByKind = provider.resolvePaletteCacheFields(paletteDescriptor)
            val factories =
                PaletteKind.values().map { kind ->
                    resolveFactory(
                        kind,
                        cacheFieldsByKind.getValue(kind),
                        paletteDescriptor,
                        constructorReference,
                    )
                }

            if (factories.map { it.method to it.paletteAllocationIndex }.distinct().size !=
                EXPECTED_FACTORY_COUNT
            ) {
                throw PatchException(
                    "Expected $EXPECTED_FACTORY_COUNT distinct X-Lite palette factory branches, found " +
                        factories.joinToString(),
                )
            }

            factories
                .groupBy(ResolvedFactory::method)
                .values
                .forEach { methodFactories ->
                    methodFactories.sortedByDescending(ResolvedFactory::paletteAllocationIndex)
                        .forEach { factory ->
                            factory.method.injectDynamicPalette(
                                index = factory.paletteAllocationIndex,
                                kind = factory.kind,
                                paletteDescriptor = paletteDescriptor,
                                constructorReference = constructorReference,
                            )
                        }
                }

            patchDynamicAccentPalettes()
            patchInlineActionTints()
        }
    }

private fun <T> requireExactlyOne(
    target: String,
    matches: List<T>,
) {
    if (matches.size == 1) return
    throw PatchException("Expected one $target, found ${matches.size}: ${matches.joinToString()}")
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
    if (constructors.size != 1) {
        throw PatchException(
            "Expected one X-Lite Horizon palette constructor, found ${constructors.size}: " +
                constructors.joinToString(),
        )
    }
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
    if (mappingArrayFields.size != 1) {
        throw PatchException(
            "Expected one X-Lite theme-variant mapping array, found " +
                "${mappingArrayFields.size}: ${mappingArrayFields.joinToString()}",
        )
    }
    val selectorByKind = resolveThemeVariantSelectors(mappingArrayFields.single())
    val selectorRegister =
        instructions.singleOrNull { instruction -> instruction.opcode == Opcode.AGET }
            ?.let { instruction -> (instruction as? ThreeRegisterInstruction)?.registerA }
            ?: throw PatchException("Expected one X-Lite theme-variant selector read: $this")

    val branchStartBySelector = mutableMapOf<Int, Int>()
    instructions.withIndex().forEach { (index, instruction) ->
        if (instruction.opcode != Opcode.IF_EQ && instruction.opcode != Opcode.IF_NE) return@forEach
        val branch = instruction as? BuilderInstruction22t
            ?: throw PatchException("X-Lite theme-variant branch is not mutable: $instruction")
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
            throw PatchException("Duplicate X-Lite theme-variant branch for selector $selector: $this")
        }
    }

    val fieldsByKind =
        selectorByKind.mapValues { (_, selector) ->
            val branchStart = branchStartBySelector[selector]
                ?: throw PatchException("X-Lite theme-variant branch $selector was not found: $this")
            paletteCacheFieldAt(branchStart, paletteDescriptor)
        }
    val cacheFields = fieldsByKind.values.toList()
    if (cacheFields.distinctBy(FieldReference::toString).size != PaletteKind.values().size) {
        throw PatchException("X-Lite theme variants do not resolve distinct caches: $fieldsByKind")
    }
    if (cacheFields.map { field -> field.definingClass }.distinct().size != 1) {
        throw PatchException("X-Lite Horizon palette caches must share one holder: $fieldsByKind")
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
            "X-Lite theme-variant mapping holder has no class initializer: " +
                mappingArrayField.definingClass,
        )
    return PaletteKind.values().associateWith { kind ->
        val enumReadIndex =
            initializer.instructions.withIndex().singleOrNull { (_, instruction) ->
                instruction.opcode == Opcode.SGET_OBJECT &&
                    instruction.getReference<FieldReference>()?.name == kind.themeVariantFieldName
            }?.index ?: throw PatchException(
                "Expected one X-Lite ${kind.name} theme-variant enum read: $initializer",
            )
        val arrayStore =
            initializer.instructions.withIndex().drop(enumReadIndex + 1)
                .firstOrNull { (_, instruction) -> instruction.opcode == Opcode.APUT }
                ?: throw PatchException("X-Lite ${kind.name} selector store was not found: $initializer")
        val valueRegister = (arrayStore.value as? ThreeRegisterInstruction)?.registerA
            ?: throw PatchException("X-Lite ${kind.name} selector store has no value register")
        initializer.instructions.resolveLatestLiteral(arrayStore.index, valueRegister)
            ?: throw PatchException("X-Lite ${kind.name} selector literal was not found: $initializer")
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
        throw PatchException("X-Lite palette cache branch has an unexpected shape at $index: $this")
    }
    return sequence[0].getReference<FieldReference>()
        ?: throw PatchException("X-Lite palette cache field is missing at $index: $this")
}

context(context: BytecodePatchContext)
private fun resolveFactory(
    kind: PaletteKind,
    cacheField: FieldReference,
    paletteDescriptor: String,
    constructorReference: String,
): ResolvedFactory {
    val holder = context.mutableClassDefBy(cacheField.definingClass)
    val initializer =
        holder.methods.singleOrNull { method ->
            method.name == "<clinit>" && method.parameterTypes.isEmpty() && method.returnType == "V"
        } ?: throw PatchException("X-Lite palette cache holder has no class initializer: $holder")
    val stores =
        initializer.instructions.withIndex().filter { indexed ->
            indexed.value.opcode == Opcode.SPUT_OBJECT &&
                indexed.value.getReference<FieldReference>()?.toString() == cacheField.toString()
        }
    if (stores.size != 1) {
        throw PatchException(
            "Expected one cache store for $kind in $holder, found ${stores.size}",
        )
    }

    val storeIndex = stores.single().index
    val allocation =
        initializer.instructions
            .take(storeIndex)
            .withIndex()
            .lastOrNull { indexed -> indexed.value.opcode == Opcode.NEW_INSTANCE }
            ?: throw PatchException("No Function0 allocation found for X-Lite $kind palette cache")
    val factoryDescriptor =
        allocation.value.getReference<TypeReference>()?.type
            ?: throw PatchException("X-Lite $kind palette factory allocation has no type")
    val initializesAllocation =
        initializer.instructions
            .drop(allocation.index + 1)
            .take(storeIndex - allocation.index - 1)
            .mapNotNull { instruction -> instruction.getReference<MethodReference>() }
            .any { reference -> reference.name == "<init>" && reference.returnType == "V" }
    if (!initializesAllocation) {
        throw PatchException("X-Lite $kind palette cache allocation is not initialized: $factoryDescriptor")
    }

    val factoryClass = context.mutableClassDefBy(factoryDescriptor)
    if (FUNCTION0_DESCRIPTOR !in factoryClass.interfaces) {
        throw PatchException("X-Lite $kind palette cache factory is not a Function0: $factoryDescriptor")
    }
    val invokes =
        factoryClass.methods.filter { method ->
            method.name == "invoke" &&
                method.parameterTypes.isEmpty() &&
                method.returnType == "Ljava/lang/Object;"
        }
    if (invokes.size != 1) {
        throw PatchException(
            "Expected one X-Lite $kind Function0.invoke(), found ${invokes.size}: " +
                invokes.joinToString(),
        )
    }

    val invoke = invokes.single()
    val paletteAllocations =
        invoke.instructions.withIndex().filter { indexed ->
            indexed.value.opcode == Opcode.NEW_INSTANCE &&
                indexed.value.getReference<TypeReference>()?.type == paletteDescriptor
        }
    val matchingAllocations =
        paletteAllocations.filter { allocation ->
            invoke.resolvePaletteIsLight(allocation.index, constructorReference) == kind.isLight
        }
    if (matchingAllocations.size != 1) {
        throw PatchException(
            "Expected one ${kind.name} X-Lite palette allocation branch, found " +
                "${matchingAllocations.size} of ${paletteAllocations.size}: $invoke",
        )
    }
    return ResolvedFactory(kind, invoke, matchingAllocations.single().index)
}

private fun MutableMethod.resolvePaletteIsLight(
    allocationIndex: Int,
    constructorReference: String,
): Boolean {
    val constructor =
        instructions.withIndex().drop(allocationIndex + 1).firstOrNull { (_, instruction) ->
            instruction.getReference<MethodReference>()?.toString() == constructorReference
        } ?: throw PatchException("X-Lite palette allocation has no matching constructor call: $this")
    val range = constructor.value as? RegisterRangeInstruction
        ?: throw PatchException("X-Lite palette constructor is not an invoke-range: $this")
    val allocationRegister =
        (instructions[allocationIndex] as? OneRegisterInstruction)?.registerA
            ?: throw PatchException("X-Lite palette allocation has no destination register: $this")
    if (allocationRegister != range.startRegister) {
        throw PatchException(
            "X-Lite palette allocation v$allocationRegister does not match constructor receiver " +
                "v${range.startRegister}: $this",
        )
    }

    val isLightRegister = range.startRegister + 1
    val isLightLiteral =
        instructions.subList(allocationIndex + 1, constructor.index).asReversed()
            .firstOrNull { instruction ->
                instruction is OneRegisterInstruction &&
                    instruction is NarrowLiteralInstruction &&
                    instruction.registerA == isLightRegister &&
                    instruction.narrowLiteral in 0..1
            } as? NarrowLiteralInstruction
            ?: throw PatchException("X-Lite palette isLight literal not found: $this")
    return isLightLiteral.narrowLiteral == 1
}

context(context: BytecodePatchContext)
private fun patchDynamicAccentPalettes() {
    val providerMatches =
        XLiteDynamicColorPaletteProviderFingerprint.scopedMatchAll()
    requireExactlyOne("X-Lite dynamic color-scale provider", providerMatches)
    val provider = providerMatches.single().method
    val paletteInterfaceDescriptor = provider.returnType
    if (!paletteInterfaceDescriptor.startsWith("L")) {
        throw PatchException(
            "X-Lite dynamic color-scale provider does not return an object: $provider",
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
            "Expected three X-Lite dynamic palette casts, found ${implementationCasts.size}: " +
                implementationCasts.joinToString(),
        )
    }

    val castsByDescriptor = implementationCasts.groupingBy { descriptor -> descriptor }.eachCount()
    val standardDescriptor =
        castsByDescriptor.entries.singleOrNull { entry -> entry.value == 1 }?.key
            ?: throw PatchException(
                "Expected one standard X-Lite dynamic palette cast: $castsByDescriptor",
            )
    val darkDescriptor =
        castsByDescriptor.entries.singleOrNull { entry -> entry.value == 2 }?.key
            ?: throw PatchException(
                "Expected two shared dark X-Lite dynamic palette casts: $castsByDescriptor",
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
            "Expected $ACCENT_TONE_COUNT distinct standard X-Lite accent fields, found " +
                standardAccentFields.joinToString(),
        )
    }
    if (standardAccentFields.map { field -> field.definingClass }.distinct().size != 1) {
        throw PatchException(
            "X-Lite accent fields must share one static palette: " +
                standardAccentFields.joinToString(),
        )
    }

    val tonesByField =
        standardAccentFields
            .mapIndexed { tone, field -> field.toString() to tone }
            .toMap()
    standardConstructor.injectDynamicAccentTones(tonesByField)
    darkConstructor.injectDynamicAccentTones(tonesByField)
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
    if (constructors.size == 1) return constructors.single()
    throw PatchException(
        "Expected one no-argument X-Lite dynamic palette constructor for $descriptor, found " +
            "${constructors.size}: ${constructors.joinToString()}",
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
            "Expected $expectedColorCount X-Lite color-scale fields per palette, found " +
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
        throw PatchException("X-Lite dark color scales are not reversed at $mismatchedScales")
    }
    return standardScales.first()
}

context(context: BytecodePatchContext)
private fun patchInlineActionTints() {
    val inlineActionEntryMatches =
        Fingerprint(
            definingClass = "Lcom/x/models/",
            name = "toString",
            returnType = "Ljava/lang/String;",
            parameters = emptyList(),
            filters = listOf(
                string("InlineActionEntry(actionType="),
                string(", isEnabled="),
            ),
        ).scopedMatchAll()
    requireExactlyOne("X-Lite inline action entry model", inlineActionEntryMatches)
    val inlineActionEntryMatch = inlineActionEntryMatches.single()
    val inlineActionEntryClass = inlineActionEntryMatch.originalClassDef
    val actionTypeField =
        inlineActionEntryMatch.fieldForToStringLabel("InlineActionEntry(actionType=")
    if (!actionTypeField.type.startsWith("L")) {
        throw PatchException("X-Lite inline action-type field is not an object: $actionTypeField")
    }
    val enabledField = inlineActionEntryMatch.fieldForBooleanToStringLabel(", isEnabled=")
    if (enabledField.type != "Z") {
        throw PatchException("X-Lite inline action enabled field is not boolean: $enabledField")
    }
    val actionTypeDescriptor = actionTypeField.type
    val entryMatches =
        Fingerprint(
            definingClass = "Lcom/x/inlineactionbar/",
            parameters = listOf(
                inlineActionEntryClass.type,
                "L",
                "J",
                "F",
                "L",
                "J",
                "L",
                "L",
                "Landroidx/compose/ui/Modifier;",
                "Landroidx/compose/runtime/Composer;",
                "I",
            ),
            returnType = "V",
            filters = listOf(
                fieldAccess(opcode = Opcode.IGET_OBJECT, reference = actionTypeField),
                fieldAccess(opcode = Opcode.IGET_BOOLEAN, reference = enabledField),
            ),
        ).scopedMatchAll()
    requireExactlyOne("X-Lite inline action entry renderer", entryMatches)
    val entryMethod = entryMatches.single().method
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
        } ?: throw PatchException("X-Lite inline action tint renderer call not found: $entryMethod")
    val tintMethod = tintReference.resolveMutableMethod("X-Lite inline action tint renderer")
    val unfavoriteIndex =
        tintMethod.instructions.indexOfFirst { instruction ->
            instruction.getReference<FieldReference>()?.let { field ->
                field.definingClass == actionTypeDescriptor && field.name == "Unfavorite"
            } == true
        }
    if (unfavoriteIndex < 0) {
        throw PatchException("X-Lite activated-like branch not found: $tintMethod")
    }
    val likeComposableConstructors =
        tintMethod.instructions.drop(unfavoriteIndex + 1)
            .mapNotNull { instruction -> instruction.getReference<MethodReference>() }
            .filter { reference ->
                reference.name == "<init>" &&
                    reference.parameterTypes.size == 5 &&
                    reference.parameterTypes[0] == "Ljava/lang/String;" &&
                    reference.parameterTypes[1] == "Z" &&
                    reference.parameterTypes[2] == "Ljava/lang/Long;" &&
                    reference.parameterTypes[3] == "F" &&
                    reference.parameterTypes[4].toString()
                        .startsWith("Landroidx/compose/runtime/") &&
                    reference.definingClass.hasComposableLambdaInvoke()
            }.distinctBy(MethodReference::toString)
    requireExactlyOne("X-Lite like icon composable constructor", likeComposableConstructors)
    val likeComposableConstructor = likeComposableConstructors.single()

    entryMethod.addInstructions(
        0,
        """
        invoke-static/range {p2 .. p3}, $DYNAMIC_COLOR_PALETTE_DESCRIPTOR->inlineActionTint(J)J
        move-result-wide p2
        """.trimIndent(),
    )
    val activeLikeField = tintMethod.injectActivatedLikeTint(unfavoriteIndex)
    patchLikeIconComposable(likeComposableConstructor.definingClass, activeLikeField)
}

private fun app.morphe.patcher.Match.fieldForBooleanToStringLabel(
    label: String,
): FieldReference {
    val labelIndex = method.instructions.indexOfFirst { instruction ->
        instruction.getReference<com.android.tools.smali.dexlib2.iface.reference.StringReference>()
            ?.string == label
    }
    if (labelIndex < 0) throw PatchException("X-Lite boolean model label was not found: $label")
    val fields =
        method.instructions.drop(labelIndex + 1).mapNotNull { instruction ->
            if (instruction.opcode != Opcode.IGET_BOOLEAN) return@mapNotNull null
            instruction.getReference<FieldReference>()?.takeIf { field ->
                field.definingClass == originalMethod.definingClass
            }
        }.distinctBy(FieldReference::toString)
    requireExactlyOne("X-Lite boolean model field after '$label'", fields)
    return fields.single()
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

private fun MutableMethod.injectActivatedLikeTint(unfavoriteIndex: Int): FieldReference {
    val colorLoad =
        instructions.withIndex()
            .drop(unfavoriteIndex + 1)
            .take(20)
            .firstOrNull { (_, instruction) -> instruction.opcode == Opcode.SGET_WIDE }
            ?: throw PatchException("X-Lite activated-like tint load not found: $this")
    val activeLikeField = colorLoad.value.getReference<FieldReference>()
        ?: throw PatchException("X-Lite activated-like tint field is missing: $this")
    val colorRegister = (colorLoad.value as? OneRegisterInstruction)?.registerA
        ?: throw PatchException("X-Lite activated-like tint is not a one-register wide load: $this")
    addInstructions(
        colorLoad.index + 1,
        """
        invoke-static/range {v$colorRegister .. v${colorRegister + 1}}, $DYNAMIC_COLOR_PALETTE_DESCRIPTOR->inlineActionActiveTint(J)J
        move-result-wide v$colorRegister
        """.trimIndent(),
    )
    return activeLikeField
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
        } ?: throw PatchException("X-Lite like icon composable invoke method not found: $descriptor")
    val lottieCall =
        composable.instructions.withIndex().singleOrNull { (_, instruction) ->
            val reference = instruction.getReference<MethodReference>() ?: return@singleOrNull false
            reference.parameterTypes.size == 8 &&
                reference.parameterTypes[0] == "Z" &&
                reference.parameterTypes[2] == "Z" &&
                reference.parameterTypes[3] == "Lkotlin/jvm/functions/Function0;" &&
                reference.parameterTypes[4] == "Landroidx/compose/ui/Modifier;" &&
                reference.parameterTypes[5] == "Ljava/lang/String;" &&
                reference.parameterTypes[6] == "Landroidx/compose/runtime/Composer;" &&
                reference.parameterTypes[7] == "I" &&
                reference.returnType == "V"
        } ?: throw PatchException("X-Lite like Lottie renderer call not found: $composable")
    val rangeInstruction = lottieCall.value as? RegisterRangeInstruction
        ?: throw PatchException("X-Lite like Lottie renderer is not an invoke-range: $composable")
    val animationRegister = rangeInstruction.startRegister + 2
    val lottieReference = lottieCall.value.getReference<MethodReference>()
        ?: throw PatchException("X-Lite like Lottie renderer reference not found: $composable")

    composable.addInstructions(
        lottieCall.index,
        """
        invoke-static/range {v$animationRegister .. v$animationRegister}, $DYNAMIC_COLOR_PALETTE_DESCRIPTOR->inlineLikeAnimation(Z)Z
        move-result v$animationRegister
        """.trimIndent(),
    )
    lottieReference.resolveMutableMethod("X-Lite Lottie renderer")
        .injectLottieFallbackTint(activeLikeField)
}

private fun MutableMethod.injectLottieFallbackTint(activeLikeField: FieldReference) {
    val colorLoads =
        instructions.withIndex().filter { (_, instruction) ->
            instruction.opcode == Opcode.SGET_WIDE &&
                instruction.getReference<FieldReference>()?.toString() == activeLikeField.toString()
        }
    if (colorLoads.size != 1) {
        throw PatchException(
            "Expected one X-Lite Lottie fallback tint, found ${colorLoads.size}: $this",
        )
    }
    val colorLoad = colorLoads.single()
    val colorRegister = (colorLoad.value as? OneRegisterInstruction)?.registerA
        ?: throw PatchException("X-Lite Lottie fallback tint has no wide register: $this")
    addInstructions(
        colorLoad.index + 1,
        """
        invoke-static/range {v$colorRegister .. v${colorRegister + 1}}, $DYNAMIC_COLOR_PALETTE_DESCRIPTOR->inlineActionActiveTint(J)J
        move-result-wide v$colorRegister
        """.trimIndent(),
    )
}

private fun MutableMethod.injectDynamicAccentTones(tonesByField: Map<String, Int>) {
    val toneLoads =
        instructions.withIndex().mapNotNull { indexed ->
            if (indexed.value.opcode != Opcode.SGET_WIDE) return@mapNotNull null
            val field = indexed.value.getReference<FieldReference>() ?: return@mapNotNull null
            val tone = tonesByField[field.toString()] ?: return@mapNotNull null
            Triple(indexed.index, indexed.value, tone)
        }
    if (toneLoads.size != ACCENT_TONE_COUNT) {
        throw PatchException(
            "Expected $ACCENT_TONE_COUNT X-Lite accent loads in $this, found ${toneLoads.size}",
        )
    }

    if (implementation == null) {
        throw PatchException("X-Lite dynamic palette constructor has no implementation: $this")
    }

    toneLoads.asReversed().forEach { (index, instruction, tone) ->
        val colorRegister =
            (instruction as? OneRegisterInstruction)?.registerA
                ?: throw PatchException("X-Lite accent load has no destination register: $instruction")
        if (colorRegister !in 0..14) {
            throw PatchException(
                "X-Lite accent color needs a low wide register, found v$colorRegister: $this",
            )
        }
        addInstructions(
            index + 1,
            """
            invoke-static {v$colorRegister, v${colorRegister + 1}}, $DYNAMIC_COLOR_PALETTE_DESCRIPTOR->accentTone$tone(J)J
            move-result-wide v$colorRegister
            """.trimIndent(),
        )
    }
}

private fun MutableMethod.injectDynamicPalette(
    index: Int,
    kind: PaletteKind,
    paletteDescriptor: String,
    constructorReference: String,
) {
    val originalAllocation = instructions.getOrNull(index)
        ?: throw PatchException("X-Lite $kind palette allocation index is out of bounds: $index")
    if (originalAllocation.opcode != Opcode.NEW_INSTANCE ||
        originalAllocation.getReference<TypeReference>()?.type != paletteDescriptor
    ) {
        throw PatchException("X-Lite $kind palette allocation changed before mutation: $this")
    }
    val registerCount = implementation?.registerCount
        ?: throw PatchException("X-Lite $kind palette factory has no implementation: $this")
    if (registerCount < REQUIRED_FACTORY_REGISTER_COUNT) {
        throw PatchException(
            "X-Lite $kind palette factory needs $REQUIRED_FACTORY_REGISTER_COUNT registers, " +
                "found $registerCount: $this",
        )
    }

    // This helper moves all incoming labels from the original new-instance to the API guard.
    // API < 31 falls through a no-op into the byte-for-byte original allocation sequence.
    addInstructionsAtControlFlowLabel(
        index,
        kind.dynamicPaletteInstructions(paletteDescriptor, constructorReference),
    )
}

private fun PaletteKind.dynamicPaletteInstructions(
    paletteDescriptor: String,
    constructorReference: String,
): String {
    val originalLabel = "piko_xlite_dynamic_color_original_${name.lowercase()}"
    return buildString {
        appendLine("invoke-static {}, $DYNAMIC_COLOR_PALETTE_DESCRIPTOR->isEnabled()Z")
        appendLine("move-result v36")
        appendLine("if-eqz v36, :$originalLabel")
        if (this@dynamicPaletteInstructions == PaletteKind.LIGHTS_OUT) {
            appendLine("invoke-static {}, $DYNAMIC_COLOR_PALETTE_DESCRIPTOR->isAmoledBlack()Z")
            appendLine("move-result v36")
        }
        appendLine("new-instance v0, $paletteDescriptor")
        appendLine("const/4 v1, ${if (isLight) "0x1" else "0x0"}")
        repeat(PALETTE_COLOR_COUNT) { token ->
            val colorRegister = 2 + token * 2
            appendLine("const/16 v$colorRegister, 0x${token.toString(16)}")
            if (this@dynamicPaletteInstructions == PaletteKind.LIGHTS_OUT) {
                appendLine("move/from16 v${colorRegister + 1}, v36")
                appendLine(
                    "invoke-static/range {v$colorRegister .. v${colorRegister + 1}}, " +
                        "$DYNAMIC_COLOR_PALETTE_DESCRIPTOR->$helperMethod(IZ)J",
                )
            } else {
                appendLine(
                    "invoke-static/range {v$colorRegister .. v$colorRegister}, " +
                        "$DYNAMIC_COLOR_PALETTE_DESCRIPTOR->$helperMethod(I)J",
                )
            }
            appendLine("move-result-wide v$colorRegister")
        }
        appendLine("invoke-direct/range {v0 .. v35}, $constructorReference")
        appendLine("return-object v0")
        appendLine(":$originalLabel")
        append("nop")
    }
}
