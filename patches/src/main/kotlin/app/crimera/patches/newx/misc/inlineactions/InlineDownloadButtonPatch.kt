package app.crimera.patches.newx.misc.inlineactions

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.misc.extension.newXInitHook
import app.crimera.patches.newx.models.ResolvedNewXInlineActionBarModels
import app.crimera.patches.newx.models.ResolvedNewXInlineActionKindOverride
import app.crimera.patches.newx.models.ResolvedNewXInlineActionModels
import app.crimera.patches.newx.models.ResolvedNewXInlineDownloadModels
import app.crimera.patches.newx.models.ResolvedNewXPostMediaModels
import app.crimera.patches.newx.models.ResolvedNewXPostModels
import app.crimera.patches.newx.models.firstParameterSlot
import app.crimera.patches.newx.models.isInlineActionEntryRenderer
import app.crimera.patches.newx.models.requirePublicFields
import app.crimera.patches.newx.models.resolveMutableMethodOwner
import app.crimera.patches.newx.models.resolvedNewXInlineActionBarModels
import app.crimera.patches.newx.models.resolvedNewXInlineActionKindOverride
import app.crimera.patches.newx.models.resolvedNewXInlineActionModels
import app.crimera.patches.newx.models.resolvedNewXInlineDownloadModels
import app.crimera.patches.newx.models.resolvedNewXPostMediaModels
import app.crimera.patches.newx.models.resolvedNewXPostModels
import app.crimera.patches.newx.models.newXInlineDownloadModelResolutionPatch
import app.crimera.patches.newx.models.newXPostMediaModelResolutionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.choice
import app.crimera.patches.newx.settings.customScreen
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.input
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.singleChoice
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.utils.Constants.DOWNLOAD_OPTIONS_FRAGMENT_DESCRIPTOR
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.util.cloneMutable
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val MODIFIER = "Landroidx/compose/ui/Modifier;"
private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
private const val RESOURCES_DESCRIPTOR = "Landroid/content/res/Resources;"
private const val EXTENSION = "Lapp/morphe/extension/newx/misc/InlineDownloadButton;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val PRESENTER_POST_HELPER = "getPresenterPost"
private const val CANONICAL_POST_HELPER = "getCanonicalPost"
private const val POST_MEDIA_HELPER = "getPostMedia"
private const val REPOSTED_POST_HELPER = "getRepostedPost"
private const val REPOSTED_CANONICAL_POST_HELPER = "getRepostedCanonicalPost"
private const val CREATE_ACTION_HELPER = "createDownloadAction"

private fun MutableMethod.requireStatic(label: String) {
    if (AccessFlags.STATIC.isSet(accessFlags)) return
    throw PatchException("$label is no longer static: $this")
}

@Suppress("unused")
val newXInlineDownloadButtonPatch =
    bytecodePatch(
        name = "NewX: Inline download button",
        description =
            "Adds a Download button below NewX posts and saves media to a folder you choose, " +
                "with a customizable filename template.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(
            customizeNewXInlineActionsPatch,
            newXPostMediaModelResolutionPatch,
            newXInlineDownloadModelResolutionPatch,
            newXThumbnailCachePatch,
            newXInAppNotificationPatch,
            newXExtensionPatch,
        )

        newXSettings {
            category(Categories.POST_ACTIONS_MEDIA) {
                group(Groups.INLINE_DOWNLOAD) {
                    toggle(
                        id = "newx.content.inline_download_button",
                        strings = settingStrings("piko_newx_inline_download_button"),
                        order = 100,
                        defaultValue = true,
                    )
                    toggle(
                        id = "newx.content.inline_download_hide_no_media",
                        strings = settingStrings("piko_newx_inline_download_hide_no_media"),
                        order = 150,
                        defaultValue = true,
                    )
                    toggle(
                        id = "newx.content.media_picker_copy_link",
                        strings = settingStrings("piko_newx_media_picker_copy_link"),
                        order = 200,
                        defaultValue = true,
                    )
                    toggle(
                        id = "newx.content.media_picker_resolution_button",
                        strings = settingStrings("piko_newx_media_picker_resolution_button"),
                        order = 250,
                        defaultValue = true,
                    )
                    toggle(
                        id = "newx.content.media_picker_thumbnails",
                        strings = settingStrings("piko_newx_media_picker_thumbnails"),
                        order = 300,
                        defaultValue = true,
                    )
                    toggle(
                        id = "newx.content.media_picker_merge_button",
                        strings = settingStrings("piko_newx_media_picker_merge_button"),
                        order = 350,
                        defaultValue = true,
                    )
                    // Preferred quality for downloads that skip the picker. "ask" keeps the
                    // resolution chooser; any other value auto-selects the matching option.
                    singleChoice(
                        id = "newx.content.inline_download.image_resolution",
                        strings = settingStrings("piko_newx_inline_download_image_resolution"),
                        order = 360,
                        defaultValue = "original",
                        options =
                            listOf(
                                choice("ask", "piko_newx_inline_download_quality_ask"),
                                choice("original", "piko_newx_inline_download_quality_original"),
                                choice("4096x4096", "piko_newx_inline_download_quality_4096"),
                                choice("large", "piko_newx_inline_download_quality_large"),
                                choice("medium", "piko_newx_inline_download_quality_medium"),
                                choice("small", "piko_newx_inline_download_quality_small"),
                            ),
                    )
                    // Videos and GIFs share the same variant model, so one policy covers both.
                    singleChoice(
                        id = "newx.content.inline_download.video_quality",
                        strings = settingStrings("piko_newx_inline_download_video_quality"),
                        order = 370,
                        defaultValue = "highest",
                        options =
                            listOf(
                                choice("ask", "piko_newx_inline_download_quality_ask"),
                                choice("highest", "piko_newx_inline_download_quality_highest"),
                                choice("1080p", "piko_newx_inline_download_quality_1080p"),
                                choice("720p", "piko_newx_inline_download_quality_720p"),
                                choice("480p", "piko_newx_inline_download_quality_480p"),
                                choice("360p", "piko_newx_inline_download_quality_360p"),
                                choice("lowest", "piko_newx_inline_download_quality_lowest"),
                            ),
                    )
                    singleChoice(
                        id = "newx.content.inline_download_conflict",
                        strings = settingStrings("piko_newx_inline_download_conflict"),
                        order = 400,
                        defaultValue = "skip",
                        options =
                            listOf(
                                choice("overwrite", "piko_newx_inline_download_conflict_overwrite"),
                                choice("rename", "piko_newx_inline_download_conflict_rename"),
                                choice("skip", "piko_newx_inline_download_conflict_skip"),
                            ),
                    )
                    // Folders and the filename template need richer editors than a settings row, so
                    // they live in the Download options screen.
                    customScreen(
                        id = "newx.content.inline_download.options",
                        strings = settingStrings("piko_newx_inline_download_options"),
                        order = 410,
                        fragmentClassDescriptor = DOWNLOAD_OPTIONS_FRAGMENT_DESCRIPTOR,
                    )
                    // The template is edited only from the Download options screen, which owns the
                    // token chips and live preview. It stays registered as a setting so
                    // "back up settings" carries it, but is hidden from the list.
                    input(
                        id = "newx.content.inline_download.filename_template",
                        strings = settingStrings("piko_newx_inline_download_filename_template"),
                        order = 600,
                        defaultValue = "{screenName}_{id}",
                        visible = false,
                    )
                    // Hidden nodes store the SAF destination for each media type. They are declared
                    // as registry settings so "back up settings" carries them between installs.
                    input(
                        id = "newx.content.inline_download.images_tree_uri",
                        strings = settingStrings("piko_newx_inline_download_images_tree_uri"),
                        order = 700,
                        defaultValue = "",
                        visible = false,
                    )
                    input(
                        id = "newx.content.inline_download.videos_tree_uri",
                        strings = settingStrings("piko_newx_inline_download_videos_tree_uri"),
                        order = 710,
                        defaultValue = "",
                        visible = false,
                    )
                    input(
                        id = "newx.content.inline_download.images_display_path",
                        strings = settingStrings("piko_newx_inline_download_images_display_path"),
                        order = 720,
                        defaultValue = "",
                        visible = false,
                    )
                    input(
                        id = "newx.content.inline_download.videos_display_path",
                        strings = settingStrings("piko_newx_inline_download_videos_display_path"),
                        order = 730,
                        defaultValue = "",
                        visible = false,
                    )
                }
            }
        }

        execute {
            val entryModels = resolvedNewXInlineActionModels()
            val barModels = resolvedNewXInlineActionBarModels()
            val mediaModels = resolvedNewXPostMediaModels()
            val downloadModels = resolvedNewXInlineDownloadModels()
            val kindOverrideModels = resolvedNewXInlineActionKindOverride()
            patchPostModelBridges(
                resolvedNewXPostModels(),
                entryModels,
                barModels,
                mediaModels,
                downloadModels,
            )
            // Null on validated legacy targets whose boolean-only kind model has no IconOnly
            // enum to rewrite (12.27/12.28). Only the 12.29 enum contract needs the override.
            if (kindOverrideModels != null) {
                patchInlineActionKindOverride(entryModels, kindOverrideModels)
            }
            val applicationOnCreate = newXInitHook.fingerprint.method
            applicationOnCreate.insertHook(0, relocateBranchTargets = false) {
                invokeStatic(
                    methodReference("$EXTENSION->initialize(Landroid/content/Context;)V"),
                    applicationOnCreate.p0Register,
                )
            }

            val inlineRenderer = requireExactlyOne(
                "NewX inline-action entry renderer",
                Fingerprint(
                    returnType = "V",
                    custom = { method, _ ->
                        method.isInlineActionEntryRenderer(entryModels.inlineActionEntryDescriptor)
                    },
                    filters =
                        listOf(
                            fieldAccess(
                                opcode = Opcode.IGET_OBJECT,
                                reference = entryModels.inlineActionTypeField,
                            ),
                            fieldAccess(
                                opcode = Opcode.IGET_BOOLEAN,
                                reference = entryModels.inlineActionEnabledField,
                            ),
                        ),
                ).scopedMatchAll(),
            )
            inlineRenderer.method.apply {
                requireStatic("NewX inline-action entry renderer")
                // Icon-size float slot; Compose inserts auxiliary params between releases. The
                // helper returns a p-index, so the absolute register is p0 plus that slot.
                val sizeRegister = p0Register + firstParameterSlot("F")
                insertHook(0, relocateBranchTargets = false) {
                    val entryRegister = scratchRegister()
                    val workRegister = scratchRegister()
                    move(entryRegister, p0Register, OBJECT_DESCRIPTOR)
                    move(workRegister, sizeRegister, "F")
                    invokeStatic(
                        methodReference("$EXTENSION->markIconSize(Ljava/lang/Object;F)F"),
                        entryRegister,
                        workRegister,
                    )
                    moveResult(workRegister, "F")
                    move(sizeRegister, workRegister, "F")
                }
            }

            // The injected entry deliberately carries TwitterShare, which maps to exactly this
            // ic_vector_share branch. Share/ic_vector_share_android is a separate native action
            // and must remain untouched. The sign of the captured size is the complete download
            // discriminator; normalize it before layout and read the original sign at icon choice.
            val shareIconField = resolveIconField("ic_vector_share")
            val incomingIconField = resolveIconField("ic_vector_incoming_stroke")
            if (shareIconField.type != incomingIconField.type) {
                throw PatchException("NewX inline icon types differ")
            }
            val iconRenderer = requireExactlyOne(
                "NewX TwitterShare icon lambda",
                Fingerprint(
                    filters =
                        listOf(
                            fieldAccess(opcode = Opcode.IGET, definingClass = "this", type = "F"),
                            fieldAccess(opcode = Opcode.SGET_OBJECT, reference = shareIconField),
                        ),
                ).scopedMatchAll(),
            )
            iconRenderer.method.apply {
                val iconAccess = requireExactlyOne(
                    "NewX TwitterShare icon access",
                    iconRenderer.instructionMatches.filter { match ->
                        match.instruction.opcode == Opcode.SGET_OBJECT &&
                            match.instruction.getReference<FieldReference>()?.toString() ==
                            shareIconField.toString()
                    },
                )
                val matchedSizeAccess = requireExactlyOne(
                    "NewX TwitterShare icon size field access",
                    iconRenderer.instructionMatches.filter { match ->
                        val field = match.instruction.getReference<FieldReference>()
                        match.instruction.opcode == Opcode.IGET && field?.type == "F"
                    },
                )
                val sizeField =
                    matchedSizeAccess.instruction.getReference<FieldReference>()
                        ?: throw PatchException("NewX share icon size field was not found")
                // newx-resolver-lint: allow instruction-order previous-return because each
                // packed-switch icon branch is a self-contained block ending in return-object.
                val branchStart =
                    instructions
                        .subList(0, iconAccess.index)
                        .indexOfLast { instruction -> instruction.opcode == Opcode.RETURN_OBJECT } + 1
                val branchLocalSizeAccesses = instructions.mapIndexedNotNull { index, instruction ->
                    index.takeIf {
                        index in branchStart until iconAccess.index &&
                            instruction.opcode == Opcode.IGET &&
                            instruction.getReference<FieldReference>()?.toString() ==
                            sizeField.toString()
                    }?.let { accessIndex -> accessIndex to instruction }
                }
                val packedSwitchIndexes = instructions.mapIndexedNotNull { index, instruction ->
                    index.takeIf {
                        index < iconAccess.index && instruction.opcode == Opcode.PACKED_SWITCH
                    }
                }
                val preSwitchHoistedSizeAccesses = packedSwitchIndexes.flatMap { switchIndex ->
                    instructions.mapIndexedNotNull { index, instruction ->
                        index.takeIf {
                            index < switchIndex &&
                                instruction.opcode == Opcode.IGET &&
                                instruction.getReference<FieldReference>()?.toString() ==
                                sizeField.toString()
                        }?.let { accessIndex -> accessIndex to instruction }
                    }
                }
                val sizeAccess = requireExactlyOne(
                    "NewX TwitterShare branch-local or pre-switch-hoisted size access",
                    branchLocalSizeAccesses + preSwitchHoistedSizeAccesses,
                )
                val sizeRegister =
                    (sizeAccess.second as? OneRegisterInstruction)?.registerA
                        ?: throw PatchException("NewX share icon size access has no register")
                val sizeModifierCalls = instructions.mapIndexedNotNull { index, instruction ->
                    if (index in branchStart until iconAccess.index &&
                        instruction.opcode == Opcode.INVOKE_STATIC
                    ) {
                        val method = instruction.getReference<MethodReference>()
                        if (method?.parameterTypes?.map { it.toString() } == listOf(MODIFIER, "F") &&
                            method?.returnType == MODIFIER
                        ) {
                            index to instruction
                        } else null
                    } else null
                }
                val sizeModifierCall = requireExactlyOne(
                    "NewX TwitterShare branch size modifier call",
                    sizeModifierCalls,
                )
                val iconRegister =
                    (iconAccess.instruction as? OneRegisterInstruction)?.registerA
                        ?: throw PatchException("NewX TwitterShare icon access has no register")
                val parameterRegisterStart = p0Register
                val parameterRegisters =
                    parameterRegisterStart until
                        (parameterRegisterStart + numberOfParameterRegisters)

                // Mutate from the higher index first so the earlier size-modifier index remains valid.
                insertHook(
                    index = iconAccess.index + 1,
                    excludedRegisters = parameterRegisters + iconRegister,
                    relocateBranchTargets = false,
                ) {
                    val incomingIconRegister = scratchRegister()
                    sget(incomingIconRegister, incomingIconField)
                    invokeStatic(
                        methodReference("$EXTENSION->selectIcon(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
                        iconRegister,
                        incomingIconRegister,
                    )
                    moveResult(iconRegister, OBJECT_DESCRIPTOR)
                    checkCast(iconRegister, shareIconField.type)
                }

                insertHook(
                    index = sizeModifierCall.first,
                    excludedRegisters = parameterRegisters + sizeRegister,
                    relocateBranchTargets = false,
                ) {
                    val displaySizeRegister = scratchRegister()
                    move(displaySizeRegister, sizeRegister, "F")
                    invokeStatic(
                        methodReference("$EXTENSION->displayIconSize(F)F"),
                        displaySizeRegister,
                    )
                    moveResult(displaySizeRegister, "F")
                    move(sizeRegister, displaySizeRegister, "F")
                }
            }

            val inlinePresenterType = barModels.inlineActionBarDescriptor
            val inlineEventHandler = requireExactlyOne(
                "NewX inline-action event handler",
                Fingerprint(
                    definingClass = inlinePresenterType,
                    returnType = "V",
                    filters =
                        listOf(
                            fieldAccess(
                                opcode = Opcode.IGET_OBJECT,
                                definingClass = entryModels.inlineActionEntryDescriptor,
                                type = entryModels.postActionTypeDescriptor,
                            ),
                            methodCall(
                                definingClass = "Ljava/lang/Enum;",
                                name = "ordinal",
                                parameters = emptyList(),
                                returnType = "I",
                            ),
                        ),
                ).scopedMatchAll(),
            )
            val longPressEventType = resolveInlineLongPressEventType(
                inlineEventHandler.originalMethod,
                entryModels.inlineActionEntryDescriptor,
            )
            inlineEventHandler.method.apply {
                requireStatic("NewX inline-action event handler")
                if (parameterTypes.firstOrNull().toString() != inlinePresenterType) {
                    throw PatchException("NewX inline event handler presenter parameter changed: $this")
                }
                val eventParameter =
                    parameterTypes.dropLast(1).sumOf { type ->
                        if (type.toString() == "J" || type.toString() == "D") 2 else 1
                    }
                val eventRegister = p0Register + eventParameter
                // The presenter receives a distinct event type for long press, but it carries the
                // same action entry. Classify the gesture from that native event identity so the
                // extension can download every media item without opening the picker.
                insertHook(0, relocateBranchTargets = false) {
                    val presenterRegister = scratchRegister()
                    val eventScratch = scratchRegister()
                    move(presenterRegister, p0Register, OBJECT_DESCRIPTOR)
                    move(eventScratch, eventRegister, OBJECT_DESCRIPTOR)
                    // `instance-of` needs both a four-bit destination and a four-bit reference.
                    val longPressRegister = scratchRegister()
                    instanceOf(longPressRegister, eventScratch, longPressEventType)
                    invokeStatic(
                        methodReference(
                            "$EXTENSION->handleEvent(Ljava/lang/Object;Ljava/lang/Object;Z)Z",
                        ),
                        presenterRegister,
                        eventScratch,
                        longPressRegister,
                    )
                    moveResult(presenterRegister, "Z")
                    ifEqz(presenterRegister, Target.Original)
                    returnVoid()
                }
            }
        }
    }

/**
 * The shared inline-action-bar layout lambda builds one kind model per entry and classifies
 * TwitterShare as Countless because this release's native kind switch has no TwitterShare arm.
 * That reserves a wider slot than the icon-only layout and leaves a trailing gap after the
 * injected download button. Identity-gate the injected action immediately before the kind model
 * constructor and rewrite only its kind register to IconOnly, leaving native actions untouched.
 */
context(context: BytecodePatchContext)
private fun patchInlineActionKindOverride(
    entryModels: ResolvedNewXInlineActionModels,
    kindModels: ResolvedNewXInlineActionKindOverride,
) {
    val (_, layoutMethod) = kindModels.layoutLambda.resolveMutableMethodOwner(
        "NewX inline-action kind layout lambda",
    )

    // The action-type field read is the only place the layout lambda touches an action entry; its
    // object register is that entry. This grounds the register instead of assuming a v-name.
    val actionTypeReads = layoutMethod.instructions.mapIndexedNotNull { index, instruction ->
        index.takeIf {
            instruction.opcode == Opcode.IGET_OBJECT &&
                instruction.getReference<FieldReference>()?.toString() ==
                entryModels.inlineActionTypeField.toString()
        }
    }
    val actionTypeReadIndex = requireExactlyOne(
        "NewX inline-action kind action-type read in $layoutMethod",
        actionTypeReads,
    )
    val entryRegister =
        layoutMethod.getInstruction<TwoRegisterInstruction>(actionTypeReadIndex).registerB

    // The same register must be the checked-cast entry the collection iterator produced, proving
    // it is the per-composition action object rather than an unrelated value.
    val entryCasts = layoutMethod.instructions.mapIndexedNotNull { index, instruction ->
        index.takeIf {
            instruction.opcode == Opcode.CHECK_CAST &&
                instruction.getReference<TypeReference>()?.type ==
                entryModels.inlineActionEntryDescriptor &&
                (instruction as? OneRegisterInstruction)?.registerA == entryRegister
        }
    }
    requireExactlyOne(
        "NewX inline-action entry cast for v$entryRegister in $layoutMethod",
        entryCasts,
    )

    val constructorIndex = requireExactlyOne(
        "NewX inline-action kind model construction in $layoutMethod",
        layoutMethod.instructions.mapIndexedNotNull { index, instruction ->
            index.takeIf {
                instruction.opcode == Opcode.INVOKE_DIRECT &&
                    instruction.getReference<MethodReference>()?.toString() ==
                    kindModels.kindModelConstructor.toString()
            }
        },
    )
    val constructorInstruction = layoutMethod.getInstruction<Instruction>(constructorIndex)
    // The kind is the last constructor parameter in this contract, and enum values are not wide.
    val kindRegister = constructorInstruction.registersUsed.last()
    if (kindRegister == entryRegister) {
        throw PatchException(
            "NewX inline-action entry and kind registers collide in $layoutMethod",
        )
    }

    // The entry must still be the first argument of the render call the constructor feeds, so the
    // register is live and unmodified at the rewrite point.
    requireExactlyOne(
        "NewX inline-action render call for v$entryRegister in $layoutMethod",
        layoutMethod.instructions.mapIndexedNotNull { index, instruction ->
            val reference = instruction.getReference<MethodReference>()
                ?: return@mapIndexedNotNull null
            index.takeIf {
                index > constructorIndex &&
                    instruction.opcode in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) &&
                    reference.parameterTypes.firstOrNull()?.toString() ==
                    entryModels.inlineActionEntryDescriptor &&
                    instruction.registersUsed.firstOrNull() == entryRegister
            }
        },
    )

    val parameterRegisters = layoutMethod.p0Register until
        (layoutMethod.p0Register + layoutMethod.numberOfParameterRegisters)
    layoutMethod.insertHook(
        index = constructorIndex,
        excludedRegisters = parameterRegisters + entryRegister + kindRegister,
        relocateBranchTargets = false,
    ) {
        val scratchRegister = scratchRegister()
        move(scratchRegister, entryRegister, OBJECT_DESCRIPTOR)
        invokeStatic(
            methodReference("$EXTENSION->isDownloadAction(Ljava/lang/Object;)Z"),
            scratchRegister,
        )
        moveResult(scratchRegister, "Z")
        ifEqz(scratchRegister, Target.Original)
        sget(kindRegister, kindModels.iconOnlyField)
    }
}

/**
 * The inline-action bar dispatches two gesture events through the presenter handler. The tap event
 * also carries resources for the presenter's limited-action messaging; the long-press event carries
 * only the action entry. Both implement the event interface, so resolve the long-press event as the
 * one that implements the event interface, exposes the action entry, and exposes no resources.
 * Resolve it semantically rather than by its obfuscated name.
 */
context(context: BytecodePatchContext)
private fun resolveInlineLongPressEventType(
    eventHandler: Method,
    actionEntryDescriptor: String,
): String {
    val eventInterfaceDescriptor = eventHandler.parameterTypes.lastOrNull()?.toString()
        ?: throw PatchException("NewX inline event handler has no event parameter: $eventHandler")
    val instructions = eventHandler.implementation?.instructions
        ?: throw PatchException("NewX inline event handler has no implementation: $eventHandler")

    return requireExactlyOne(
        "NewX inline-action long-press event",
        instructions
            .filter { instruction -> instruction.opcode == Opcode.INSTANCE_OF }
            .mapNotNull { instruction -> instruction.getReference<TypeReference>()?.type }
            .distinct()
            .filter { type ->
                val eventClass = context.classDefByOrNull(type) ?: return@filter false
                val carriesActionEntry = eventClass.exposesGetter(actionEntryDescriptor)
                val carriesResources = eventClass.exposesGetter(RESOURCES_DESCRIPTOR)
                eventClass.interfaces.any { descriptor ->
                    descriptor.toString() == eventInterfaceDescriptor
                } && carriesActionEntry && !carriesResources
            },
    )
}

private fun ClassDef.exposesGetter(returnType: String): Boolean =
    methods.any { method ->
        method.parameterTypes.isEmpty() && method.returnType == returnType
    }

context(context: BytecodePatchContext)
private fun patchPostModelBridges(
    postModels: ResolvedNewXPostModels,
    entryModels: ResolvedNewXInlineActionModels,
    barModels: ResolvedNewXInlineActionBarModels,
    mediaModels: ResolvedNewXPostMediaModels,
    downloadModels: ResolvedNewXInlineDownloadModels,
) {
    val contextualCanonicalPostField = postModels.contextualCanonicalPostField
    val contextualRepostedPostField = postModels.contextualRepostedPostField
    val repostedCanonicalPostField = postModels.repostedCanonicalPostField
    val canonicalPostMediaField = mediaModels.canonicalPostMediaField

    val presenterClass = context.mutableClassDefBy(barModels.inlineActionBarDescriptor)
    val presenterPostField = requireExactlyOne(
        "NewX inline presenter contextual-post field",
        presenterClass.fields.filter { field ->
            !AccessFlags.STATIC.isSet(field.accessFlags) &&
                field.type == postModels.contextualPostDescriptor
        },
    )
    presenterClass.requirePublicFields(listOf(presenterPostField))

    val extensionClass = context.mutableClassDefBy(EXTENSION)
    extensionClass.portHelperBody(
        PRESENTER_POST_HELPER,
        listOf(OBJECT_DESCRIPTOR),
        barModels.inlineActionBarDescriptor,
        presenterPostField,
    )
    extensionClass.portHelperBody(
        CANONICAL_POST_HELPER,
        listOf(OBJECT_DESCRIPTOR),
        postModels.contextualPostDescriptor,
        contextualCanonicalPostField,
    )
    extensionClass.portHelperBody(
        POST_MEDIA_HELPER,
        listOf(OBJECT_DESCRIPTOR),
        postModels.canonicalPostDescriptor,
        canonicalPostMediaField,
    )
    extensionClass.portHelperBody(
        REPOSTED_POST_HELPER,
        listOf(OBJECT_DESCRIPTOR),
        postModels.contextualPostDescriptor,
        contextualRepostedPostField,
    )
    extensionClass.portHelperBody(
        REPOSTED_CANONICAL_POST_HELPER,
        listOf(OBJECT_DESCRIPTOR),
        postModels.contextualRepostedPostField.type,
        repostedCanonicalPostField,
    )

    val entryClass = context.mutableClassDefBy(entryModels.inlineActionEntryDescriptor)
    val actionConstructor = requireExactlyOne(
        "NewX inline-action constructor",
        entryClass.methods.filter { method ->
            method.toString() == downloadModels.inlineActionEntryConstructor.toString()
        },
    )
    val actionTypeClass = context.mutableClassDefBy(entryModels.postActionTypeDescriptor)
    val carrierField = requireExactlyOne(
        "NewX TwitterShare action constant",
        actionTypeClass.fields.filter { field ->
            field.toString() == downloadModels.twitterShareActionField.toString()
        },
    )
    val createActionPlaceholder = extensionClass.requireHelper(CREATE_ACTION_HELPER, emptyList())
    val registerCount = createActionPlaceholder.implementation?.registerCount ?: 0
    val createActionHelper =
        if (registerCount >= 4) {
            createActionPlaceholder
        } else {
            createActionPlaceholder.cloneMutable(additionalRegisters = 4 - registerCount).also { expanded ->
                extensionClass.methods.remove(createActionPlaceholder)
                extensionClass.methods.add(expanded)
            }
        }
    createActionHelper.insertHook(0, relocateBranchTargets = false) {
        newInstance(0, entryModels.inlineActionEntryDescriptor)
        sget(1, carrierField)
        constInt(2, 0)
        constInt(3, 1)
        invokeDirect(actionConstructor, 0, 1, 2, 3)
        returnObject(0)
    }
}

/**
 * Fills an extension helper stub with `check-cast`, the field read and `return-object` on `p0`.
 * The stub keeps its own trailing return; the injected body runs before it.
 */
private fun MutableClass.portHelperBody(
    name: String,
    parameters: List<String>,
    type: String,
    field: FieldReference,
) {
    val helper = requireHelper(name, parameters)
    helper.insertHook(0, relocateBranchTargets = false) {
        val value = helper.p0Register
        checkCast(value, type)
        iget(value, value, field)
        returnObject(value)
    }
}

private fun MutableClass.requireHelper(
    name: String,
    parameters: List<String>,
): MutableMethod =
    requireExactlyOne(
        "NewX inline helper $name",
        methods.filter { method ->
            method.name == name &&
                method.parameterTypes.map { it.toString() } == parameters &&
                method.returnType == "Ljava/lang/Object;"
        },
    )

context(_: BytecodePatchContext)
private fun resolveIconField(resourceName: String): FieldReference {
    val resourceId = getResourceId(ResourceType.DRAWABLE, resourceName)
    val fields =
        Fingerprint(
            name = "<clinit>",
            returnType = "V",
            parameters = emptyList(),
            filters = listOf(literal(resourceId)),
        ).scopedMatchAll().map { match ->
            val literalIndex = requireExactlyOne(
                "NewX $resourceName drawable resource literal in ${match.originalMethod}",
                match.instructionMatches,
            ).index
            val store = requireExactlyOne(
                "NewX $resourceName drawable field store after ${match.originalMethod}",
                match.method.instructions
                    .drop(literalIndex + 1)
                    .take(4)
                    .filter { instruction -> instruction.opcode == Opcode.SPUT_OBJECT },
            )
            store.getReference<FieldReference>()
                ?: throw PatchException(
                    "NewX $resourceName drawable field store has no field reference: $store",
                )
        }.distinctBy(FieldReference::toString)

    return requireExactlyOne("NewX $resourceName icon field", fields)
}
