package app.crimera.patches.newx.misc.mediasheet

import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.settings.MultiChoiceSettingDefinition
import app.crimera.patches.settings.SettingReadRegisterConstraint
import app.crimera.patches.settings.choice
import app.crimera.patches.settings.injectRead
import app.crimera.patches.settings.multiChoice
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.MEDIA_SHEET_FILTER_DESCRIPTOR
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.literal
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.util.getReference
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val MEDIA_SHEET_FILTER_SIGNATURE =
    "filter(Ljava/util/List;Ljava/util/Set;)Ljava/util/List;"
private const val LABEL_BRIDGE_NAME = "getLabelResourceId"
private const val IMMUTABLE_LIST_DESCRIPTOR = "Lkotlinx/collections/immutable/b;"
private const val ITERABLE_DESCRIPTOR = "Ljava/lang/Iterable;"
private const val INT_DESCRIPTOR = "I"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"

/** The label initializer sits directly after its resource literal; the window only guards churn. */
private const val LABEL_INIT_WINDOW = 8

/** The row constructor follows the label initializer within one branch. */
private const val ITEM_CONSTRUCTOR_WINDOW = 16

private val DIRECT_INVOKE_OPCODES = setOf(Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE)
private val STATIC_INVOKE_OPCODES = setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)

/**
 * Labels always rendered by the media long-press sheet. Requiring two of them pins the builder
 * without depending on its Compose parameter list.
 */
private const val MEDIA_SHEET_BUILDER_ANCHOR = "post_photo"
private val MEDIA_SHEET_BUILDER_ANCHORS = listOf(MEDIA_SHEET_BUILDER_ANCHOR, "save_4k_photo")

/**
 * Every hideable sheet label. The option ID is the string-resource entry name, which is what the
 * extension compares against the runtime label, so an upstream resource rename fails validation.
 */
private val MEDIA_SHEET_OPTIONS =
    listOf(
        "post_photo" to "piko_newx_media_sheet_post_photo",
        "gif_fab_label" to "piko_newx_media_sheet_post_gif",
        "post_video" to "piko_newx_media_sheet_post_video",
        "copy_gif_link" to "piko_newx_media_sheet_copy_gif_link",
        "copy_video_link" to "piko_newx_media_sheet_copy_video_link",
        "copy_photo" to "piko_newx_media_sheet_copy_photo",
        "save" to "piko_newx_media_sheet_save",
        "save_photo" to "piko_newx_media_sheet_save_photo",
        "save_video" to "piko_newx_media_sheet_save_video",
        "save_4k_photo" to "piko_newx_media_sheet_save_4k_photo",
        "twitter_share" to "piko_newx_media_sheet_share",
        "make_video_with_grok" to "piko_newx_media_sheet_make_video_with_grok",
        "edit_image_with_grok" to "piko_newx_media_sheet_edit_image_with_grok",
        "add_to_offline_videos" to "piko_newx_media_sheet_add_to_offline_videos",
        "reload_to_offline_videos" to "piko_newx_media_sheet_reload_to_offline_videos",
        "remove_from_offline_videos" to "piko_newx_media_sheet_remove_from_offline_videos",
    )

private object MediaSheetBuilderFingerprint : Fingerprint(
    returnType = "V",
    // The patches library's resourceLiteral is not in the patcher's bundled-filter set, so it cannot
    // use the literal index; the lazy patcher literal resolves the same id at match time.
    filters =
        MEDIA_SHEET_BUILDER_ANCHORS.map { name ->
            literal({ getResourceId(ResourceType.STRING, name) })
        },
)

/** Release-specific access paths for one media sheet row. */
private data class ResolvedMediaSheetLabel(
    val rowDescriptor: String,
    val labelDescriptor: String,
    val labelGetter: MethodReference,
    val labelResourceField: FieldReference,
)

@Suppress("unused")
val customizeNewXMediaSheetPatch =
    bytecodePatch(
        name = "NewX: Customize media menu items",
        description = "Lets you hide selected items from the NewX photo and video long-press menu.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        val hiddenItems =
            newXSettings {
                category(Categories.POST_ACTIONS_MEDIA) {
                    multiChoice(
                        id = "newx.content.hidden_media_sheet_items",
                        strings = settingStrings("piko_newx_media_sheet_items"),
                        order = 160,
                        defaultValue = emptySet(),
                        options =
                            MEDIA_SHEET_OPTIONS.map { (resourceName, titleResourceName) ->
                                choice(resourceName, titleResourceName)
                            },
                    )
                }
            }

        execute {
            validateMediaSheetResources()
            val match =
                requireExactlyOne(
                    label = "NewX media sheet builder",
                    candidates = MediaSheetBuilderFingerprint.scopedMatchAll(),
                )
            val labelModel = resolveMediaSheetLabelModel(match)
            filterMediaSheetOptions(match.method, hiddenItems)
            patchMediaSheetLabelBridge(labelModel)
        }
    }

private fun validateMediaSheetResources() {
    // The extension compares resource entry names, so a missing or renamed label must fail the
    // patch instead of leaving a dead option in the settings screen.
    MEDIA_SHEET_OPTIONS.forEach { (resourceName, _) ->
        getResourceId(ResourceType.STRING, resourceName)
    }
}

context(context: BytecodePatchContext)
private fun resolveMediaSheetLabelModel(match: Match): ResolvedMediaSheetLabel {
    val method = match.method
    val anchorName = MEDIA_SHEET_BUILDER_ANCHOR
    val anchorResourceId = getResourceId(ResourceType.STRING, anchorName)
    val anchorIndices =
        method.instructions.mapIndexedNotNull { index, instruction ->
            index.takeIf {
                (instruction as? NarrowLiteralInstruction)?.narrowLiteral?.toLong() == anchorResourceId
            }
        }
    val anchorIndex = requireExactlyOne("NewX media-sheet $anchorName literal", anchorIndices)

    val labelInitializers =
        method.instructions.mapIndexedNotNull { index, instruction ->
            if (index <= anchorIndex || index - anchorIndex > LABEL_INIT_WINDOW) {
                return@mapIndexedNotNull null
            }
            if (instruction.opcode !in DIRECT_INVOKE_OPCODES) return@mapIndexedNotNull null
            val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
            (index to reference).takeIf {
                it.second.name == "<init>" &&
                    it.second.returnType == "V" &&
                    it.second.parameterTypes.map(CharSequence::toString) == listOf(INT_DESCRIPTOR)
            }
        }
    val labelInitializer =
        requireExactlyOne("NewX media-sheet label initializer after $anchorName", labelInitializers)
    val labelInitializerIndex = labelInitializer.first
    val labelInitializerReference = labelInitializer.second

    val labelClass =
        context.classDefByOrNull(labelInitializerReference.definingClass)
            ?: throw PatchException(
                "NewX media-sheet label class was not found: " +
                    "${labelInitializerReference.definingClass}",
            )
    val labelBase =
        labelClass.superclass
            ?.takeIf { superclass -> superclass != "Ljava/lang/Object;" }
            ?: labelClass.type

    val rowConstructors =
        method.instructions.mapIndexedNotNull { index, instruction ->
            if (index <= labelInitializerIndex || index - labelInitializerIndex > ITEM_CONSTRUCTOR_WINDOW) {
                return@mapIndexedNotNull null
            }
            if (instruction.opcode !in DIRECT_INVOKE_OPCODES) return@mapIndexedNotNull null
            val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
            reference.takeIf {
                it.name == "<init>" &&
                    it.returnType == "V" &&
                    it.definingClass != labelInitializerReference.definingClass &&
                    it.parameterTypes.map(CharSequence::toString).any { parameter -> parameter == labelBase }
            }
        }
    val rowConstructor = requireExactlyOne("NewX media-sheet row constructor for $labelBase", rowConstructors)
    val rowClass =
        context.classDefByOrNull(rowConstructor.definingClass)
            ?: throw PatchException("NewX media-sheet row class was not found: ${rowConstructor.definingClass}")

    val labelGetters =
        rowClass.methods.filter { candidate ->
            candidate.parameterTypes.isEmpty() && candidate.returnType == labelBase
        }
    val labelGetter = requireExactlyOne("NewX media-sheet label getter in ${rowClass.type}", labelGetters)

    val labelResourceFields =
        labelClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) && field.type == INT_DESCRIPTOR
        }
    val labelResourceField =
        requireExactlyOne("NewX media-sheet label resource field in ${labelClass.type}", labelResourceFields)

    return ResolvedMediaSheetLabel(
        rowDescriptor = rowClass.type,
        labelDescriptor = labelClass.type,
        labelGetter = labelGetter,
        labelResourceField = labelResourceField,
    )
}

context(_: BytecodePatchContext)
private fun filterMediaSheetOptions(
    method: MutableMethod,
    hiddenItems: MultiChoiceSettingDefinition,
) {
    val conversions =
        method.instructions.mapIndexedNotNull { index, instruction ->
            if (instruction.opcode !in STATIC_INVOKE_OPCODES) return@mapIndexedNotNull null
            val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
            val parameters = reference.parameterTypes.map(CharSequence::toString)
            (index to reference).takeIf {
                reference.returnType == IMMUTABLE_LIST_DESCRIPTOR &&
                    parameters.size == 1 &&
                    parameters[0] == ITERABLE_DESCRIPTOR
            }
        }
    val conversion = requireExactlyOne("NewX media-sheet immutable list conversion", conversions)
    val conversionIndex = conversion.first
    val conversionReference = conversion.second

    val resultIndex = conversionIndex + 1
    val resultInstruction = method.instructions.getOrNull(resultIndex)
    if (resultInstruction?.opcode != Opcode.MOVE_RESULT_OBJECT) {
        throw PatchException(
            "NewX media-sheet immutable list conversion has no object result in $method: " +
                "$resultInstruction",
        )
    }
    val resultRegister = (resultInstruction as OneRegisterInstruction).registerA

    val insertionIndex = resultIndex + 1
    val implementation =
        method.implementation
            ?: throw PatchException("NewX media-sheet builder has no implementation: $method")
    if (insertionIndex >= implementation.instructions.size ||
        implementation.instructions[insertionIndex].location.labels.isNotEmpty()
    ) {
        throw PatchException(
            "NewX media-sheet list insertion point carries branch labels; refusing to filter " +
                "only some paths in $method",
        )
    }

    val read =
        hiddenItems.injectRead(
            method = method,
            index = insertionIndex,
            excludedRegisters = listOf(resultRegister),
            registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
        )
    method.insertHook(read.nextIndex, relocateBranchTargets = false) {
        invokeStatic(
            methodReference("$MEDIA_SHEET_FILTER_DESCRIPTOR->$MEDIA_SHEET_FILTER_SIGNATURE"),
            resultRegister,
            read.register,
        )
        moveResult(resultRegister, LIST_DESCRIPTOR)
        // The sheet renderer expects the app's immutable list type, so reuse the resolved
        // conversion instead of handing it the extension's ArrayList.
        invokeStatic(conversionReference, resultRegister)
        moveResult(resultRegister, IMMUTABLE_LIST_DESCRIPTOR)
    }
}

context(context: BytecodePatchContext)
private fun patchMediaSheetLabelBridge(model: ResolvedMediaSheetLabel) {
    val extensionClass = context.mutableClassDefBy(MEDIA_SHEET_FILTER_DESCRIPTOR)
    val bridges =
        extensionClass.methods.filter { method ->
            method.name == LABEL_BRIDGE_NAME &&
                method.parameterTypes.map(CharSequence::toString) == listOf(OBJECT_DESCRIPTOR) &&
                method.returnType == INT_DESCRIPTOR
        }
    val bridge = requireExactlyOne("NewX media-sheet label bridge in $MEDIA_SHEET_FILTER_DESCRIPTOR", bridges)
    val register = bridge.p0Register
    if (register > 15) {
        throw PatchException(
            "NewX media-sheet label bridge needs a 4-bit parameter register, found v$register in " +
                "$bridge",
        )
    }

    bridge.insertHook(0, relocateBranchTargets = false) {
        checkCast(register, model.rowDescriptor)
        invokeVirtual(model.labelGetter, register)
        moveResult(register, model.labelGetter.returnType)
        checkCast(register, model.labelDescriptor)
        iget(register, register, model.labelResourceField)
        returnValue(register)
    }
}
