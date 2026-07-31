/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.theme

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
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

// Allow only the single packing instruction between the literal and GRAY_1600 write.
internal object ComposePrismBlackFingerprint : Fingerprint(
    definingClass = PRISM_COLORS_V2_CLASS,
    name = "<clinit>",
    filters = listOf(
        opcode(Opcode.CONST_WIDE),
        fieldAccess(
            definingClass = PRISM_COLORS_V2_CLASS,
            name = GRAY_1600_FIELD_NAME,
            opcode = Opcode.SPUT_WIDE,
            location = InstructionLocation.MatchAfterWithin(1),
        ),
    ),
)

internal object ComposeSearchRowFingerprint : Fingerprint(
    strings = listOf(SEARCH_ROW_SEMANTICS_ID),
    returnType = "V",
    filters = listOf(opcode(Opcode.IGET_WIDE)),
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
    val storeMatch = ComposePrismBlackFingerprint.instructionMatches[1]
    val register =
        (constWideMatch.instruction as? OneRegisterInstruction)?.registerA
            ?: throw PatchException("GRAY_1600 source is not a one-register const-wide")
    val nativeArgb =
        (constWideMatch.instruction as? WideLiteralInstruction)?.wideLiteral
            ?: throw PatchException("GRAY_1600 source does not contain a wide literal")
    if (storeMatch.index != constWideMatch.index + 2) {
        throw PatchException("GRAY_1600 source is not followed by one packing instruction")
    }
    val shiftInstruction =
        method.instructions[constWideMatch.index + 1] as? TwoRegisterInstruction
            ?: throw PatchException("GRAY_1600 packing instruction does not use two registers")
    if (
        shiftInstruction.opcode != Opcode.SHL_LONG_2ADDR ||
        shiftInstruction.registerA != register
    ) {
        throw PatchException("GRAY_1600 source is not packed with shl-long/2addr")
    }
    val shiftAmount =
        method.instructions
            .subList(0, constWideMatch.index)
            .asReversed()
            .firstOrNull { instruction ->
                instruction is OneRegisterInstruction &&
                    instruction is NarrowLiteralInstruction &&
                    instruction.registerA == shiftInstruction.registerB
            } as? NarrowLiteralInstruction
    if (shiftAmount?.narrowLiteral != 32) {
        throw PatchException("GRAY_1600 packing shift is not 32 bits")
    }

    val field =
        ((storeMatch.instruction as? ReferenceInstruction)?.reference as? FieldReference)
            ?: throw PatchException("GRAY_1600 store does not reference a field")
    if (
        field.definingClass != PRISM_COLORS_V2_CLASS ||
        field.name != GRAY_1600_FIELD_NAME ||
        field.type != "J"
    ) {
        throw PatchException("GRAY_1600 field reference does not match the expected shape")
    }
    val fieldDefinition =
        ComposePrismBlackFingerprint.classDef.fields.singleOrNull {
            it.definingClass == field.definingClass &&
                it.name == field.name &&
                it.type == field.type
        } ?: throw PatchException("Expected one GRAY_1600 field definition")
    if (
        !AccessFlags.PUBLIC.isSet(fieldDefinition.accessFlags) ||
        !AccessFlags.STATIC.isSet(fieldDefinition.accessFlags) ||
        !AccessFlags.FINAL.isSet(fieldDefinition.accessFlags)
    ) {
        throw PatchException("GRAY_1600 must remain public static final")
    }
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
    val method = ComposeSearchRowFingerprint.method
    composeSearchRowSurfaceWrites(method.instructions)
        .sortedByDescending { it.index }
        .forEach { write ->
            method.addInstructions(
                write.index + 1,
                composeSearchRowOverrideInstructions(write.register),
            )
        }
}

internal data class ComposeSearchRowSurfaceWrite(
    val index: Int,
    val register: Int,
)

internal fun composeSearchRowSurfaceWrites(
    instructions: List<Instruction>,
): List<ComposeSearchRowSurfaceWrite> {
    val paletteWrites =
        instructions.withIndex().filter { (_, instruction) ->
            instruction.opcode == Opcode.IGET_WIDE &&
                ((instruction as? ReferenceInstruction)?.reference as? FieldReference)
                    ?.type == "J"
        }
    if (paletteWrites.size != 1) {
        throw PatchException(
            "Expected one Compose search-row palette surface read, found ${paletteWrites.size}",
        )
    }
    val paletteWrite = paletteWrites.single()
    val register =
        (paletteWrite.value as? OneRegisterInstruction)?.registerA
            ?: throw PatchException(
                "Compose search-row palette surface read has no destination register",
            )

    val alternateWrites =
        instructions.withIndex().filter { (index, instruction) ->
            if (
                index == 0 ||
                instruction.opcode != Opcode.MOVE_RESULT_WIDE ||
                (instruction as? OneRegisterInstruction)?.registerA != register
            ) {
                return@filter false
            }
            val source = instructions[index - 1]
            source.opcode == Opcode.INVOKE_STATIC &&
                ((source as? ReferenceInstruction)?.reference as? MethodReference)
                    ?.returnType == "J"
        }
    if (alternateWrites.size != 1) {
        throw PatchException(
            "Expected one alternate Compose search-row surface result, found " +
                alternateWrites.size,
        )
    }

    return listOf(
        ComposeSearchRowSurfaceWrite(paletteWrite.index, register),
        ComposeSearchRowSurfaceWrite(alternateWrites.single().index, register),
    )
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
    val holderClasses = mutableListOf<com.android.tools.smali.dexlib2.iface.ClassDef>()
    patchContext.classDefForEach { classDef ->
        val clinit =
            classDef.methods.singleOrNull {
                it.name == "<clinit>" &&
                    it.parameterTypes.isEmpty() &&
                    it.returnType == "V"
            } ?: return@classDefForEach
        val readsPrismField =
            clinit.implementation?.instructions?.any { instruction ->
                instruction.opcode == Opcode.SGET_WIDE &&
                    (instruction as? ReferenceInstruction)?.reference == prismField
            } == true
        if (readsPrismField) holderClasses += classDef
    }
    if (holderClasses.size != EXPECTED_COMPOSE_PRISM_PALETTE_HOLDERS) {
        throw PatchException(
            "Expected $EXPECTED_COMPOSE_PRISM_PALETTE_HOLDERS cached Compose prism " +
                "palette holders, found ${holderClasses.size}",
        )
    }
    val holderInitializers =
        holderClasses.map { classDef ->
            classDef.methods
                .single {
                    it.name == "<clinit>" &&
                        it.parameterTypes.isEmpty() &&
                        it.returnType == "V"
                }.implementation
                ?.instructions
                ?.toList()
                ?: throw PatchException(
                    "Cached Compose prism palette initializer is missing in ${classDef.type}",
                )
        }

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
            .mapNotNull { method ->
                runCatching {
                    composePrismPrimaryBackgroundField(method)
                }.getOrNull()
            }
            .distinct()
            .singleOrNull()
            ?: throw PatchException(
                "Expected one cached Compose prism primary background field",
            )
    val rootBackgroundParameterOrdinal =
        composePrismRootBackgroundParameterOrdinal(
            paletteType = paletteClass.type,
            holderInitializers = holderInitializers,
        )
    val bdsBackgroundField =
        paletteConstructors
            .mapNotNull { method ->
                runCatching {
                    composePrismBackgroundField(
                        constructor = method,
                        parameterOrdinal = rootBackgroundParameterOrdinal,
                    )
                }.getOrNull()
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

internal fun composePrismRootBackgroundParameterOrdinal(
    paletteType: String,
    holderInitializers: List<List<Instruction>>,
): Int {
    if (holderInitializers.size != EXPECTED_COMPOSE_PRISM_PALETTE_HOLDERS) {
        throw PatchException(
            "Expected $EXPECTED_COMPOSE_PRISM_PALETTE_HOLDERS Compose prism palette " +
                "initializers, found ${holderInitializers.size}",
        )
    }
    val holderSources =
        holderInitializers.map { instructions ->
            val constructorCall =
                instructions.withIndex().singleOrNull { (_, instruction) ->
                    if (instruction.opcode != Opcode.INVOKE_DIRECT_RANGE) {
                        return@singleOrNull false
                    }
                    val reference =
                        (instruction as? ReferenceInstruction)?.reference as? MethodReference
                            ?: return@singleOrNull false
                    reference.definingClass == paletteType &&
                        reference.name == "<init>" &&
                        reference.returnType == "V" &&
                        reference.parameterTypes.isNotEmpty() &&
                        reference.parameterTypes.all { it.toString() == "J" }
                } ?: throw PatchException(
                    "Expected one cached Compose prism palette constructor call",
                )
            val constructorReference =
                (constructorCall.value as ReferenceInstruction).reference as MethodReference
            val range =
                constructorCall.value as? RegisterRangeInstruction
                    ?: throw PatchException(
                        "Cached Compose prism palette constructor is not a register range",
                    )
            val expectedRegisterCount = 1 + (constructorReference.parameterTypes.size * 2)
            if (range.registerCount != expectedRegisterCount) {
                throw PatchException(
                    "Cached Compose prism palette constructor register count is invalid",
                )
            }
            val firstParameterRegister = range.startRegister + 1

            fun sourceOrdinals(fieldName: String): Set<Int> =
                instructions
                    .take(constructorCall.index)
                    .mapNotNull { instruction ->
                        if (instruction.opcode != Opcode.SGET_WIDE) {
                            return@mapNotNull null
                        }
                        val field =
                            (instruction as? ReferenceInstruction)?.reference as? FieldReference
                                ?: return@mapNotNull null
                        if (
                            field.definingClass != PRISM_COLORS_V2_CLASS ||
                            field.name != fieldName ||
                            field.type != "J"
                        ) {
                            return@mapNotNull null
                        }
                        val register =
                            (instruction as? OneRegisterInstruction)?.registerA
                                ?: return@mapNotNull null
                        val offset = register - firstParameterRegister
                        if (
                            offset < 0 ||
                            offset % 2 != 0 ||
                            offset / 2 !in constructorReference.parameterTypes.indices
                        ) {
                            return@mapNotNull null
                        }
                        offset / 2
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

internal fun composePrismPrimaryBackgroundField(
    constructor: com.android.tools.smali.dexlib2.iface.Method,
): FieldReference = composePrismBackgroundField(constructor, parameterOrdinal = 0)

internal fun composePrismBackgroundField(
    constructor: com.android.tools.smali.dexlib2.iface.Method,
    parameterOrdinal: Int,
): FieldReference {
    if (
        constructor.name != "<init>" ||
        parameterOrdinal !in constructor.parameterTypes.indices ||
        constructor.parameterTypes[parameterOrdinal].toString() != "J"
    ) {
        throw PatchException(
            "Compose prism palette constructor parameter $parameterOrdinal is not a wide color",
        )
    }
    val implementation =
        constructor.implementation
            ?: throw PatchException("Compose prism palette constructor has no implementation")
    val parameterRegisterCount =
        1 + constructor.parameterTypes.sumOf { type ->
            when (type.first()) {
                'J', 'D' -> 2
                else -> 1
            }
        }
    val thisRegister = implementation.registerCount - parameterRegisterCount
    val colorRegister =
        thisRegister +
            1 +
            constructor.parameterTypes
                .take(parameterOrdinal)
                .sumOf { type ->
                    when (type.first()) {
                        'J', 'D' -> 2
                        else -> 1
                    }
                }
    val instructions = implementation.instructions.toList()
    return instructions
        .withIndex()
        .filter { (index, instruction) ->
            if (instruction.opcode != Opcode.IPUT_WIDE) {
                return@filter false
            }
            val store = instruction as? TwoRegisterInstruction ?: return@filter false
            if (store.registerB != thisRegister) {
                return@filter false
            }
            if (store.registerA == colorRegister) {
                return@filter true
            }
            if (index == 0) {
                return@filter false
            }
            val move = instructions[index - 1] as? TwoRegisterInstruction ?: return@filter false
            instructions[index - 1].opcode in
                setOf(
                    Opcode.MOVE_WIDE,
                    Opcode.MOVE_WIDE_FROM16,
                    Opcode.MOVE_WIDE_16,
                ) &&
                move.registerA == store.registerA &&
                move.registerB == colorRegister
        }
        .mapNotNull { (_, instruction) ->
            (instruction as? ReferenceInstruction)?.reference as? FieldReference
        }
        .filter { field ->
            field.definingClass == constructor.definingClass &&
                field.type == "J"
        }
        .distinct()
        .singleOrNull()
        ?: throw PatchException(
            "Expected one Compose prism background constructor store for parameter " +
                parameterOrdinal,
        )
}

private fun wideLiteral(value: Long): String =
    "0x${value.toULong().toString(16)}L"
