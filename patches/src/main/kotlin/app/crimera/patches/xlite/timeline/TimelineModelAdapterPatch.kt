package app.crimera.patches.xlite.timeline

import app.crimera.patches.xlite.misc.extension.xLiteExtensionPatch
import app.crimera.patches.xlite.utils.Constants.TIMELINE_FILTER_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"

private object TimelinePostModelFingerprint : Fingerprint(
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    strings = listOf("UrtTimelinePost(postResult=", ", promotedMetadata="),
)

private object TimelineModuleModelFingerprint : Fingerprint(
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    strings = listOf("UrtTimelineModule(innerContent=", ", moduleHeader="),
)

private object TimelineModuleItemModelFingerprint : Fingerprint(
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    strings = listOf("UrtTimelineModuleItem(item=", ", isDispensable="),
)

private object VerticalConversationModelFingerprint : Fingerprint(
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    strings = listOf("VerticalConversation(allTweetIds="),
)

private data class TimelineModels(
    val postDescriptor: String,
    val moduleDescriptor: String,
    val moduleItemDescriptor: String,
    val verticalConversationDescriptor: String,
    val postIdGetter: MethodReference,
    val verticalConversationPostIdsField: FieldReference,
    val verticalConversationConstructor: MethodReference,
    val moduleInnerContentField: FieldReference,
    val moduleHeaderField: FieldReference,
    val moduleFooterField: FieldReference,
    val moduleDisplayTypeField: FieldReference,
    val moduleSortIndexField: FieldReference,
    val moduleEntryIdField: FieldReference,
    val moduleClientEventInfoField: FieldReference,
    val moduleItemField: FieldReference,
    val moduleItemDispensableField: FieldReference,
    val moduleConstructor: MethodReference,
    val moduleItemConstructor: MethodReference,
)

internal val xLiteTimelineModelAdapterPatch =
    bytecodePatch(default = false) {
        dependsOn(xLiteExtensionPatch)

        execute {
            val models = resolveTimelineModels()
            patchTimelineFilterBridges(models)
        }
    }

context(_: BytecodePatchContext)
private fun resolveTimelineModels(): TimelineModels {
    val postMatch = TimelinePostModelFingerprint.requireSingle("timeline post model")
    val moduleMatch = TimelineModuleModelFingerprint.requireSingle("timeline module model")
    val moduleItemMatch = TimelineModuleItemModelFingerprint.requireSingle("timeline module-item model")
    val verticalConversationMatch =
        VerticalConversationModelFingerprint.requireSingle("vertical-conversation display model")
    val postClass = postMatch.classDef
    val moduleClass = moduleMatch.classDef
    val moduleItemClass = moduleItemMatch.classDef
    val verticalConversationClass = verticalConversationMatch.classDef

    val postIdGetters = postClass.methods.filter { method ->
        method.name == "getId" && method.parameterTypes.isEmpty() && method.returnType.startsWith("L")
    }
    if (postIdGetters.size != 1) {
        throw PatchException(
            "Expected one X-Lite timeline post getId(), found ${postIdGetters.size}: " +
                postIdGetters.joinToString(),
        )
    }
    val verticalConversationPostIdsField = verticalConversationClass.requireSingleField(LIST_DESCRIPTOR)
    val verticalConversationConstructor =
        verticalConversationClass.methods.singleOrNull { method ->
            method.name == "<init>" &&
                method.parameterTypes.map(CharSequence::toString) == listOf(LIST_DESCRIPTOR) &&
                method.returnType == "V"
        } ?: throw PatchException(
            "Expected one X-Lite vertical-conversation constructor: $verticalConversationClass",
        )

    val moduleInnerContentField = moduleClass.requireSingleField(LIST_DESCRIPTOR)
    val moduleHeaderField = moduleMatch.fieldForToStringLabel(", moduleHeader=")
    val moduleFooterField = moduleMatch.fieldForToStringLabel(", moduleFooter=")
    val moduleDisplayTypeField = moduleMatch.fieldForToStringLabel(", displayType=")
    val moduleSortIndexField = moduleClass.requireSingleField("J")
    val moduleEntryIdField = moduleClass.requireSingleField(STRING_DESCRIPTOR)
    val moduleClientEventInfoField = moduleMatch.fieldForToStringLabel(", clientEventInfo=")
    val moduleConstructorParameters =
        listOf(
            moduleInnerContentField.type,
            moduleHeaderField.type,
            moduleFooterField.type,
            moduleDisplayTypeField.type,
            "J",
            STRING_DESCRIPTOR,
            moduleClientEventInfoField.type,
        )
    val moduleConstructor =
        moduleClass.methods.singleOrNull { method ->
            method.name == "<init>" &&
                method.parameterTypes.map(CharSequence::toString) == moduleConstructorParameters &&
                method.returnType == "V"
        } ?: throw PatchException(
            "Expected one X-Lite timeline module constructor with $moduleConstructorParameters: " +
                moduleClass,
        )

    val moduleItemField = moduleItemMatch.fieldForToStringLabel("UrtTimelineModuleItem(item=")
    val moduleItemDispensableField = moduleItemClass.requireSingleField("Z")
    val moduleItemConstructorParameters = listOf(moduleItemField.type, "Z")
    val moduleItemConstructor =
        moduleItemClass.methods.singleOrNull { method ->
            method.name == "<init>" &&
                method.parameterTypes.map(CharSequence::toString) == moduleItemConstructorParameters &&
                method.returnType == "V"
        } ?: throw PatchException(
            "Expected one X-Lite timeline module-item constructor with " +
                "$moduleItemConstructorParameters: $moduleItemClass",
        )

    verticalConversationClass.makeFieldsPublic(listOf(verticalConversationPostIdsField))
    moduleClass.makeFieldsPublic(
        listOf(
            moduleInnerContentField,
            moduleHeaderField,
            moduleFooterField,
            moduleDisplayTypeField,
            moduleSortIndexField,
            moduleEntryIdField,
            moduleClientEventInfoField,
        ),
    )
    moduleItemClass.makeFieldsPublic(listOf(moduleItemField, moduleItemDispensableField))

    return TimelineModels(
        postDescriptor = postClass.type,
        moduleDescriptor = moduleClass.type,
        moduleItemDescriptor = moduleItemClass.type,
        verticalConversationDescriptor = verticalConversationClass.type,
        postIdGetter = postIdGetters.single(),
        verticalConversationPostIdsField = verticalConversationPostIdsField,
        verticalConversationConstructor = verticalConversationConstructor,
        moduleInnerContentField = moduleInnerContentField,
        moduleHeaderField = moduleHeaderField,
        moduleFooterField = moduleFooterField,
        moduleDisplayTypeField = moduleDisplayTypeField,
        moduleSortIndexField = moduleSortIndexField,
        moduleEntryIdField = moduleEntryIdField,
        moduleClientEventInfoField = moduleClientEventInfoField,
        moduleItemField = moduleItemField,
        moduleItemDispensableField = moduleItemDispensableField,
        moduleConstructor = moduleConstructor,
        moduleItemConstructor = moduleItemConstructor,
    )
}

context(_: BytecodePatchContext)
internal fun resolveTimelinePostModelMatch(): Match =
    TimelinePostModelFingerprint.requireSingle("timeline post model")

context(context: BytecodePatchContext)
private fun patchTimelineFilterBridges(models: TimelineModels) {
    val filterClass = context.mutableClassDefBy(TIMELINE_FILTER_DESCRIPTOR)

    filterClass.patchBridge(
        "isTimelineModuleItem",
        OBJECT_DESCRIPTOR,
        "Z",
        "instance-of p0, p0, ${models.moduleItemDescriptor}\nreturn p0",
    )
    filterClass.patchBridge(
        "isTimelinePost",
        OBJECT_DESCRIPTOR,
        "Z",
        "instance-of p0, p0, ${models.postDescriptor}\nreturn p0",
    )
    filterClass.patchBridge(
        "isTimelineModule",
        OBJECT_DESCRIPTOR,
        "Z",
        "instance-of p0, p0, ${models.moduleDescriptor}\nreturn p0",
    )
    filterClass.patchBridge(
        "getPostId",
        OBJECT_DESCRIPTOR,
        OBJECT_DESCRIPTOR,
        """
            check-cast p0, ${models.postDescriptor}
            invoke-virtual {p0}, ${models.postIdGetter.smaliReference()}
            move-result-object p0
            return-object p0
        """.trimIndent(),
    )
    filterClass.patchBridge(
        "isVerticalConversation",
        OBJECT_DESCRIPTOR,
        "Z",
        "instance-of p0, p0, ${models.verticalConversationDescriptor}\nreturn p0",
    )
    filterClass.patchObjectFieldGetter(
        "getVerticalConversationPostIds",
        models.verticalConversationDescriptor,
        models.verticalConversationPostIdsField,
        LIST_DESCRIPTOR,
    )
    filterClass.patchBridge(
        "copyVerticalConversation",
        "$OBJECT_DESCRIPTOR$LIST_DESCRIPTOR",
        OBJECT_DESCRIPTOR,
        """
            new-instance p0, ${models.verticalConversationDescriptor}
            invoke-direct {p0, p1}, ${models.verticalConversationConstructor.smaliReference()}
            return-object p0
        """.trimIndent(),
    )
    filterClass.patchObjectFieldGetter(
        "getModuleItem",
        models.moduleItemDescriptor,
        models.moduleItemField,
    )
    filterClass.patchBooleanFieldGetter(
        "isModuleItemDispensable",
        models.moduleItemDescriptor,
        models.moduleItemDispensableField,
    )
    filterClass.patchBridge(
        "copyModuleItem",
        "$OBJECT_DESCRIPTOR$OBJECT_DESCRIPTOR" + "Z",
        OBJECT_DESCRIPTOR,
        """
            check-cast p1, ${models.moduleItemField.type}
            new-instance p0, ${models.moduleItemDescriptor}
            invoke-direct {p0, p1, p2}, ${models.moduleItemConstructor.smaliReference()}
            return-object p0
        """.trimIndent(),
    )
    filterClass.patchObjectFieldGetter(
        "getModuleInnerContent",
        models.moduleDescriptor,
        models.moduleInnerContentField,
        LIST_DESCRIPTOR,
    )
    filterClass.patchObjectFieldGetter(
        "getModuleHeader",
        models.moduleDescriptor,
        models.moduleHeaderField,
    )
    filterClass.patchObjectFieldGetter(
        "getModuleFooter",
        models.moduleDescriptor,
        models.moduleFooterField,
    )
    filterClass.patchObjectFieldGetter(
        "getModuleDisplayType",
        models.moduleDescriptor,
        models.moduleDisplayTypeField,
    )
    filterClass.patchWideFieldGetter(
        "getModuleSortIndex",
        models.moduleDescriptor,
        models.moduleSortIndexField,
    )
    filterClass.patchObjectFieldGetter(
        "getModuleEntryId",
        models.moduleDescriptor,
        models.moduleEntryIdField,
        STRING_DESCRIPTOR,
    )
    filterClass.patchObjectFieldGetter(
        "getModuleClientEventInfo",
        models.moduleDescriptor,
        models.moduleClientEventInfoField,
    )
    filterClass.patchBridge(
        "copyModule",
        "$OBJECT_DESCRIPTOR$LIST_DESCRIPTOR$OBJECT_DESCRIPTOR$OBJECT_DESCRIPTOR" +
            "$OBJECT_DESCRIPTOR" + "J$STRING_DESCRIPTOR$OBJECT_DESCRIPTOR",
        OBJECT_DESCRIPTOR,
        """
            check-cast p1, ${models.moduleInnerContentField.type}
            check-cast p2, ${models.moduleHeaderField.type}
            check-cast p3, ${models.moduleFooterField.type}
            check-cast p4, ${models.moduleDisplayTypeField.type}
            check-cast p8, ${models.moduleClientEventInfoField.type}
            new-instance p0, ${models.moduleDescriptor}
            invoke-direct/range {p0 .. p8}, ${models.moduleConstructor.smaliReference()}
            return-object p0
        """.trimIndent(),
    )
}

context(_: BytecodePatchContext)
private fun Fingerprint.requireSingle(target: String): Match {
    val matches = matchAll()
    if (matches.size == 1) return matches.single()
    throw PatchException(
        "Expected one X-Lite $target, found ${matches.size}: " +
            matches.joinToString { it.originalMethod.toString() },
    )
}

internal fun Match.fieldForToStringLabel(value: String): FieldReference {
    val instructions = method.instructions
    val labelIndex =
        instructions.indexOfFirst { instruction ->
            instruction.getReference<StringReference>()?.string == value
        }
    if (labelIndex < 0) throw PatchException("X-Lite model label was not found: $value in $originalMethod")

    val appendCandidates =
        instructions.withIndex().drop(labelIndex + 1).filter { (_, instruction) ->
            instruction.getReference<MethodReference>()?.let { reference ->
                reference.definingClass == "Ljava/lang/StringBuilder;" &&
                    reference.name == "append" &&
                    reference.parameterTypes.size == 1
            } == true
        }
    val appendIndex = if (value.startsWith("UrtTimeline")) 0 else 1
    val append = appendCandidates.getOrNull(appendIndex)
        ?: throw PatchException("X-Lite model value append after '$value' was not found in $originalMethod")
    val appendInstruction = append.value as? FiveRegisterInstruction
        ?: throw PatchException("X-Lite model append after '$value' has an unsupported register layout")
    val valueRegister = appendInstruction.registerD

    return instructions.take(append.index).asReversed().firstNotNullOfOrNull { instruction ->
        val registerInstruction = instruction as? TwoRegisterInstruction ?: return@firstNotNullOfOrNull null
        if (registerInstruction.registerA != valueRegister) return@firstNotNullOfOrNull null
        instruction.getReference<FieldReference>()?.takeIf { field ->
            field.definingClass == originalMethod.definingClass
        }
    } ?: throw PatchException("X-Lite model field for '$value' was not found in $originalMethod")
}

private fun MutableClass.requireSingleField(type: String): FieldReference {
    val matches = fields.filter { field ->
        field.type == type && !AccessFlags.STATIC.isSet(field.accessFlags)
    }
    if (matches.size == 1) return matches.single()
    throw PatchException(
        "Expected one X-Lite model field of type $type in $this, found " +
            "${matches.size}: ${matches.joinToString()}",
    )
}

internal fun MutableClass.makeFieldsPublic(fields: List<FieldReference>) {
    for (field in fields) {
        val definition = this.fields.singleOrNull { candidate -> candidate.toString() == field.toString() }
            ?: throw PatchException("X-Lite model field definition was not found: $field")
        val nonPublicFlags = AccessFlags.PRIVATE.value or AccessFlags.PROTECTED.value
        definition.accessFlags =
            (definition.accessFlags and nonPublicFlags.inv()) or AccessFlags.PUBLIC.value
    }
}

internal fun MutableClass.patchObjectFieldGetter(
    name: String,
    ownerDescriptor: String,
    field: FieldReference,
    returnType: String = OBJECT_DESCRIPTOR,
) = patchBridge(
    name,
    OBJECT_DESCRIPTOR,
    returnType,
    "check-cast p0, $ownerDescriptor\niget-object p0, p0, $field\nreturn-object p0",
)

private fun MutableClass.patchBooleanFieldGetter(
    name: String,
    ownerDescriptor: String,
    field: FieldReference,
) = patchBridge(
    name,
    OBJECT_DESCRIPTOR,
    "Z",
    "check-cast p0, $ownerDescriptor\niget-boolean p0, p0, $field\nreturn p0",
)

private fun MutableClass.patchWideFieldGetter(
    name: String,
    ownerDescriptor: String,
    field: FieldReference,
) = patchBridge(
    name,
    OBJECT_DESCRIPTOR,
    "J",
    "check-cast p0, $ownerDescriptor\niget-wide v0, p0, $field\nreturn-wide v0",
)

internal fun MutableClass.patchBridge(
    name: String,
    parameters: String,
    returnType: String,
    instructions: String,
) {
    val matches = methods.filter { method ->
        method.name == name &&
            method.parameterTypes.joinToString("") == parameters &&
            method.returnType == returnType
    }
    if (matches.size != 1) {
        throw PatchException(
            "Expected one X-Lite timeline bridge $name($parameters)$returnType, found " +
                "${matches.size}: ${matches.joinToString()}",
        )
    }
    matches.single().addInstructions(0, instructions)
}

internal fun MethodReference.smaliReference(): String =
    "$definingClass->$name(${parameterTypes.joinToString("")})$returnType"
