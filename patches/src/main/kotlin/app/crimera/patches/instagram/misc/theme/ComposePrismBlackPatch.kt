/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.patches.instagram.utils.methodReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.util.findInstructionIndicesReversed
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference

// GRAY_1600 is compiled into Compose, so runtime themes override only its background uses.
private const val PRISM_COLORS_V2_CLASS = "Lcom/instagram/compose/core/theme/BasePrismColorsV2;"
private const val GRAY_1600_FIELD_NAME = "GRAY_1600"
private const val GRAY_0100_FIELD_NAME = "GRAY_0100"
private const val MATERIAL_YOU_THEME_CLASS =
    "Lapp/morphe/extension/instagram/theme/MaterialYouTheme;"
private const val COMPOSE_PRISM_COLOR_BRIDGE_NAME = "applyComposePrismColor"
private const val COMPOSE_PRISM_REFRESH_METHOD_NAME = "pikoRefreshPrismPalette"
private const val COMPOSE_SEARCH_ROW_BACKGROUND_RESOLVER =
    "$MATERIAL_YOU_THEME_CLASS->resolveComposeSearchRowBackground(J)J"
private const val PURE_BLACK_ARGB = 0xff000000L
private const val EXPECTED_COMPOSE_PRISM_PALETTE_HOLDERS = 2
private const val SEARCH_ROW_SEMANTICS_ID = "search_row"

internal object ComposePrismBlackFingerprint : Fingerprint(
    definingClass = PRISM_COLORS_V2_CLASS,
    name = "<clinit>",
    filters = listOf(
        opcode(Opcode.CONST_WIDE),
        // Packing shift of the color.
        opcode(Opcode.SHL_LONG_2ADDR, InstructionLocation.MatchAfterImmediately()),
        fieldAccess(
            definingClass = PRISM_COLORS_V2_CLASS,
            name = GRAY_1600_FIELD_NAME,
            opcode = Opcode.SPUT_WIDE,
            location = InstructionLocation.MatchAfterImmediately(),
        ),
    ),
)

internal object ComposeSearchRowFingerprint : Fingerprint(
    returnType = "V",
    filters = listOf(
        // Palette surface color.
        fieldAccess(type = "J", opcode = Opcode.IGET_WIDE),
        string(SEARCH_ROW_SEMANTICS_ID),
        // Alternate surface color.
        methodCall(returnType = "J", opcode = Opcode.INVOKE_STATIC),
        opcode(Opcode.MOVE_RESULT_WIDE, InstructionLocation.MatchAfterImmediately()),
    ),
)

/**
 * Class initializer of a cached Compose prism palette, which is constructed from the prism colors.
 */
internal object ComposePrismPaletteHolderFingerprint : Fingerprint(
    name = "<clinit>",
    returnType = "V",
    parameters = emptyList(),
    filters = listOf(
        fieldAccess(definingClass = PRISM_COLORS_V2_CLASS, name = GRAY_1600_FIELD_NAME, opcode = Opcode.SGET_WIDE),
        // Palette constructor.
        methodCall(name = "<init>", opcode = Opcode.INVOKE_DIRECT_RANGE),
        // Cached palette.
        fieldAccess(definingClass = "this", opcode = Opcode.SPUT_OBJECT),
    ),
)

internal fun composePrismBlackLiteral(
    legacyAmoled: Boolean,
    nativeArgb: Long,
): Long = if (legacyAmoled) PURE_BLACK_ARGB else nativeArgb

internal fun composePrismBlackFieldAccessFlags(accessFlags: Int): Int =
    accessFlags and AccessFlags.FINAL.value.inv()

internal data class ComposePrismPaletteHolder(
    val cachedPaletteField: FieldReference,
    val refreshMethod: MethodReference,
)

internal data class ComposePrismPaletteRuntime(
    val backgroundFields: List<FieldReference>,
    val holders: List<ComposePrismPaletteHolder>,
)

internal fun createComposePrismColorBridge(
    owner: String,
    paletteRuntime: ComposePrismPaletteRuntime,
): MutableMethod {
    val implementation =
        MethodImplementationBuilder(7).apply {
            addInstruction(
                BuilderInstruction21t(
                    Opcode.IF_EQZ,
                    6,
                    getLabel("piko_compose_prism_write"),
                ),
            )
            paletteRuntime.holders.forEach { holder ->
                addInstruction("invoke-static {}, ${holder.refreshMethod}".toInstruction())
            }
            addLabel("piko_compose_prism_write")
            addInstruction(
                BuilderInstruction21t(
                    Opcode.IF_EQZ,
                    5,
                    getLabel("piko_compose_prism_return"),
                ),
            )
            addInstruction("int-to-long v0, v5".toInstruction())
            addInstruction("const/16 v3, 0x20".toInstruction())
            addInstruction("shl-long/2addr v0, v3".toInstruction())
            paletteRuntime.holders.forEach { holder ->
                addInstruction(
                    "sget-object v2, ${holder.cachedPaletteField}".toInstruction(),
                )
                paletteRuntime.backgroundFields.forEach { backgroundField ->
                    addInstruction(
                        "iput-wide v0, v2, $backgroundField".toInstruction(),
                    )
                }
            }
            addLabel("piko_compose_prism_return")
            addInstruction("return-void".toInstruction())
        }.methodImplementation
    return MutableMethod(
        ImmutableMethod(
            owner,
            COMPOSE_PRISM_COLOR_BRIDGE_NAME,
            listOf(
                ImmutableMethodParameter("I", emptySet(), null),
                ImmutableMethodParameter("Z", emptySet(), null),
            ),
            "V",
            AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
            emptySet(),
            emptySet(),
            implementation,
        ),
    )
}

context(patchContext: BytecodePatchContext)
internal fun installComposePrismBlackRuntime(legacyAmoled: Boolean) {
    val method = ComposePrismBlackFingerprint.method
    val constWideMatch = ComposePrismBlackFingerprint.instructionMatches[0]
    val register = constWideMatch.getInstruction<OneRegisterInstruction>().registerA
    val nativeArgb = constWideMatch.getInstruction<WideLiteralInstruction>().wideLiteral
    val field = ComposePrismBlackFingerprint.instructionMatches.last().fieldReference
    val legacyLiteral = composePrismBlackLiteral(legacyAmoled, nativeArgb)
    if (legacyLiteral != nativeArgb) {
        method.replaceInstruction(
            constWideMatch.index,
            "const-wide v$register, ${wideLiteral(legacyLiteral)}",
        )
    }
    val paletteRuntime = installComposePrismPaletteRuntime(field)
    installComposeSearchRowBackgroundOverride()

    val extensionClass = patchContext.mutableClassDefBy(MATERIAL_YOU_THEME_CLASS)
    val bridgeCandidates =
        extensionClass.methods.filter {
            it.name == COMPOSE_PRISM_COLOR_BRIDGE_NAME &&
                it.parameterTypes.map(CharSequence::toString) == listOf("I", "Z") &&
                it.returnType == "V"
        }
    if (bridgeCandidates.size != 1) {
        throw PatchException(
            "Expected one MaterialYouTheme compose prism bridge, found " +
                bridgeCandidates.size,
        )
    }
    val bridgeStub = bridgeCandidates.single()
    if (
        !AccessFlags.PRIVATE.isSet(bridgeStub.accessFlags) ||
        !AccessFlags.STATIC.isSet(bridgeStub.accessFlags) ||
        !AccessFlags.NATIVE.isSet(bridgeStub.accessFlags)
    ) {
        throw PatchException("MaterialYouTheme compose prism bridge has an invalid signature")
    }
    extensionClass.methods.remove(bridgeStub)
    extensionClass.methods.add(
        createComposePrismColorBridge(
            owner = MATERIAL_YOU_THEME_CLASS,
            paletteRuntime = paletteRuntime,
        ),
    )
}

context(patchContext: BytecodePatchContext)
private fun installComposeSearchRowBackgroundOverride() {
    ComposeSearchRowFingerprint.let {
        val paletteRead = it.instructionMatches.first()
        val alternateResult = it.instructionMatches.last()
        val register = paletteRead.getInstruction<OneRegisterInstruction>().registerA
        if (alternateResult.getInstruction<OneRegisterInstruction>().registerA != register) {
            throw PatchException("Compose search-row surface colors use different registers")
        }
        listOf(alternateResult.index, paletteRead.index).forEach { index ->
            it.method.addInstructions(index + 1, composeSearchRowOverrideInstructions(register))
        }
    }
}

internal fun composeSearchRowOverrideInstructions(register: Int): String {
    if (register !in 0..14) {
        throw PatchException(
            "Compose search-row surface requires a 4-bit wide register, found v$register",
        )
    }
    return """
        invoke-static {v$register, v${register + 1}}, $COMPOSE_SEARCH_ROW_BACKGROUND_RESOLVER
        move-result-wide v$register
        """.trimIndent()
}

context(patchContext: BytecodePatchContext)
private fun installComposePrismPaletteRuntime(
    prismField: FieldReference,
): ComposePrismPaletteRuntime {
    // Other Compose caches also read GRAY_1600 without constructing a Prism palette.
    val holderMatches = ComposePrismPaletteHolderFingerprint.matchAll().filter { match ->
        val constructor = match.instructionMatches[1].methodReference
        constructor.parameterTypes.isNotEmpty() && constructor.parameterTypes.all { it.toString() == "J" } &&
            match.instructionMatches[2].fieldReference.type == constructor.definingClass
    }
    if (holderMatches.size != EXPECTED_COMPOSE_PRISM_PALETTE_HOLDERS) {
        throw PatchException(
            "Expected $EXPECTED_COMPOSE_PRISM_PALETTE_HOLDERS cached Compose prism " +
                "palette holders, found ${holderMatches.size}",
        )
    }
    val rootBackgroundParameterOrdinal = composePrismRootBackgroundParameterOrdinal(holderMatches)
    val holderClasses = holderMatches.map { it.originalClassDef }

    val holders = holderClasses.map { classDef ->
        val holderClass = patchContext.mutableClassDefBy(classDef)
        if (
            holderClass.methods.any {
                it.name == COMPOSE_PRISM_REFRESH_METHOD_NAME
            }
        ) {
            throw PatchException(
                "Compose prism palette refresh already exists in ${holderClass.type}",
            )
        }
        val clinit =
            holderClass.methods.single {
                it.name == "<clinit>" &&
                    it.parameterTypes.isEmpty() &&
                    it.returnType == "V"
            }
        val holderWrites =
            clinit.instructions
                .filter { instruction ->
                    instruction.opcode == Opcode.SPUT_OBJECT &&
                        ((instruction as? ReferenceInstruction)?.reference as? FieldReference)
                            ?.definingClass == holderClass.type
                }
                .map { instruction ->
                    (instruction as ReferenceInstruction).reference as FieldReference
                }
                .distinct()
        if (holderWrites.size != 1) {
            throw PatchException(
                "Expected one cached Compose prism palette field in ${holderClass.type}",
            )
        }
        val holderFieldReference = holderWrites.single()
        val holderField =
            holderClass.fields.singleOrNull {
                it.name == holderFieldReference.name &&
                    it.type == holderFieldReference.type
            } ?: throw PatchException(
                "Cached Compose prism palette field is missing in ${holderClass.type}",
            )
        if (
            !AccessFlags.PUBLIC.isSet(holderField.accessFlags) ||
            !AccessFlags.STATIC.isSet(holderField.accessFlags) ||
            !AccessFlags.FINAL.isSet(holderField.accessFlags)
        ) {
            throw PatchException(
                "Cached Compose prism palette field has an invalid shape in " +
                    holderClass.type,
            )
        }
        holderField.accessFlags =
            composePrismBlackFieldAccessFlags(holderField.accessFlags)

        val refreshMethod =
            MutableMethod(clinit).apply {
                name = COMPOSE_PRISM_REFRESH_METHOD_NAME
                accessFlags =
                    AccessFlags.PUBLIC.value or
                    AccessFlags.STATIC.value
            }
        holderClass.methods.add(refreshMethod)
        ComposePrismPaletteHolder(
            cachedPaletteField = holderFieldReference,
            refreshMethod =
                ImmutableMethodReference(
                    holderClass.type,
                    COMPOSE_PRISM_REFRESH_METHOD_NAME,
                    emptyList(),
                    "V",
                ),
        )
    }

    val paletteTypes = holders.map { it.cachedPaletteField.type }.distinct()
    if (paletteTypes.size != 1) {
        throw PatchException(
            "Cached Compose prism palettes use different types: $paletteTypes",
        )
    }
    val paletteClass = patchContext.mutableClassDefBy(paletteTypes.single())
    val paletteConstructors =
        paletteClass.methods.filter { method ->
            method.name == "<init>" &&
                method.parameterTypes.isNotEmpty() &&
                method.parameterTypes.all { it.toString() == "J" }
        }
    val primaryBackgroundField =
        paletteConstructors
            .mapNotNull { method -> composePrismBackgroundField(method, parameterOrdinal = 0) }
            .distinct()
            .singleOrNull()
            ?: throw PatchException(
                "Expected one cached Compose prism primary background field",
            )
    val bdsBackgroundField =
        paletteConstructors
            .mapNotNull { method ->
                composePrismBackgroundField(method, parameterOrdinal = rootBackgroundParameterOrdinal)
            }
            .distinct()
            .singleOrNull()
            ?: throw PatchException(
                "Expected one cached Compose BDS root background field",
            )
    val backgroundFields =
        listOf(primaryBackgroundField, bdsBackgroundField).distinct()
    if (backgroundFields.size != 2) {
        throw PatchException(
            "Compose primary and BDS root backgrounds must use different fields",
        )
    }
    backgroundFields.forEach { backgroundField ->
        val definition =
            paletteClass.fields.singleOrNull { field ->
                field.name == backgroundField.name && field.type == backgroundField.type
            } ?: throw PatchException(
                "Cached Compose background field is missing",
            )
        if (
            !AccessFlags.PUBLIC.isSet(definition.accessFlags) ||
            AccessFlags.STATIC.isSet(definition.accessFlags) ||
            !AccessFlags.FINAL.isSet(definition.accessFlags)
        ) {
            throw PatchException(
                "Cached Compose background field has an invalid shape",
            )
        }
        definition.accessFlags =
            composePrismBlackFieldAccessFlags(definition.accessFlags)
    }

    return ComposePrismPaletteRuntime(
        backgroundFields = backgroundFields,
        holders = holders,
    )
}

private data class ComposePrismHolderColorOrdinals(
    val gray1600: Set<Int>,
    val gray0100: Set<Int>,
)

/**
 * Constructor parameter of the root background, which the dark palette sets from GRAY_1600 and
 * the light palette from GRAY_0100.
 */
private fun composePrismRootBackgroundParameterOrdinal(holderMatches: List<Match>): Int {
    val holderSources =
        holderMatches.map { match ->
            val method = match.originalMethod
            val constructorCall = match.instructionMatches[1]
            val constructorParameterCount = constructorCall.methodReference.parameterTypes.size
            val firstParameterRegister =
                constructorCall.getInstruction<RegisterRangeInstruction>().startRegister + 1

            fun sourceOrdinals(fieldName: String): Set<Int> =
                method.findInstructionIndicesReversed(
                    fieldAccess(definingClass = PRISM_COLORS_V2_CLASS, name = fieldName, opcode = Opcode.SGET_WIDE),
                ).filter { it < constructorCall.index }.mapNotNull { index ->
                    val offset =
                        method.getInstruction<OneRegisterInstruction>(index).registerA - firstParameterRegister
                    (offset / 2).takeIf { offset >= 0 && offset % 2 == 0 && it < constructorParameterCount }
                }.toSet()

            ComposePrismHolderColorOrdinals(
                gray1600 = sourceOrdinals(GRAY_1600_FIELD_NAME),
                gray0100 = sourceOrdinals(GRAY_0100_FIELD_NAME),
            )
        }
    val darkHolder =
        holderSources.singleOrNull { holder -> 0 in holder.gray1600 }
            ?: throw PatchException(
                "Expected one dark Compose prism palette initializer",
            )
    val lightHolder =
        holderSources.single { holder -> holder !== darkHolder }
    val candidates = darkHolder.gray1600.intersect(lightHolder.gray0100)
    return candidates.singleOrNull()
        ?: throw PatchException(
            "Expected one Compose prism root background parameter, found $candidates",
        )
}

/**
 * Field the palette constructor stores the wide color parameter [parameterOrdinal] in,
 * or null if the constructor does not store it in exactly one field.
 */
private fun composePrismBackgroundField(
    constructor: Method,
    parameterOrdinal: Int,
): FieldReference? {
    if (
        constructor.implementation == null ||
        constructor.parameterTypes.getOrNull(parameterOrdinal)?.toString() != "J"
    ) {
        return null
    }
    val thisRegister = constructor.p0Register
    val colorRegister =
        thisRegister + 1 +
            constructor.parameterTypes.take(parameterOrdinal).sumOf { type ->
                if (type.toString() == "J" || type.toString() == "D") 2 else 1
            }
    return constructor.findInstructionIndicesReversed(
        fieldAccess(definingClass = constructor.definingClass, type = "J", opcode = Opcode.IPUT_WIDE),
    ).filter { index ->
        val store = constructor.getInstruction<TwoRegisterInstruction>(index)
        if (store.registerB != thisRegister) return@filter false
        if (store.registerA == colorRegister) return@filter true
        if (index == 0) return@filter false
        val move = constructor.getInstruction(index - 1)
        move.opcode in setOf(Opcode.MOVE_WIDE, Opcode.MOVE_WIDE_FROM16, Opcode.MOVE_WIDE_16) &&
            (move as TwoRegisterInstruction).registerA == store.registerA &&
            move.registerB == colorRegister
    }.map { constructor.getInstruction(it).getReference<FieldReference>()!! }
        .distinct()
        .singleOrNull()
}

private fun wideLiteral(value: Long): String =
    "0x${value.toULong().toString(16)}L"
