/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.disableAutoScroll

import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.utils.changeString
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import com.android.tools.smali.dexlib2.Opcode

private object HomeTimelineSaveFingerprint : Fingerprint(
    classFingerprint = HomeTimelineRestoreFingerprint,
    returnType = "V",
    parameters = emptyList(),
    strings = listOf("android_htl_position_metadata_capture_enabled"),
)

private data class TimelineFields(
    val type: FieldReference,
    val view: FieldReference,
    val list: FieldReference,
    val recycler: FieldReference,
    val provider: FieldReference,
    val rowId: FieldReference,
    val offset: FieldReference,
)

context(context: BytecodePatchContext)
internal fun preserveTimelinePosition(): Pair<String, Int> {
    val restore = HomeTimelineRestoreFingerprint.matchAll(1..1).single()
    val home = restore.classDef
    val restoreCode = restore.method.instructions.toList()
    val timelineType = restoreCode[0].getReference<FieldReference>()
    val forYouType = (restoreCode.getOrNull(1) as? NarrowLiteralInstruction)?.narrowLiteral
    if (restoreCode[0].opcode != Opcode.IGET || timelineType?.type != "I" ||
        forYouType == null || forYouType !in 1..127 || restoreCode.getOrNull(2)?.opcode != Opcode.IF_NE ||
        restoreCode[2].registersUsed != listOf(restoreCode[0].registersUsed[0], restoreCode[1].registersUsed.single())
    ) throw PatchException("Unexpected For You restoration scope")

    val scopeName = "pikoDisableAutoTimelineScroll"
    val scope = "${home.type}->$scopeName()Z"
    val captureName = "pikoCaptureVisibleTimeline"
    if (home.methods.any { it.name == scopeName || it.name == captureName }) {
        throw PatchException("Timeline position helpers already exist")
    }

    val save = HomeTimelineSaveFingerprint.matchAll(1..1).single().method
    val captureReference = save.instructions.mapNotNull { it.getReference<MethodReference>() }
        .singleOrNull { it.parameterTypes.isEmpty() && it.returnType == "Ljava/util/List;" }
        ?: throw PatchException("Could not uniquely resolve native position capture")
    val capture = context.mutableClassDefBy(captureReference.definingClass).methods.single {
        it.toString() == captureReference.toString()
    }
    val captureCode = capture.instructions.toList()
    val viewFields = captureCode.filter { it.opcode == Opcode.IGET_OBJECT }
        .map { it.getReference<FieldReference>()!! }
    if (viewFields.size != 3 || viewFields[1].definingClass != viewFields[0].type ||
        viewFields[2].definingClass != viewFields[1].type || (capture.implementation?.registerCount ?: 0) < 2
    ) throw PatchException("Unexpected timeline view chain")
    val (view, list, recycler) = viewFields
    val firstPosition = captureCode.firstOrNull { it.opcode == Opcode.INVOKE_VIRTUAL }
        ?.getReference<MethodReference>() ?: throw PatchException("Missing native position model")
    val positionConstructor = context.classDefBy(firstPosition.returnType).methods.singleOrNull {
        it.name == "<init>" && it.parameterTypes == listOf("I", "I", "J") && AccessFlags.PUBLIC.isSet(it.accessFlags)
    } ?: throw PatchException("Unexpected native position constructor")
    val currentPosition = captureCode.mapNotNull { it.getReference<MethodReference>() }
        .singleOrNull { it.definingClass == list.type && it.parameterTypes.isEmpty() }
        ?: throw PatchException("Could not resolve pending timeline position")
    val positionReader = context.classDefBy(list.type).methods.single { it.toString() == currentPosition.toString() }
    val pending = positionReader.getInstruction(0).getReference<FieldReference>()
    if (positionReader.getInstruction(0).opcode != Opcode.IGET_OBJECT || pending?.type != currentPosition.returnType) {
        throw PatchException("Unexpected pending timeline position")
    }

    val recyclerClass = context.classDefBy(recycler.type)
    val holderReader = recyclerClass.methods.singleOrNull {
        AccessFlags.STATIC.isSet(it.accessFlags) && AccessFlags.PUBLIC.isSet(it.accessFlags) &&
            it.parameterTypes == listOf("Landroid/view/View;") && it.returnType.startsWith(recycler.type.dropLast(1) + "$")
    } ?: throw PatchException("Could not uniquely resolve the visible ViewHolder")
    val holderClass = context.classDefBy(holderReader.returnType)
    val itemId = holderClass.methods.singleOrNull {
        it.name == "getItemId" && it.parameterTypes.isEmpty() && it.returnType == "J" && AccessFlags.PUBLIC.isSet(it.accessFlags)
    } ?: throw PatchException("Missing visible item ID accessor")
    val adapterPosition = holderClass.methods.singleOrNull {
        it.name == "getAbsoluteAdapterPosition" && it.parameterTypes.isEmpty() && it.returnType == "I" &&
            AccessFlags.PUBLIC.isSet(it.accessFlags)
    } ?: throw PatchException("Missing visible adapter position accessor")
    val bounds = recyclerClass.methods.singleOrNull {
        AccessFlags.STATIC.isSet(it.accessFlags) && AccessFlags.PUBLIC.isSet(it.accessFlags) &&
            it.parameterTypes == listOf("Landroid/graphics/Rect;", "Landroid/view/View;") && it.returnType == "V"
    } ?: throw PatchException("Could not uniquely resolve decorated item bounds")

    home.methods.add(
        ImmutableMethod(home.type, scopeName, emptyList(), "Z", AccessFlags.PUBLIC.value or AccessFlags.FINAL.value,
            null, null, MutableMethodImplementation(3)).toMutable().apply {
            addInstructions("""
                iget v0, p0, $timelineType
                const/16 v1, $forYouType
                if-ne v0, v1, :disabled
                invoke-static {}, $PREF_DESCRIPTOR;->disableAutoTimelineScroll()Z
                move-result v0
                return v0
                :disabled
                const/4 v0, 0x0
                return v0
            """.trimIndent())
        },
    )

    val snapshot = "${home.type}->$captureName(${capture.definingClass})Ljava/util/List;"
    home.methods.add(
        ImmutableMethod(home.type, captureName, listOf(ImmutableMethodParameter(capture.definingClass, null, null)),
            "Ljava/util/List;", AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            null, null, MutableMethodImplementation(13)).toMutable().apply {
            // Adapter positions can be NO_POSITION while an update awaits layout. The
            // native capture adds child indices to that value and saves unrelated rows.
            // Bound IDs and decorated offsets describe the items actually on screen.
            addInstructions("""
                instance-of v0, p0, ${home.type}
                if-eqz v0, :native
                check-cast p0, ${home.type}
                invoke-virtual {p0}, $scope
                move-result v0
                if-eqz v0, :native
                iget-object v0, p0, $view
                iget-object v0, v0, $list
                iget-object v1, v0, $pending
                if-nez v1, :native
                iget-object v0, v0, $recycler
                new-instance v1, Ljava/util/ArrayList;
                invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V
                new-instance v2, Landroid/graphics/Rect;
                invoke-direct {v2}, Landroid/graphics/Rect;-><init>()V
                const/4 v3, 0x0
                :next_child
                invoke-virtual {v0}, Landroid/view/ViewGroup;->getChildCount()I
                move-result v4
                if-ge v3, v4, :done
                invoke-virtual {v0, v3}, Landroid/view/ViewGroup;->getChildAt(I)Landroid/view/View;
                move-result-object v4
                invoke-static {v4}, $holderReader
                move-result-object v5
                if-eqz v5, :next
                invoke-virtual {v5}, $itemId
                move-result-wide v6
                const-wide/16 v8, 0x0
                cmp-long v10, v6, v8
                if-lez v10, :next
                invoke-virtual {v5}, $adapterPosition
                move-result v5
                invoke-static {v2, v4}, $bounds
                iget v8, v2, Landroid/graphics/Rect;->top:I
                new-instance v4, ${firstPosition.returnType}
                invoke-direct {v4, v8, v5, v6, v7}, $positionConstructor
                invoke-virtual {v1, v4}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z
                :next
                add-int/lit8 v3, v3, 0x1
                goto :next_child
                :done
                invoke-virtual {v1}, Ljava/util/ArrayList;->isEmpty()Z
                move-result v3
                if-nez v3, :native
                return-object v1
                :native
                const/4 v0, 0x0
                return-object v0
            """.trimIndent())
        },
    )
    capture.addInstructionsWithLabels(0, """
        invoke-static/range {p0 .. p0}, $snapshot
        move-result-object v0
        if-eqz v0, :native
        return-object v0
    """.trimIndent(), ExternalLabel("native", capture.getInstruction(0)))

    val savedPositions = restoreCode.mapNotNull { it.getReference<FieldReference>() }
        .singleOrNull { it.type == "Ljava/util/List;" } ?: throw PatchException("Missing saved position list")
    val restoreStart = restoreCode.singleOrNull {
        it.opcode == Opcode.IGET_OBJECT && it.getReference<FieldReference>()?.let { field ->
            field.definingClass == home.type && field.type == savedPositions.definingClass
        } == true
    } ?: throw PatchException("Could not uniquely resolve saved position restoration")
    val constructorCode = positionConstructor.implementation!!.instructions.toList()
    val constructorReceiver = positionConstructor.implementation!!.registerCount - 5
    fun positionField(parameter: Int, opcode: Opcode) = constructorCode.singleOrNull {
        it.opcode == opcode && it.registersUsed == listOf(constructorReceiver + parameter, constructorReceiver)
    }?.getReference<FieldReference>() ?: throw PatchException("Could not resolve saved position field")
    preserveTimelineIdentity(home.type, forYouType, scope, save, restore.method,
        TimelineFields(
            type = timelineType,
            view = view,
            list = list,
            recycler = recycler,
            provider = restoreStart.getReference<FieldReference>()!!,
            rowId = positionField(3, Opcode.IPUT_WIDE),
            offset = positionField(1, Opcode.IPUT),
        ))
    return scope to forYouType
}

context(context: BytecodePatchContext)
private fun preserveTimelineIdentity(
    homeType: String, forYouType: Int, scope: String, save: MutableMethod, restore: MutableMethod,
    fields: TimelineFields,
) {
    val code = save.instructions.toList()
    val references = code.mapNotNull { it.getReference<MethodReference>() }
    val constructor = references.singleOrNull {
        it.name == "<init>" && it.parameterTypes == listOf("Ljava/lang/String;", "Ljava/lang/String;", "J", "J", "I")
    } ?: throw PatchException("Could not resolve native timeline metadata")
    val constructorMethod = context.classDefBy(constructor.definingClass).methods.single { it.toString() == constructor.toString() }
    val receiver = constructorMethod.implementation!!.registerCount - 8
    fun metadataField(parameter: Int, opcode: Opcode): FieldReference = constructorMethod.instructions.singleOrNull {
        it.opcode == opcode && it.registersUsed == listOf(receiver + parameter, receiver)
    }?.getReference<FieldReference>() ?: throw PatchException("Unexpected timeline metadata constructor")
    val metadata = code.filter { it.opcode == Opcode.IPUT_OBJECT }.mapNotNull { it.getReference<FieldReference>() }
        .singleOrNull { it.type == constructor.definingClass }
        ?: throw PatchException("Could not resolve serialized timeline metadata field")
    val modelMethods = references.filter { it.parameterTypes.isEmpty() && it.returnType == "Ljava/lang/String;" }
        .filter { it.definingClass != homeType && it.definingClass != fields.type.definingClass }
    if (modelMethods.size != 2 || modelMethods[0].definingClass != modelMethods[1].definingClass) {
        throw PatchException("Could not resolve entry and group identity accessors")
    }
    val itemType = modelMethods.first().definingClass
    val itemRow = code.filter { it.opcode == Opcode.IGET_WIDE }.mapNotNull { it.getReference<FieldReference>() }
        .singleOrNull { it.definingClass == itemType } ?: throw PatchException("Could not resolve timeline item row ID")
    val itemInfo = references.singleOrNull {
        it.definingClass == itemType && it.parameterTypes.isEmpty() && it.returnType != "Ljava/lang/String;"
    } ?: throw PatchException("Could not resolve timeline item metadata accessor")
    val sort = code.filter { it.opcode == Opcode.IGET_WIDE }.mapNotNull { it.getReference<FieldReference>() }
        .singleOrNull { it.definingClass == itemInfo.returnType } ?: throw PatchException("Could not resolve timeline sort metadata")
    val adapter = references.singleOrNull { it.definingClass == fields.view.type && it.parameterTypes.isEmpty() }
        ?: throw PatchException("Could not resolve timeline adapter accessor")
    val count = references.filter { it.parameterTypes.isEmpty() && it.returnType == "I" }.distinctBy { it.toString() }.singleOrNull()
        ?: throw PatchException("Could not resolve timeline item count")
    val item = references.singleOrNull { it.parameterTypes == listOf("I") && it.returnType == "Ljava/lang/Object;" }
        ?: throw PatchException("Could not resolve timeline item accessor")
    val restoreCallIndex = restore.instructions.withIndex().singleOrNull { (_, instruction) ->
        instruction.opcode == Opcode.INVOKE_VIRTUAL && instruction.getReference<MethodReference>()?.let {
            it.parameterTypes == listOf("Ljava/util/List;") && it.returnType == "Z"
        } == true
    }?.index ?: throw PatchException("Could not uniquely resolve native position restoration")
    val nativeRestore = restore.instructions[restoreCallIndex].getReference<MethodReference>()!!
    val nativeCode = context.classDefBy(nativeRestore.definingClass).methods.single { it.toString() == nativeRestore.toString() }.instructions
    val nativeMethods = nativeCode.mapNotNull { it.getReference<MethodReference>() }
    val lookup = nativeMethods.singleOrNull { it.parameterTypes == listOf("J") && it.returnType == "I" }
        ?: throw PatchException("Could not resolve row position lookup")
    val scroll = nativeMethods.singleOrNull { it.parameterTypes == listOf("I", "I", "Z") && it.returnType == "V" }
        ?: throw PatchException("Could not resolve native position application")
    val bindingFields = mapOf(
        "type" to fields.type,
        "view" to fields.view,
        "list" to fields.list,
        "recycler" to fields.recycler,
        "provider" to fields.provider,
        "id" to fields.rowId,
        "offset" to fields.offset,
        "metadata" to metadata,
        "entry" to metadataField(1, Opcode.IPUT_OBJECT),
        "group" to metadataField(2, Opcode.IPUT_OBJECT),
        "metadataRow" to metadataField(5, Opcode.IPUT_WIDE),
        "metadataType" to metadataField(7, Opcode.IPUT),
        "itemRow" to itemRow,
        "sort" to sort,
    )
    val bindingMethods = mapOf(
        "adapter" to adapter, "count" to count, "item" to item,
        "itemEntry" to modelMethods[0], "itemGroup" to modelMethods[1], "itemInfo" to itemInfo,
        "lookup" to lookup, "scroll" to scroll,
    )
    bindingFields.values.forEach { ref ->
        val definition = context.classDefBy(ref.definingClass).fields.single { it.toString() == ref.toString() }
        if (!AccessFlags.PUBLIC.isSet(definition.accessFlags)) throw PatchException("Timeline identity field is not public: $ref")
    }
    bindingMethods.values.forEach { ref ->
        val definition = context.classDefBy(ref.definingClass).methods.single { it.toString() == ref.toString() }
        if (!AccessFlags.PUBLIC.isSet(definition.accessFlags)) throw PatchException("Timeline identity method is not public: $ref")
    }
    val bindings = mapOf("forYouType" to forYouType.toString()) +
        bindingFields.mapValues { it.value.toString() } + bindingMethods.mapValues { it.value.toString() }
    bindTimelineAccess(bindings)
    val extension = "$PATCHES_DESCRIPTOR/TimelinePosition;"
    val home = context.mutableClassDefBy(homeType)
    fun helper(name: String, parameters: List<String>, result: String, registers: Int, body: String) {
        if (home.methods.any { it.name == name }) throw PatchException("Timeline identity helper already exists")
        home.methods.add(ImmutableMethod(homeType, name, parameters.map { ImmutableMethodParameter(it, null, null) }, result,
            AccessFlags.PUBLIC.value or AccessFlags.FINAL.value, null, null, MutableMethodImplementation(registers)).toMutable().apply {
            addInstructions(0, body.trimIndent())
        })
    }
    helper("pikoRestorePositions", listOf("Ljava/util/List;"), "Z", 4, """
        invoke-virtual {p0}, $scope
        move-result v0
        if-eqz v0, :native
        invoke-static {p0, p1}, $extension->restore(Ljava/lang/Object;Ljava/util/List;)Z
        move-result v0
        return v0
        :native
        invoke-virtual {p0, p1}, $nativeRestore
        move-result v0
        return v0
    """)
    val restoreRegisters = restore.instructions[restoreCallIndex].registersUsed
    if (restoreRegisters.size != 2 || restoreRegisters.any { it !in 0..15 }) throw PatchException("Unexpected restore registers")
    restore.replaceInstruction(restoreCallIndex, "invoke-virtual {v${restoreRegisters[0]}, v${restoreRegisters[1]}}, $homeType->pikoRestorePositions(Ljava/util/List;)Z")
    for ((name, extensionMethod) in listOf("pikoKeepPosition" to "keep", "pikoCanSavePosition" to "canSave")) {
        helper(name, emptyList(), "Z", 2, """
            invoke-static {p0}, $extension->$extensionMethod(Ljava/lang/Object;)Z
            move-result v0
            return v0
        """)
    }
    helper("pikoPreparePositions", listOf("Ljava/util/List;"), "V", 3, """
        invoke-static {p0, p1}, $extension->prepare(Ljava/lang/Object;Ljava/util/List;)V
        return-void
    """)
    helper("pikoRetryTimelinePosition", emptyList(), "V", 2, """
        invoke-static {p0}, $extension->afterRender(Ljava/lang/Object;)V
        return-void
    """)
    val resetIndex = restore.instructions.indexOfFirst {
        it.getReference<MethodReference>()?.let { ref -> ref.definingClass == fields.provider.type && ref.name == "reset" && ref.parameterTypes.isEmpty() } == true
    }
    if (resetIndex < 0) throw PatchException("Could not resolve saved position reset")
    val resetResultRegister = restore.instructions.take(resetIndex).lastOrNull { it.opcode == Opcode.MOVE_RESULT }
        ?.registersUsed?.singleOrNull() ?: throw PatchException("Could not resolve reset guard register")
    val resetReceiver = restore.instructions[resetIndex].registersUsed.singleOrNull()
    if (resetResultRegister !in 0..255 || resetResultRegister == resetReceiver ||
        resetResultRegister >= restore.implementation!!.registerCount - 1
    ) throw PatchException("Unexpected reset guard register")
    val afterReset = restore.instructions[resetIndex + 1]
    restore.addInstructionsWithLabels(resetIndex, """
        invoke-virtual/range {p0 .. p0}, $homeType->pikoKeepPosition()Z
        move-result v$resetResultRegister
        if-nez v$resetResultRegister, :keep
    """.trimIndent(), ExternalLabel("keep", afterReset))
    val saveIndex = save.instructions.indexOfFirst {
        it.getReference<MethodReference>()?.let { ref -> ref.definingClass == fields.provider.type && ref.parameterTypes == listOf("Ljava/util/List;") } == true
    }
    if (saveIndex < 0) throw PatchException("Could not resolve native position persistence")
    val positionRegister = save.instructions[saveIndex].registersUsed.last()
    val saveReceiver = save.implementation!!.registerCount - 1
    if (positionRegister !in 0..15 || saveReceiver !in 0..15) throw PatchException("Unexpected position persistence registers")
    save.addInstructions(saveIndex, "invoke-virtual {v$saveReceiver, v$positionRegister}, $homeType->pikoPreparePositions(Ljava/util/List;)V")
    save.addInstructionsWithLabels(0, """
        invoke-virtual/range {p0 .. p0}, $homeType->pikoCanSavePosition()Z
        move-result v0
        if-nez v0, :save
        return-void
    """.trimIndent(), ExternalLabel("save", save.instructions.first()))

    val scrollListener = context.classDefBy(fields.list.type).fields.mapNotNull {
        if (!it.type.startsWith(fields.list.type.dropLast(1) + "$")) null else context.classDefBy(it.type)
    }.flatMap { it.methods }.singleOrNull {
        it.name == "onScrollStateChanged" && it.parameterTypes == listOf(fields.recycler.type, "I") && it.returnType == "V"
    } ?: throw PatchException("Could not resolve the timeline scroll-state listener")
    context.mutableClassDefBy(scrollListener.definingClass).methods.single { it.toString() == scrollListener.toString() }
        .addInstructions(0, "invoke-static/range {p1 .. p2}, $extension->onScrollStateChanged(Landroid/view/View;I)V")
}

context(context: BytecodePatchContext)
private fun bindTimelineAccess(bindings: Map<String, String>) {
    val bindingClass = "$PATCHES_DESCRIPTOR/TimelinePosition\$Binding;"
    val prefix = "piko_timeline_"
    val constructor = context.mutableClassDefBy(bindingClass).methods.singleOrNull { it.name == "<init>" }
        ?: throw PatchException("Could not uniquely resolve timeline access initialization")
    val placeholders = constructor.instructions.mapNotNull { it.getReference<StringReference>()?.string }
        .filter { it.startsWith(prefix) }
    if (placeholders.size != bindings.size || placeholders.toSet() != bindings.keys.map { prefix + it }.toSet()) {
        throw PatchException("Timeline access placeholders do not match the resolved bindings")
    }
    bindings.forEach { (name, descriptor) ->
        if (descriptor.any { it == '"' || it == '\\' || it == '\n' }) {
            throw PatchException("Unsupported timeline identity descriptor: $name")
        }
        val placeholder = prefix + name
        val fingerprint = object : Fingerprint(
            definingClass = bindingClass,
            name = "<init>",
            strings = listOf(placeholder),
        ) {}
        fingerprint.matchAll(1..1)
        fingerprint.changeString(placeholder, descriptor)
    }
}
