/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.disableAutoScroll

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.Opcode

private object DisableAutoScrollFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf(
        "applicationManager", "releaseCompletable", "preferences", "twSystemClock",
        "launchTracker", "cold_start_launch_time_millis",
    ),
)

internal object HomeTimelineRestoreFingerprint : Fingerprint(
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("home_timeline_disable_home_scroll_position_restoration_on_cold_launch_enabled"),
)

private object HomeTimelineRenderFingerprint : Fingerprint(
    classFingerprint = HomeTimelineRestoreFingerprint,
    returnType = "V",
    filters = listOf(
        fieldAccess(opcode = Opcode.SGET_OBJECT, name = "RENDERING_STARTED"),
        methodCall(opcode = Opcode.INVOKE_VIRTUAL, parameters = listOf("I", "I", "Z"), returnType = "V"),
        fieldAccess(opcode = Opcode.SGET_OBJECT, name = "RENDERING_COMPLETED"),
    ),
)

// credits to @Ouxyl
@Suppress("unused")
val disableAutoScrollPatch =
    bytecodePatch(
        name = "Disable auto timeline scroll on launch",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val coldStart = DisableAutoScrollFingerprint.matchAll(1..1).single().classDef.methods.singleOrNull {
                it.returnType == "Z" && it.parameterTypes.isEmpty() &&
                    !AccessFlags.STATIC.isSet(it.accessFlags) && (it.implementation?.registerCount ?: 0) > 1
            } ?: throw PatchException("Could not uniquely resolve the home cold-start decision")
            // Preserve the original patch's shared home decision only while the setting is enabled.
            coldStart.addInstructionsWithLabels(0, """
                invoke-static {}, $PREF_DESCRIPTOR;->disableAutoTimelineScroll()Z
                move-result v0
                if-eqz v0, :native
                const/4 v0, 0x0
                return v0
            """.trimIndent(), ExternalLabel("native", coldStart.getInstruction(0)))

            val (scope, forYouType) = preserveTimelinePosition()
            HomeTimelineRenderFingerprint.matchAll(1..1).single().method.apply {
                val scrollIndex = instructions.withIndex().filter { (_, instruction) ->
                    instruction.opcode == Opcode.INVOKE_VIRTUAL &&
                        instruction.getReference<MethodReference>()?.let {
                            it.parameterTypes == listOf("I", "I", "Z") && it.returnType == "V"
                        } == true
                }.singleOrNull()?.index
                    ?: throw PatchException("Could not uniquely resolve automatic timeline navigation")

                val scrollRegisters = getInstruction(scrollIndex).registersUsed
                val zeroRegister = scrollRegisters.getOrNull(1)
                    ?: throw PatchException("Missing automatic timeline scroll arguments")
                val zero = getInstruction(scrollIndex - 1)
                val clear = getInstruction(scrollIndex + 1)
                val store = getInstruction(scrollIndex + 2)
                if (scrollRegisters.size != 4 || scrollRegisters[0] == zeroRegister ||
                    scrollRegisters.drop(1).any { it != zeroRegister } ||
                    zero.opcode != Opcode.CONST_4 || zero.registersUsed.singleOrNull() != zeroRegister ||
                    (zero as? NarrowLiteralInstruction)?.narrowLiteral != 0 ||
                    clear.opcode != Opcode.CONST_4 || (clear as? NarrowLiteralInstruction)?.narrowLiteral != 0 ||
                    store.opcode != Opcode.IPUT_OBJECT || store.registersUsed.size != 2 ||
                    clear.registersUsed.singleOrNull() != store.registersUsed[0]
                ) {
                    throw PatchException("Unexpected automatic timeline navigation or cleanup")
                }

                // Automatic refresh responses can request a jump independently of the cold-start decision.
                // A false setting result preserves the shared zero arguments; either path consumes the request.
                addInstructionsWithLabels(
                    scrollIndex,
                    """
                    invoke-virtual/range {p0 .. p0}, $scope
                    move-result v$zeroRegister
                    if-nez v$zeroRegister, :consume_navigation
                    """.trimIndent(),
                    ExternalLabel("consume_navigation", clear),
                )
                instructions.withIndex().filter { it.value.opcode == Opcode.RETURN_VOID }.map { it.index }
                    .reversed().forEach {
                        replaceInstruction(it, "invoke-virtual/range {p0 .. p0}, $definingClass->pikoRetryTimelinePosition()V")
                        addInstructions(it + 1, "return-void")
                    }
            }

            preserveTimelineCache(forYouType)
            preserveInitialTimelineLoad(forYouType)

            enableSettings("disableAutoTimelineScroll")
        }
    }

private object HomeRequestFactoryFingerprint : Fingerprint(
    strings = listOf("requestConfig", "urtCursorProvider", "home_timeline_send_seen_ids_ignore_network_state"),
)

private object TimelineResponseFingerprint : Fingerprint(
    strings = listOf("globalObjects", "responseObjects", "urt_replace_entry"),
)

private object TimelineInstructionParserFingerprint : Fingerprint(
    name = "<clinit>",
    strings = listOf("clearCache", "TimelineClearCache", "addEntries", "TimelineAddEntries"),
)

context(context: BytecodePatchContext)
internal fun preserveTimelineCache(forYouType: Int) {
    val response = TimelineResponseFingerprint.matchAll(1..1).single()
    val factory = HomeRequestFactoryFingerprint.matchAll(1..1).single().method
    val parser = TimelineInstructionParserFingerprint.matchAll(1..1).single().method.instructions.toList()
    val cacheName = parser.indexOfFirst { it.getReference<StringReference>()?.string == "clearCache" }
    val cacheClass = parser.take(cacheName).lastOrNull { it.opcode == Opcode.CONST_CLASS }
        ?.getReference<TypeReference>()?.type
        ?: throw PatchException("Could not resolve the clear-cache instruction type")
    if (response.method.instructions.count {
            it.opcode == Opcode.INSTANCE_OF && it.getReference<TypeReference>()?.type == cacheClass
        } != 1
    ) throw PatchException("Unexpected clear-cache instruction dispatch")

    val fieldName = "pikoHomeFetchType"
    val field = "${response.classDef.type}->$fieldName:I"
    if (response.classDef.fields.any { it.name == fieldName }) {
        throw PatchException("Home request type field already exists")
    }

    val factoryCode = factory.instructions.toList()
    val processorRead = factoryCode.singleOrNull {
        it.opcode == Opcode.IGET_OBJECT && it.getReference<FieldReference>()?.type == response.classDef.type
    } ?: throw PatchException("Could not uniquely resolve the home response processor")
    val requestRegister = processorRead.registersUsed[1]
    val fetchIndex = factoryCode.withIndex().filter { (index, instruction) ->
        instruction.opcode == Opcode.IGET &&
            instruction.getReference<FieldReference>()?.definingClass == factory.parameterTypes.getOrNull(1) &&
            factoryCode.getOrNull(index + 1)?.let {
                it.opcode == Opcode.CONST_4 && (it as? NarrowLiteralInstruction)?.narrowLiteral == 2
            } == true && factoryCode.getOrNull(index + 2)?.opcode == Opcode.IF_NE
    }.singleOrNull()?.index ?: throw PatchException("Could not uniquely resolve the home fetch type")
    val fetchRegister = factoryCode[fetchIndex].registersUsed[0]
    val temporary = factoryCode[fetchIndex + 1].registersUsed.single()
    val processorRegister = processorRead.registersUsed[0]
    val configRegister = factoryCode[fetchIndex].registersUsed[1]
    val identifierIndex = factoryCode.indexOfFirst { it.getReference<StringReference>()?.string == "timelineIdentifier" }
    val identifierRead = factoryCode.getOrNull(identifierIndex - 1)
    val identifier = identifierRead?.getReference<FieldReference>()
    if (identifierRead?.opcode != Opcode.IGET_OBJECT || identifier == null || identifier.definingClass != factory.parameterTypes.getOrNull(1) ||
        identifierRead.registersUsed.getOrNull(1) != configRegister
    ) throw PatchException("Could not resolve the home timeline identifier")
    val requestScope = factoryCode.filter { it.opcode == Opcode.IGET_OBJECT }
        .mapNotNull { it.getReference<FieldReference>() }.singleOrNull { it.definingClass == identifier.type }
        ?: throw PatchException("Could not uniquely resolve the request timeline scope")
    val requestType = factoryCode.filter { it.opcode == Opcode.IGET }
        .mapNotNull { it.getReference<FieldReference>() }.singleOrNull { it.definingClass == requestScope.type }
        ?: throw PatchException("Could not uniquely resolve the request timeline type")
    if (setOf(requestRegister, fetchRegister, temporary).size != 3 ||
        listOf(requestRegister, fetchRegister, temporary).any { it !in 0..15 } ||
        processorRegister !in 0..15 || processorRegister in listOf(fetchRegister, temporary) ||
        configRegister !in 0..15 || configRegister == temporary ||
        factoryCode.last().opcode != Opcode.RETURN_OBJECT ||
        factoryCode.last().registersUsed.singleOrNull() != requestRegister ||
        factoryCode.subList(factoryCode.indexOf(processorRead) + 1, fetchIndex + 1).any {
            it.opcode.setsRegister() && it.registersUsed.firstOrNull() in listOf(requestRegister, processorRegister)
        }
    ) throw PatchException("Unexpected home request registers")

    val method = response.method
    val code = method.instructions.toList()
    val copyIndex = code.withIndex().filter { (_, instruction) ->
        instruction.getReference<MethodReference>()?.toString() ==
            "Ljava/util/ArrayList;->addAll(Ljava/util/Collection;)Z"
    }.singleOrNull()?.index ?: throw PatchException("Could not uniquely resolve the mutable instruction list")
    val copyRegisters = code[copyIndex].registersUsed
    if (copyRegisters.size != 2 || code.getOrNull(copyIndex + 1)?.opcode == Opcode.MOVE_RESULT) {
        throw PatchException("Unexpected instruction-list copy")
    }
    val listRegister = copyRegisters[0]
    // Use only registers overwritten before their first read, on the straight-line
    // path after the copy. Existing loop targets retain their original instructions.
    val straightLine = code.drop(copyIndex + 1).takeWhile {
        it !is OffsetInstruction && it.opcode.canContinue()
    }
    val scratch = (0..15).filter { register ->
        register != listRegister && straightLine.firstOrNull { register in it.registersUsed }?.let {
            it.opcode.setsRegister() && it.opcode != Opcode.CHECK_CAST &&
                it.registersUsed.first() == register && register !in it.registersUsed.drop(1)
        } == true
    }.take(2)
    if (scratch.size != 2 || listRegister !in 0..15) {
        throw PatchException("No safe registers for preserving the timeline cache")
    }

    response.classDef.fields.add(
        ImmutableField(response.classDef.type, fieldName, "I", AccessFlags.PUBLIC.value, null, emptySet(), emptySet()).toMutable(),
    )
    factory.addInstructionsWithLabels(
        fetchIndex + 1,
        """
        iget-object v$temporary, v$configRegister, $identifier
        iget-object v$temporary, v$temporary, $requestScope
        iget v$temporary, v$temporary, $requestType
        add-int/lit8 v$temporary, v$temporary, -$forYouType
        if-nez v$temporary, :native
        iput v$fetchRegister, v$processorRegister, $field
        """.trimIndent(),
        ExternalLabel("native", factoryCode[fetchIndex + 1]),
    )
    method.addInstructions(
        copyIndex + 1,
        """
        move-object/from16 v${scratch[0]}, p0
        iget v${scratch[0]}, v${scratch[0]}, $field
        const-class v${scratch[1]}, $cacheClass
        invoke-static {v$listRegister, v${scratch[1]}, v${scratch[0]}}, $PATCHES_DESCRIPTOR/TimelinePosition;->preserveCache(Ljava/util/List;Ljava/lang/Class;I)V
        """.trimIndent(),
    )
}

private object InitialTimelineLoadFingerprint : Fingerprint(
    returnType = "I",
    parameters = emptyList(),
    strings = listOf("android_initial_timeline_load_count", "android_home_timeline_mark_as_unlimited_timeline"),
)

context(context: BytecodePatchContext)
internal fun preserveInitialTimelineLoad(forYouType: Int) {
    val method = InitialTimelineLoadFingerprint.matchAll(1..1).single().method
    val code = method.instructions.toList()
    val type = code.filter { it.opcode == Opcode.IGET }.mapNotNull { it.getReference<FieldReference>() }
        .singleOrNull { it.definingClass == method.definingClass && it.type == "I" }
        ?: throw PatchException("Could not resolve initial-load timeline type")
    val keyIndex = code.indexOfFirst {
        it.getReference<StringReference>()?.string == "android_initial_timeline_load_count"
    }
    val call = code.getOrNull(keyIndex + 2)
    val reference = call?.getReference<MethodReference>()
    val registers = call?.registersUsed.orEmpty()
    val fallback = code.getOrNull(keyIndex + 1)
    val result = code.getOrNull(keyIndex + 3)
    val exit = code.getOrNull(keyIndex + 4)
    val receiver = method.implementation!!.registerCount - 1
    if (call?.opcode != Opcode.INVOKE_VIRTUAL || reference?.parameterTypes != listOf("Ljava/lang/String;", "I") ||
        reference.returnType != "I" || registers.size != 3 || registers.distinct().size != 3 ||
        code[keyIndex].registersUsed.singleOrNull() != registers[1] ||
        fallback?.opcode !in listOf(Opcode.CONST_16, Opcode.CONST) ||
        fallback?.registersUsed?.singleOrNull() != registers[2] ||
        (fallback as? NarrowLiteralInstruction)?.narrowLiteral?.let { it > 0 } != true ||
        result?.opcode != Opcode.MOVE_RESULT || exit?.opcode != Opcode.RETURN ||
        result.registersUsed != exit.registersUsed
    ) throw PatchException("Unexpected initial-load configuration read")
    val resultRegister = result.registersUsed.single()
    val typeRegister = registers[1]
    val defaultRegister = registers[2]
    val forYouRegister = (0 until receiver).firstOrNull { it !in listOf(resultRegister, typeRegister, defaultRegister) }
        ?: throw PatchException("No initial-load scope register available")
    if (listOf(resultRegister, typeRegister, defaultRegister).distinct().size != 3 ||
        listOf(resultRegister, typeRegister, defaultRegister, forYouRegister, receiver).any { it !in 0..15 } ||
        receiver in listOf(resultRegister, typeRegister, defaultRegister)
    ) throw PatchException("Unexpected initial-load registers")

    // Only the first-load return is changed; later loads retain Twitter's existing limits.
    method.addInstructions(keyIndex + 4, """
        iget v$typeRegister, p0, $type
        const/16 v$forYouRegister, $forYouType
        invoke-static {v$resultRegister, v$defaultRegister, v$typeRegister, v$forYouRegister}, $PATCHES_DESCRIPTOR/TimelinePosition;->initialLoadCount(IIII)I
        move-result v$resultRegister
    """.trimIndent())
}
