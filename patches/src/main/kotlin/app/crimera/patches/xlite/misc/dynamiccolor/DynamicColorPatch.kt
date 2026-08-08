package app.crimera.patches.xlite.misc.dynamiccolor

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.Groups
import app.crimera.patches.xlite.settings.group
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.toggle
import app.crimera.patches.xlite.settings.xLiteSettings
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.xlite.utils.Constants.EXTENSION_PACKAGE
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val PALETTE_CONSTRUCTOR_PARAMETERS = "ZJJJJJJJJJJJJJJJJJ"
private const val PALETTE_COLOR_COUNT = 17
private const val ACCENT_TONE_COUNT = 13
private const val EXPECTED_FACTORY_COUNT = 3
private const val REQUIRED_FACTORY_REGISTER_COUNT = 37
private const val FUNCTION0_DESCRIPTOR = "Lkotlin/jvm/functions/Function0;"
private const val POST_ACTION_TYPE_DESCRIPTOR = "Lcom/x/models/PostActionType;"
private const val DYNAMIC_COLOR_PALETTE_DESCRIPTOR =
    "$EXTENSION_PACKAGE/theme/DynamicColorPalette;"

private enum class PaletteKind(
    val helperMethod: String,
    val isLight: Boolean,
) {
    STANDARD("light", true),
    DIM("dark", false),
    LIGHTS_OUT("lightsOut", false),
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
            val providerMatches = HorizonThemePaletteProviderFingerprint.matchAll()
            requireExactlyOne("X-Lite Horizon theme palette provider", providerMatches)
            val provider = providerMatches.single().method
            val paletteDescriptor = provider.returnType
            if (!paletteDescriptor.startsWith("L")) {
                throw PatchException(
                    "X-Lite Horizon theme palette provider does not return an object: $provider",
                )
            }

            val constructorReference = resolvePaletteConstructorReference(paletteDescriptor)
            val cacheFields = provider.resolvePaletteCacheFields(paletteDescriptor)
            val cacheFieldsByKind =
                mapOf(
                    PaletteKind.LIGHTS_OUT to cacheFields[0],
                    PaletteKind.DIM to cacheFields[1],
                    PaletteKind.STANDARD to cacheFields[2],
                )
            val factories =
                PaletteKind.values().map { kind ->
                    resolveFactory(kind, cacheFieldsByKind.getValue(kind), paletteDescriptor)
                }

            if (factories.map(ResolvedFactory::method).distinct().size != EXPECTED_FACTORY_COUNT) {
                throw PatchException(
                    "Expected $EXPECTED_FACTORY_COUNT distinct X-Lite palette factory methods, found " +
                        factories.joinToString { it.method.toString() },
                )
            }

            factories.forEach { factory ->
                factory.method.injectDynamicPalette(
                    index = factory.paletteAllocationIndex,
                    kind = factory.kind,
                    paletteDescriptor = paletteDescriptor,
                    constructorReference = constructorReference,
                )
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

private fun MutableMethod.resolvePaletteCacheFields(paletteDescriptor: String): List<FieldReference> {
    val cacheFields =
        instructions
            .windowed(size = 4, step = 1, partialWindows = false)
            .mapNotNull { sequence ->
                if (sequence[0].opcode != Opcode.SGET_OBJECT ||
                    sequence[1].opcode != Opcode.INVOKE_VIRTUAL ||
                    sequence[2].opcode != Opcode.MOVE_RESULT_OBJECT ||
                    sequence[3].opcode != Opcode.CHECK_CAST
                ) {
                    return@mapNotNull null
                }
                val castType = sequence[3].getReference<TypeReference>()?.type
                if (castType != paletteDescriptor) return@mapNotNull null
                sequence[0].getReference<FieldReference>()
            }
            .distinctBy(FieldReference::toString)

    if (cacheFields.size != 3) {
        throw PatchException(
            "Expected three X-Lite Horizon palette cache fields in $this, found " +
                "${cacheFields.size}: ${cacheFields.joinToString()}",
        )
    }
    if (cacheFields.map { it.definingClass }.distinct().size != 1) {
        throw PatchException(
            "X-Lite Horizon palette caches must share one holder: ${cacheFields.joinToString()}",
        )
    }
    return cacheFields
}

context(context: BytecodePatchContext)
private fun resolveFactory(
    kind: PaletteKind,
    cacheField: FieldReference,
    paletteDescriptor: String,
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
    if (paletteAllocations.size != 1) {
        throw PatchException(
            "Expected one X-Lite $kind palette allocation branch, found ${paletteAllocations.size}: " +
                invoke,
        )
    }
    return ResolvedFactory(kind, invoke, paletteAllocations.single().index)
}

context(context: BytecodePatchContext)
private fun patchDynamicAccentPalettes() {
    val providerMatches = XLiteDynamicColorPaletteProviderFingerprint.matchAll()
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
    val standardAccentFields = standardConstructor.staticColorFields().take(ACCENT_TONE_COUNT)
    val darkAccentFields = darkConstructor.staticColorFields().take(ACCENT_TONE_COUNT)

    if (standardAccentFields.size != ACCENT_TONE_COUNT ||
        standardAccentFields.distinctBy(FieldReference::toString).size != ACCENT_TONE_COUNT
    ) {
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
    if (darkAccentFields.map(FieldReference::toString) !=
        standardAccentFields.asReversed().map(FieldReference::toString)
    ) {
        throw PatchException(
            "X-Lite dark accent ramp is not the reverse of the standard ramp: " +
                darkAccentFields.joinToString(),
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

context(context: BytecodePatchContext)
private fun patchInlineActionTints() {
    val entryMatches =
        XLiteInlineActionEntryRendererFingerprint.matchAllOrNull().orEmpty() +
            XLiteInlineActionEntryRendererWithModeFingerprint.matchAllOrNull().orEmpty()
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
            reference.parameterTypes.firstOrNull() == POST_ACTION_TYPE_DESCRIPTOR &&
                reference.returnType == "V"
        } ?: throw PatchException("X-Lite inline action tint renderer call not found: $entryMethod")
    val tintMethod = tintReference.resolveMutableMethod("X-Lite inline action tint renderer")
    val unfavoriteIndex =
        tintMethod.instructions.indexOfFirst { instruction ->
            instruction.getReference<FieldReference>()?.let { field ->
                field.definingClass == POST_ACTION_TYPE_DESCRIPTOR && field.name == "Unfavorite"
            } == true
        }
    if (unfavoriteIndex < 0) {
        throw PatchException("X-Lite activated-like branch not found: $tintMethod")
    }
    val likeComposableConstructor =
        tintMethod.instructions.drop(unfavoriteIndex + 1)
            .mapNotNull { instruction -> instruction.getReference<MethodReference>() }
            .firstOrNull { reference ->
                reference.name == "<init>" &&
                    reference.parameterTypes == listOf(
                        "Ljava/lang/String;",
                        "Z",
                        "Ljava/lang/Long;",
                        "F",
                        "Landroidx/compose/runtime/v2;",
                    )
            } ?: throw PatchException("X-Lite like icon composable not found: $tintMethod")

    entryMethod.addInstructions(
        0,
        """
        invoke-static/range {p2 .. p3}, $DYNAMIC_COLOR_PALETTE_DESCRIPTOR->inlineActionTint(J)J
        move-result-wide p2
        """.trimIndent(),
    )
    tintMethod.injectActivatedLikeTint(unfavoriteIndex)
    patchLikeIconComposable(likeComposableConstructor.definingClass)
}

context(context: BytecodePatchContext)
private fun MethodReference.resolveMutableMethod(label: String): MutableMethod =
    context.mutableClassDefBy(definingClass)
        .methods.singleOrNull { method ->
            method.name == name &&
                method.parameterTypes == parameterTypes &&
                method.returnType == returnType
        } ?: throw PatchException("$label not found: $this")

private fun MutableMethod.injectActivatedLikeTint(unfavoriteIndex: Int) {
    val colorLoad =
        instructions.withIndex()
            .drop(unfavoriteIndex + 1)
            .take(20)
            .firstOrNull { (_, instruction) -> instruction.opcode == Opcode.SGET_WIDE }
            ?: throw PatchException("X-Lite activated-like tint load not found: $this")
    val colorRegister = (colorLoad.value as? OneRegisterInstruction)?.registerA
        ?: throw PatchException("X-Lite activated-like tint is not a one-register wide load: $this")
    addInstructions(
        colorLoad.index + 1,
        """
        invoke-static/range {v$colorRegister .. v${colorRegister + 1}}, $DYNAMIC_COLOR_PALETTE_DESCRIPTOR->inlineActionActiveTint(J)J
        move-result-wide v$colorRegister
        """.trimIndent(),
    )
}

context(context: BytecodePatchContext)
private fun patchLikeIconComposable(descriptor: String) {
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
    lottieReference.resolveMutableMethod("X-Lite Lottie renderer").injectLottieFallbackTint()
}

private fun MutableMethod.injectLottieFallbackTint() {
    val colorLoads =
        instructions.withIndex().filter { (_, instruction) ->
            if (instruction.opcode != Opcode.SGET_WIDE) return@filter false
            instruction.getReference<FieldReference>()?.definingClass?.startsWith("Lcom/x/compose/core/") == true
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
        if (colorRegister !in 1..14) {
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
