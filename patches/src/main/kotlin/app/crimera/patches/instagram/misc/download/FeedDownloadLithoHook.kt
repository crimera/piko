/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.shared.declaredParameterRegister
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction31i
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

/** Node type the UFI icon chain is built from. */
private const val NODE_DESCRIPTOR = "LX/03iH;"

/** Compiled component the UFI icon chain resolves to. */
private const val COMPONENT_DESCRIPTOR = "LX/03Wk;"

/** Kotlin event handler type the UFI `ON_CLICK` setter takes. */
private const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val CONTEXT_DESCRIPTOR = "Landroid/content/Context;"

/** View class the UFI icon builder mounts; precedes the save icon's setters. */
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

/** The Litho image wrapper the UFI icon builder passes its node through. */
private val ICON_WRAPPER_PARAMETERS =
    listOf("Landroid/widget/ImageView\$ScaleType;", NODE_DESCRIPTOR, INTEGER_DESCRIPTOR, "I", "I")

/**
 * Surfaces such as the contextual profile feed render the UFI as a Litho component, which rejects
 * views added by hand. The download icon is built into the component tree instead: the UFI builder
 * gets a second icon node, set up exactly like the save icon, in front of it.
 *
 * Every obfuscated member is resolved from the instructions that build the save icon. The save
 * icon's consecutive component-call registers are rebuilt right after the injection point, so the
 * block reuses them to stage every invoke instead of searching for a free span in a dense method.
 *
 * @param stateType the feed row state the builder casts its input to.
 */
context(patchContext: BytecodePatchContext)
internal fun injectLithoDownloadButton(
    saveButtonId: Long,
    downloadDrawableId: Long,
    stateType: String,
) {
    val iconWrapperConstructor =
        requireOne(
            "UFI icon wrapper constructor",
            buildList {
                patchContext.classDefForEach { classDef ->
                    classDef.methods.filterTo(this) { method ->
                        method.name == "<init>" &&
                            method.parameterTypes.map { it.toString() } == ICON_WRAPPER_PARAMETERS
                    }
                }
            },
        )
    val iconWrapperClass = iconWrapperConstructor.definingClass

    val builder =
        requireOne(
            "UFI litho component builder",
            Fingerprint(filters = listOf(literal(saveButtonId)))
                .matchAll()
                .filter { match ->
                    match.method.implementation?.instructions?.any { it.methodRef()?.definingClass == iconWrapperClass } == true
                }.map { it.method },
        )
    val instructions =
        builder.implementation?.instructions?.toList()
            ?: throw PatchException("Litho UFI builder $builder has no implementation")

    val saveIndex =
        instructions.indexOfFirst { it is Instruction31i && it.wideLiteral == saveButtonId }
    if (saveIndex < 0) throw PatchException("No save-button literal in $builder")

    fun indexAfterSave(
        what: String,
        after: Int = saveIndex,
        matches: (MethodReference) -> Boolean,
    ): Int =
        (after + 1 until instructions.size).firstOrNull { index -> instructions[index].methodRef()?.let(matches) == true }
            ?: throw PatchException("No $what after index $after in $builder")

    fun setterIndex(valueType: String) =
        indexAfterSave("icon setter taking $valueType") { reference ->
            reference.returnType == NODE_DESCRIPTOR &&
                reference.parameterTypes.map { it.toString() } == listOf(NODE_DESCRIPTOR, valueType)
        }

    val idSetter = instructions[setterIndex("I")].methodRef()!!
    val onClickIndex = setterIndex(FUNCTION1_DESCRIPTOR)
    val onClickSetter = instructions[onClickIndex].methodRef()!!

    // The save icon sets its content description two instructions before it loads the button view
    // class. The node handed to its ON_CLICK call has no click props yet, so deriving the download
    // node from it does not inherit the save behaviour.
    val viewClassIndex =
        (saveIndex - 1 downTo 0).firstOrNull { instructions[it].getReference<StringReference>()?.string == BUTTON_VIEW_CLASS }
            ?: throw PatchException("No \"$BUTTON_VIEW_CLASS\" before the save literal in $builder")
    val descriptionSetter =
        instructions.getOrNull(viewClassIndex - 2)?.methodRef()
            ?: throw PatchException("No content description setter before index $viewClassIndex in $builder")
    val nodeRegister =
        instructions[onClickIndex].registers().firstOrNull()
            ?: throw PatchException("ON_CLICK call at $onClickIndex has no node in $builder")

    // The component factory call that follows the wrapper construction takes seven consecutive
    // registers. Its last five arguments are staged by the moves right before it.
    val wrapperIndex =
        indexAfterSave("icon wrapper construction", after = onClickIndex) {
            it.definingClass == iconWrapperClass && it.name == "<init>"
        }
    val factoryIndex =
        indexAfterSave("component factory call", after = wrapperIndex) { it.returnType == COMPONENT_DESCRIPTOR }
    val factory = instructions[factoryIndex].methodRef()!!
    val call = instructions[factoryIndex].registers()
    if (call.size != 7) throw PatchException("Unexpected component factory call shape in $builder: ${call.size} registers")

    val sources =
        call.drop(2).map { destination ->
            (factoryIndex - 1 downTo (factoryIndex - 8).coerceAtLeast(0))
                .firstNotNullOfOrNull { index ->
                    (instructions[index] as? TwoRegisterInstruction)?.takeIf { it.registerA == destination }?.registerB
                } ?: throw PatchException("No source move for component argument v$destination in $builder")
        }
    // The third argument is the `UserSession` the click handler needs.
    val userSessionRegister = sources[2]

    val listAddIndex =
        indexAfterSave("icon list add", after = factoryIndex) {
            it.name == "add" && it.returnType == "Z" && it.parameterTypes.map { type -> type.toString() } == listOf(OBJECT_DESCRIPTOR)
        }
    val listRegister =
        instructions[listAddIndex].registers().firstOrNull()
            ?: throw PatchException("Icon list add at $listAddIndex has no receiver in $builder")

    val stateRegister =
        instructions
            .firstOrNull { it.opcode == Opcode.CHECK_CAST && it.getReference<TypeReference>()?.type == stateType }
            ?.let { (it as OneRegisterInstruction).registerA }
            ?: throw PatchException("No check-cast to $stateType in $builder")

    // The save icon resolves its size and tint through two theme attribute lookups right before
    // the wrapper is built; the same lookups supply the download icon's values.
    val themeCalls =
        (saveIndex until factoryIndex).mapNotNull { index ->
            val reference = instructions[index].methodRef() ?: return@mapNotNull null
            val parameters = reference.parameterTypes.map { it.toString() }
            if (instructions[index].opcode == Opcode.INVOKE_STATIC &&
                reference.returnType == "I" &&
                parameters.size == 2 &&
                parameters[1] == "I"
            ) {
                index to reference
            } else {
                null
            }
        }
    if (themeCalls.size != 2 || !themeCalls[0].second.sameSignatureAs(themeCalls[1].second)) {
        throw PatchException("Expected two matching theme attribute lookups in $builder, found ${themeCalls.size}")
    }
    val themeAccessor = themeCalls[0].second

    fun attributeLiteral(callIndex: Int): Int =
        (callIndex - 1 downTo (callIndex - 4).coerceAtLeast(0))
            .firstNotNullOfOrNull { (instructions[it] as? Instruction31i)?.wideLiteral?.toInt() }
            ?: throw PatchException("No theme attribute constant before index $callIndex in $builder")

    val dimensionAttribute = attributeLiteral(themeCalls[0].first)
    val tintAttribute = attributeLiteral(themeCalls[1].first)

    // The builder's only parameter is the component context, still intact at the injection point.
    if (builder.parameterTypes.size != 1) throw PatchException("Expected one parameter on $builder")
    val componentContext = declaredParameterRegister(builder, 0)
    val contextGetter =
        requireOne(
            "component context accessor",
            patchContext.classDefBy(themeAccessor.parameterTypes[0].toString()).methods.filter { method ->
                method.parameterTypes.isEmpty() && method.returnType == CONTEXT_DESCRIPTOR
            },
        )

    val excluded =
        (builder.parameterBlock() + nodeRegister + stateRegister + listRegister + call + sources).distinct()
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
            invoke-direct/range ${registerRange(call[1], call[6])}, $iconWrapperConstructor

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
