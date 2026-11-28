/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefBy
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resource.resourceId
import app.morphe.patcher.resourceLiteral
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val CONTEXT_DESCRIPTOR = "Landroid/content/Context;"
private const val CENTER_SCALE_TYPE =
    "Landroid/widget/ImageView\$ScaleType;->CENTER:Landroid/widget/ImageView\$ScaleType;"
private const val COMPONENT_LIST_ADD = "Ljava/util/AbstractCollection;->add($OBJECT_DESCRIPTOR)Z"
private const val INTEGER_VALUE_OF = "$INTEGER_DESCRIPTOR->valueOf(I)$INTEGER_DESCRIPTOR"
private const val CLICK_HANDLER_DESCRIPTOR =
    "Lapp/morphe/extension/instagram/patches/download/FeedDownloadClickFunction;"
private const val CLICK_HANDLER_CONSTRUCTOR =
    "$CLICK_HANDLER_DESCRIPTOR-><init>($CONTEXT_DESCRIPTOR$USER_SESSION_DESCRIPTOR$OBJECT_DESCRIPTOR)V"
private const val DOWNLOAD_ICON_METHOD = "pikoCreateDownloadIcon"

private fun saveIconFingerprint(stateType: String, inline: Boolean = false) = Fingerprint(
    parameters = if (inline) listOf("L") else listOf("L", "L", "L", stateType, "Z"),
    returnType = "L",
    filters = listOf(
        // Neutral icon style, before save state and event handlers are attached.
        if (inline) methodCall(smali = "Lkotlin/jvm/functions/Function0;->invoke()Ljava/lang/Object;")
        else fieldAccess(opcode = Opcode.SGET_OBJECT),
        if (inline) fieldAccess(opcode = Opcode.SGET_OBJECT)
        else fieldAccess(opcode = Opcode.SGET_OBJECT, location = MatchAfterImmediately()),
        methodCall(parameters = listOf("L", "L"), opcode = Opcode.INVOKE_STATIC, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        opcode(Opcode.CONST, MatchAfterImmediately()),
        methodCall(parameters = listOf("L", "I"), returnType = "J", location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_WIDE, MatchAfterImmediately()),
        methodCall(parameters = listOf("L", "J"), returnType = "L", location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        methodCall(parameters = listOf("L", "Ljava/lang/CharSequence;"), opcode = Opcode.INVOKE_STATIC),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        string("android.widget.Button", MatchAfterImmediately()),
        methodCall(parameters = listOf("L", "Ljava/lang/String;"), location = MatchAfterImmediately()),
        resourceLiteral(ResourceType.ID, "row_feed_button_save", location = MatchAfterWithin(4)),
        methodCall(parameters = listOf("L", "I"), location = MatchAfterImmediately()),
        methodCall(parameters = listOf("L", FUNCTION1_DESCRIPTOR)),
        methodCall(parameters = listOf("L", FUNCTION1_DESCRIPTOR), opcode = Opcode.INVOKE_STATIC),
        opcode(Opcode.CONST),
        methodCall(parameters = listOf("L", "I"), returnType = "I", location = MatchAfterImmediately()),
        opcode(Opcode.CONST, MatchAfterWithin(1)),
        methodCall(parameters = listOf("L", "I"), returnType = "I", location = MatchAfterImmediately()),
        methodCall(
            name = "<init>",
            parameters = listOf("Landroid/widget/ImageView\$ScaleType;", "L", INTEGER_DESCRIPTOR, "I", "I", "Z"),
        ),
    ),
)

/** The download component is added by the caller, independently of the save helper's visibility. */
internal fun injectLithoDownloadButton(stateType: String) {
    val legacy = saveIconFingerprint(stateType).matchOrNull()
    val inline = legacy == null
    val save = legacy ?: saveIconFingerprint(stateType, inline = true).match()
    val matches = save.instructionMatches
    val alignment = matches[1].fieldReference
    val alignmentSetter = matches[2].methodReference
    val heightResource = matches[4].getInstruction<WideLiteralInstruction>().wideLiteral.toInt()
    val heightAccessor = matches[5].methodReference
    val heightSetter = matches[7].methodReference
    val descriptionSetter = matches[9].methodReference
    val classSetter = matches[12].methodReference
    val idSetter = matches[14].methodReference
    val onClickSetter = matches[15].methodReference
    val onLongClickSetter = matches[16].methodReference
    val dimensionAttribute = matches[17].getInstruction<WideLiteralInstruction>().wideLiteral.toInt()
    val themeAccessor = matches[18].methodReference
    val tintAttribute = matches[19].getInstruction<WideLiteralInstruction>().wideLiteral.toInt()
    val iconConstructor = matches[21].methodReference
    val contextType = save.originalMethod.parameterTypes.first().toString()
    val nodeType = onClickSetter.parameterTypes.first().toString()
    val neutralStyle = if (inline) Fingerprint(
        definingClass = save.originalClassDef.type,
        parameters = listOf("L", "L", "L", stateType, "Z"),
        returnType = iconConstructor.definingClass,
        filters = listOf(fieldAccess(definingClass = nodeType, opcode = Opcode.SGET_OBJECT), string("getVisualSearchButtonComponent")),
    ).instructionMatches.first().fieldReference else matches[0].fieldReference
    if (neutralStyle.definingClass != nodeType ||
        !setsViewProperty(onClickSetter, "ON_CLICK") || !setsViewProperty(onLongClickSetter, "ON_LONG_CLICK") ||
        listOf(alignmentSetter, heightSetter, descriptionSetter, classSetter, idSetter, onClickSetter, onLongClickSetter).any {
            it.parameterTypes.first().toString() != nodeType || it.returnType != nodeType
        } || themeAccessor != matches[20].methodReference ||
        iconConstructor.parameterTypes[1].toString() != nodeType ||
        (!inline && save.originalMethod.returnType != iconConstructor.definingClass)
    ) {
        throw PatchException("Unexpected native save icon style in ${save.originalMethod}")
    }
    val contextGetter = requireOne(
        "component context accessor",
        classDefBy(themeAccessor.parameterTypes.first().toString()).methods.filter {
            it.parameterTypes.isEmpty() && it.returnType == CONTEXT_DESCRIPTOR
        },
    )

    val owner = mutableClassDefBy(save.originalClassDef)
    if (owner.methods.any { it.name == DOWNLOAD_ICON_METHOD }) throw PatchException("Download icon helper already exists")
    val helper = ImmutableMethod(
        owner.type,
        DOWNLOAD_ICON_METHOD,
        listOf(contextType, USER_SESSION_DESCRIPTOR, stateType).map { ImmutableMethodParameter(it, emptySet(), null) },
        iconConstructor.definingClass,
        AccessFlags.PRIVATE.value or AccessFlags.STATIC.value,
        emptySet(),
        emptySet(),
        MutableMethodImplementation(13),
    ).toMutable().apply {
        addInstructions(
            0,
            """
                invoke-static {}, $DOWNLOAD_UTILS_DESCRIPTOR->isFeedDownloadButtonEnabled()Z
                move-result v0
                if-eqz v0, :disabled
                invoke-interface {p0}, $contextGetter
                move-result-object v1
                new-instance v0, $CLICK_HANDLER_DESCRIPTOR
                invoke-direct {v0, v1, p1, p2}, $CLICK_HANDLER_CONSTRUCTOR

                sget-object v2, $neutralStyle
                sget-object v3, $alignment
                invoke-static {v2, v3}, $alignmentSetter
                move-result-object v2
                const v3, $heightResource
                invoke-static {p0, v3}, $heightAccessor
                move-result-wide v3
                invoke-static {v2, v3, v4}, $heightSetter
                move-result-object v2
                invoke-static {}, $DOWNLOAD_UTILS_DESCRIPTOR->feedDownloadButtonDescription()Ljava/lang/String;
                move-result-object v3
                invoke-static {v2, v3}, $descriptionSetter
                move-result-object v2
                const-string v3, "android.widget.Button"
                invoke-static {v2, v3}, $classSetter
                move-result-object v2
                const/4 v3, -1
                invoke-static {v2, v3}, $idSetter
                move-result-object v2
                invoke-static {v2, v0}, $onClickSetter
                move-result-object v2
                invoke-virtual {v0}, $CLICK_HANDLER_DESCRIPTOR->longClick()$FUNCTION1_DESCRIPTOR
                move-result-object v0
                invoke-static {v2, v0}, $onLongClickSetter
                move-result-object v5

                const v3, $dimensionAttribute
                invoke-static {p0, v3}, $themeAccessor
                move-result v8
                const v3, $tintAttribute
                invoke-static {p0, v3}, $themeAccessor
                move-result v6
                invoke-static {v6}, $INTEGER_VALUE_OF
                move-result-object v6
                sget-object v4, $CENTER_SCALE_TYPE
                const v7, ${resourceId(ResourceType.DRAWABLE, "instagram_download_outline_24").toInt()}
                const/4 v9, 0
                new-instance v3, ${iconConstructor.definingClass}
                invoke-direct/range {v3 .. v9}, $iconConstructor
                return-object v3
                :disabled
                const/4 v0, 0
                return-object v0
            """.trimIndent(),
        )
    }
    owner.methods.add(helper)
    if (inline) {
        // The saved-items control is optional. Insert into its owning list before native visibility checks.
        val controls = Fingerprint(
            definingClass = owner.type,
            parameters = listOf(contextType),
            filters = listOf(
                methodCall(smali = "Ljava/util/ArrayList;-><init>()V"),
                fieldAccess(definingClass = stateType, type = "Z", opcode = Opcode.IGET_BOOLEAN, location = MatchAfterImmediately()),
                opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
                methodCall(parameters = listOf("L", "L", "L", stateType, "Z"), returnType = iconConstructor.definingClass, location = MatchAfterWithin(8)),
                opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
                opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
                methodCall(smali = COMPONENT_LIST_ADD, location = MatchAfterImmediately()),
            ),
        ).match()
        val member = controls.method
        val index = controls.instructionMatches[1].index
        val list = controls.instructionMatches.first().instruction.registersUsed.single()
        val state = controls.instructionMatches[1].instruction.registersUsed.last()
        if (controls.originalMethod != save.originalMethod || list > 15 || state > 15 ||
            list != controls.instructionMatches.last().instruction.registersUsed.first()
        ) {
            throw PatchException("Save icon and native post controls belong to different lists")
        }
        val session = owner.instanceFields.single { it.type == USER_SESSION_DESCRIPTOR }
        val (contextArg, sessionArg) = member.reserveFreeRegisters(index, 2, 15, listOf(state, list))
        member.addInstructionsWithLabels(
            index,
            """
                ${moveObject(contextArg, member.parameterRegister(contextType))}
                ${moveObject(sessionArg, member.p0Register)}
                iget-object v$sessionArg, v$sessionArg, $session
                invoke-static {v$contextArg, v$sessionArg, v$state}, $helper
                move-result-object v$contextArg
                if-eqz v$contextArg, :native
                invoke-virtual {v$list, v$contextArg}, $COMPONENT_LIST_ADD
            """.trimIndent(),
            ExternalLabel("native", member.getInstruction(index)),
        )
        return
    }

    val caller = Fingerprint(
        definingClass = save.originalMethod.definingClass,
        parameters = listOf("L"),
        filters = listOf(
            methodCall(reference = save.originalMethod),
            opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
            methodCall(parameters = listOf("L", "L", "L", "L", USER_SESSION_DESCRIPTOR, "Z", "Z")),
            opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
            opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
            methodCall(smali = COMPONENT_LIST_ADD, location = MatchAfterImmediately()),
        ),
    )
    val builder = caller.method
    val callMatches = caller.instructionMatches
    val saveIndex = callMatches[0].index
    val saveCall = callMatches[0].instruction.registersUsed
    val factoryIndex = callMatches[2].index
    val factory = callMatches[2].methodReference
    val call = callMatches[2].instruction.registersUsed
    val listRegister = callMatches[5].instruction.registersUsed.first()
    val instructions = builder.instructions.toList()
    if (call.size != 7 || call != (call.first()..call.last()).toList() ||
        call.any { it in saveCall } || listRegister in call
    ) {
        throw PatchException("Unexpected component factory registers in $builder")
    }
    // All staging registers must be overwritten before the native path reads them again.
    call.forEach { register ->
        val firstUse = instructions.subList(saveIndex, factoryIndex).firstOrNull { register in it.registersUsed }
        if (firstUse == null || !firstUse.opcode.setsRegister() || firstUse.registersUsed.first() != register) {
            throw PatchException("Live component staging register v$register in $builder")
        }
    }
    val sources = call.drop(2).mapIndexed { offset, destination ->
        val move = instructions[factoryIndex - 5 + offset]
        if (move.opcode !in listOf(Opcode.MOVE_FROM16, Opcode.MOVE_OBJECT_FROM16) ||
            (move as TwoRegisterInstruction).registerA != destination
        ) {
            throw PatchException("Unexpected component argument staging in $builder")
        }
        move.registerB
    }
    if (sources.any { it in call }) throw PatchException("Component sources overlap staging registers in $builder")
    val category = builder.getInstruction(factoryIndex - 6)
    if (category.opcode != Opcode.SGET_OBJECT || category.registersUsed.single() != call[0]) {
        throw PatchException("Missing native component category in $builder")
    }
    val categoryField = category.getReference<FieldReference>()!!


    builder.addInstructionsWithLabels(
        saveIndex,
        """
            ${moveObject(call[0], saveCall[1])}
            ${moveObject(call[1], sources[2])}
            ${moveObject(call[2], saveCall[4])}
            invoke-static/range ${registerRange(call[0], call[2])}, $helper
            move-result-object v${call[1]}
            if-eqz v${call[1]}, :save
            sget-object v${call[0]}, $categoryField
            ${moveObject(call[2], sources[0])}
            ${moveObject(call[3], sources[1])}
            ${moveObject(call[4], sources[2])}
            ${moveInt(call[5], sources[3])}
            ${moveInt(call[6], sources[4])}
            invoke-static/range ${registerRange(call.first(), call.last())}, $factory
            move-result-object v${call[1]}
            if-eqz v${call[1]}, :save
            ${moveObject(call[2], listRegister)}
            ${moveObject(call[3], call[1])}
            invoke-virtual/range ${registerRange(call[2], call[3])}, $COMPONENT_LIST_ADD
        """.trimIndent(),
        ExternalLabel("save", builder.getInstruction(saveIndex)),
    )
}
