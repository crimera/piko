/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.utils.methodReference
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.checkCast
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.indexOfFirstInstructionReversedOrThrow
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import app.morphe.util.writeRegister
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction

/** Kotlin event handler type the UFI `ON_CLICK` setter takes. */
private const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val CONTEXT_DESCRIPTOR = "Landroid/content/Context;"

private const val BUTTON_VIEW_CLASS = "android.widget.Button"

private const val DOWNLOAD_CONTENT_DESCRIPTION = "Download"
private const val CENTER_SCALE_TYPE =
    "Landroid/widget/ImageView\$ScaleType;->CENTER:Landroid/widget/ImageView\$ScaleType;"
private const val COMPONENT_LIST_ADD = "Ljava/util/AbstractCollection;->add($OBJECT_DESCRIPTOR)Z"
private const val INTEGER_VALUE_OF = "$INTEGER_DESCRIPTOR->valueOf(I)$INTEGER_DESCRIPTOR"

private const val CLICK_HANDLER_DESCRIPTOR =
    "Lapp/morphe/extension/instagram/patches/download/FeedDownloadClickFunction;"
private const val CLICK_HANDLER_CONSTRUCTOR =
    "$CLICK_HANDLER_DESCRIPTOR-><init>($CONTEXT_DESCRIPTOR$USER_SESSION_DESCRIPTOR$OBJECT_DESCRIPTOR)V"
private const val FEED_DOWNLOAD_ENABLED = "$DOWNLOAD_UTILS_DESCRIPTOR->isFeedDownloadButtonEnabled()Z"

/**
 * The litho UFI builder, from the content description setter of the save icon to the add of its
 * finished component to the icon list.
 */
private fun lithoUfiBuilderFingerprint(saveButtonId: Long) = Fingerprint(
    parameters = listOf("L"),
    filters = listOf(
        // Content description setter.
        methodCall(opcode = Opcode.INVOKE_STATIC, parameters = listOf("L", "Ljava/lang/CharSequence;")),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        string(BUTTON_VIEW_CLASS, MatchAfterImmediately()),
        literal(saveButtonId, location = MatchAfterWithin(4)),
        // Icon id setter.
        methodCall(opcode = Opcode.INVOKE_STATIC, parameters = listOf("L", "I"), location = MatchAfterImmediately()),
        // ON_CLICK setter. The node passed to it has no click props yet, so the download node
        // does not inherit them.
        methodCall(parameters = listOf("L", FUNCTION1_DESCRIPTOR)),
        // The save icon's size and tint come from two theme attribute lookups.
        opcode(Opcode.CONST),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("L", "I"),
            returnType = "I",
            location = MatchAfterImmediately(),
        ),
        opcode(Opcode.CONST, MatchAfterWithin(1)),
        methodCall(
            opcode = Opcode.INVOKE_STATIC,
            parameters = listOf("L", "I"),
            returnType = "I",
            location = MatchAfterImmediately(),
        ),
        // Icon wrapper construction.
        methodCall(
            name = "<init>",
            parameters = listOf("Landroid/widget/ImageView\$ScaleType;", "L", INTEGER_DESCRIPTOR, "I", "I", "Z"),
        ),
        // Component factory, which takes seven consecutive registers.
        methodCall(parameters = listOf("L", "L", "L", "L", USER_SESSION_DESCRIPTOR, "Z", "Z")),
        opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
        // Icon list add.
        methodCall(
            name = "add",
            parameters = listOf(OBJECT_DESCRIPTOR),
            returnType = "Z",
            location = MatchAfterWithin(1),
        ),
    ),
)

/**
 * Litho UFI surfaces (e.g. the contextual profile feed) reject views added by hand, so the
 * download icon is built into the component tree as a second icon node ahead of the save icon.
 * Every obfuscated member is resolved from the instructions that build the save icon, and the
 * save icon's component-call registers, rebuilt right after the injection point, stage every invoke.
 */
context(patchContext: BytecodePatchContext)
internal fun injectLithoDownloadButton(
    saveButtonId: Long,
    downloadDrawableId: Long,
    stateType: String,
) {
    val match = lithoUfiBuilderFingerprint(saveButtonId).match()
    val builder = match.method
    val instructions = builder.instructions.toList()
    val matches = match.instructionMatches

    val descriptionSetter = matches[0].methodReference
    val idSetter = matches[4].methodReference
    val onClickIndex = matches[5].index
    val onClickSetter = matches[5].methodReference
    val nodeRegister = matches[5].instruction.registersUsed.first()
    val dimensionAttribute = matches[6].getInstruction<WideLiteralInstruction>().wideLiteral.toInt()
    val themeAccessor = matches[7].methodReference
    val tintAttribute = matches[8].getInstruction<WideLiteralInstruction>().wideLiteral.toInt()
    val wrapperIndex = matches[10].index
    val iconWrapperConstructor = matches[10].methodReference
    val iconWrapperClass = iconWrapperConstructor.definingClass
    val factoryIndex = matches[11].index
    val factory = matches[11].methodReference
    val call = matches[11].instruction.registersUsed
    val listRegister = matches[13].instruction.registersUsed.first()
    if (call != (call.first()..call.last()).toList()) {
        throw PatchException("Non-consecutive component factory registers in $builder")
    }

    // Reuse the staging register only if it is dead until the native assignment
    // and its source remains unchanged.
    val wrapperCall = matches[10].instruction.registersUsed
    if (wrapperCall.size != iconWrapperConstructor.parameterTypes.size + 1 ||
        wrapperCall.take(6) != call.drop(1) ||
        wrapperCall != (wrapperCall.first()..wrapperCall.last()).toList()
    ) {
        throw PatchException("Unexpected icon wrapper registers in $builder")
    }
    val wrapperFlagSource =
        if (wrapperCall.size == 7) {
            val flagRegister = wrapperCall.last()
            val assignmentIndex = builder.indexOfFirstInstructionReversedOrThrow(wrapperIndex - 1) {
                writeRegister == flagRegister
            }
            if (assignmentIndex < onClickIndex) throw PatchException("No native icon flag assignment in $builder")
            val assignment = instructions[assignmentIndex]
            if (assignment.opcode !in listOf(Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16)) {
                throw PatchException("Unexpected native icon flag assignment in $builder")
            }
            val source = assignment.registersUsed[1]
            if (instructions.subList(onClickIndex, assignmentIndex).any { flagRegister in it.registersUsed } ||
                instructions.subList(onClickIndex, wrapperIndex).any {
                    it.opcode.setsRegister() && it.registersUsed.firstOrNull() == source
                } || source in call
            ) {
                throw PatchException("Native icon flag registers are not safe to reuse in $builder")
            }
            source
        } else {
            null
        }

    // The last five factory arguments are staged by the moves right before it.
    val sources =
        call.drop(2).map { destination ->
            val moveIndex = builder.indexOfFirstInstructionReversedOrThrow(factoryIndex - 1) {
                this is TwoRegisterInstruction && registerA == destination
            }
            if (moveIndex < factoryIndex - 8) {
                throw PatchException("No source move for component argument v$destination in $builder")
            }
            builder.getInstruction<TwoRegisterInstruction>(moveIndex).registerB
        }
    val userSessionRegister = sources[2]

    val stateRegister = builder.getInstruction<OneRegisterInstruction>(
        builder.indexOfFirstInstructionOrThrow(checkCast(stateType)),
    ).registerA
    val componentContext = builder.p0Register + 1
    val contextGetter =
        requireOne(
            "component context accessor",
            patchContext.classDefBy(themeAccessor.parameterTypes[0].toString()).methods.filter { method ->
                method.parameterTypes.isEmpty() && method.returnType == CONTEXT_DESCRIPTOR
            },
        )

    val excluded =
        (builder.parameterBlock() + nodeRegister + stateRegister + listRegister + call + wrapperCall +
            sources + listOfNotNull(wrapperFlagSource)).distinct()
    val scratch = builder.reserveFreeRegisters(onClickIndex, 7, maximum = 255, excluded = excluded)
    val flag = scratch[0] // also the constant staging register
    val handler = scratch[1]
    val context = scratch[2]
    val node = scratch[3] // the download icon node, then the finished component
    val scaleType = scratch[4]
    val dimension = scratch[5]
    val tint = scratch[6]

    builder.addInstructionsWithLabels(
        onClickIndex,
        """
            invoke-static {}, $FEED_DOWNLOAD_ENABLED
            move-result v$flag
            if-eqz v$flag, :skip

            new-instance v$handler, $CLICK_HANDLER_DESCRIPTOR
            invoke-interface/range ${registerRange(componentContext)}, $contextGetter
            move-result-object v$context
            ${moveObject(call[1], handler)}
            ${moveObject(call[2], context)}
            ${moveObject(call[3], userSessionRegister)}
            ${moveObject(call[4], stateRegister)}
            invoke-direct/range ${registerRange(call[1], call[4])}, $CLICK_HANDLER_CONSTRUCTOR

            const v$flag, -1
            ${moveObject(call[1], nodeRegister)}
            ${moveInt(call[2], flag)}
            invoke-static/range ${registerRange(call[1], call[2])}, $idSetter
            move-result-object v$node

            const-string v$flag, "$DOWNLOAD_CONTENT_DESCRIPTION"
            ${moveObject(call[1], node)}
            ${moveObject(call[2], flag)}
            invoke-static/range ${registerRange(call[1], call[2])}, $descriptionSetter
            move-result-object v$node

            ${moveObject(call[1], node)}
            ${moveObject(call[2], handler)}
            invoke-static/range ${registerRange(call[1], call[2])}, $onClickSetter
            move-result-object v$node

            sget-object v$scaleType, $CENTER_SCALE_TYPE

            const v$flag, $dimensionAttribute
            ${moveObject(call[1], componentContext)}
            ${moveInt(call[2], flag)}
            invoke-static/range ${registerRange(call[1], call[2])}, $themeAccessor
            move-result v$dimension

            const v$flag, $tintAttribute
            ${moveObject(call[1], componentContext)}
            ${moveInt(call[2], flag)}
            invoke-static/range ${registerRange(call[1], call[2])}, $themeAccessor
            move-result v$tint
            invoke-static/range ${registerRange(tint)}, $INTEGER_VALUE_OF
            move-result-object v$tint

            const v$flag, ${downloadDrawableId.toInt()}
            new-instance v${call[1]}, $iconWrapperClass
            ${moveObject(call[2], scaleType)}
            ${moveObject(call[3], node)}
            ${moveObject(call[4], tint)}
            ${moveInt(call[5], flag)}
            ${moveInt(call[6], dimension)}
            ${wrapperFlagSource?.let { moveInt(wrapperCall.last(), it) }.orEmpty()}
            invoke-direct/range ${registerRange(wrapperCall.first(), wrapperCall.last())}, $iconWrapperConstructor

            ${moveObject(call[2], sources[0])}
            ${moveObject(call[3], sources[1])}
            ${moveObject(call[4], sources[2])}
            ${moveInt(call[5], sources[3])}
            ${moveInt(call[6], sources[4])}
            invoke-static/range ${registerRange(call[0], call[6])}, $factory
            move-result-object v$node

            ${moveObject(call[1], listRegister)}
            ${moveObject(call[2], node)}
            invoke-virtual/range ${registerRange(call[1], call[2])}, $COMPONENT_LIST_ADD
        """.trimIndent(),
        ExternalLabel("skip", builder.getInstruction(onClickIndex)),
    )
}
