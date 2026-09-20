package app.crimera.patches.newx.misc.inlineactions

import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.misc.extension.newXInitHook
import app.crimera.patches.newx.models.ResolvedNewXInlineActionBarModels
import app.crimera.patches.newx.models.ResolvedNewXInlineActionModels
import app.crimera.patches.newx.models.ResolvedNewXInlineDownloadModels
import app.crimera.patches.newx.models.ResolvedNewXPostMediaModels
import app.crimera.patches.newx.models.ResolvedNewXPostModels
import app.crimera.patches.newx.models.firstParameterSlot
import app.crimera.patches.newx.models.isInlineActionEntryRenderer
import app.crimera.patches.newx.models.requirePublicFields
import app.crimera.patches.newx.models.resolvedNewXInlineActionBarModels
import app.crimera.patches.newx.models.resolvedNewXInlineActionModels
import app.crimera.patches.newx.models.resolvedNewXInlineDownloadModels
import app.crimera.patches.newx.models.resolvedNewXPostMediaModels
import app.crimera.patches.newx.models.resolvedNewXPostModels
import app.crimera.patches.newx.models.newXInlineDownloadModelResolutionPatch
import app.crimera.patches.newx.models.newXPostMediaModelResolutionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.choice
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.singleChoice
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.util.cloneMutable
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import app.morphe.util.numberOfParameterRegisters
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val MODIFIER = "Landroidx/compose/ui/Modifier;"
private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
private const val EXTENSION = "Lapp/morphe/extension/newx/misc/InlineDownloadButton;"
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

private fun MutableMethod.freeRegisters4Bit(
    index: Int,
    count: Int,
    excludedRegisters: Collection<Int> = emptyList(),
): List<Int> =
    try {
        getFreeRegisterProvider(index, count, *excludedRegisters.toIntArray()).let { provider ->
            List(count) { provider.getFreeRegister4Bit() }
        }
    } catch (exception: RuntimeException) {
        throw PatchException("No free 4-bit registers at $this index $index", exception)
    }

@Suppress("unused")
val newXInlineDownloadButtonPatch =
    bytecodePatch(
        name = "NewX: Inline download button",
        description = "Adds a Download button below NewX posts and saves images to Pictures/Twitter and videos to Movies/Twitter.",
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
                }
            }
        }

        execute {
            val entryModels = resolvedNewXInlineActionModels()
            val barModels = resolvedNewXInlineActionBarModels()
            val mediaModels = resolvedNewXPostMediaModels()
            val downloadModels = resolvedNewXInlineDownloadModels()
            patchPostModelBridges(
                resolvedNewXPostModels(),
                entryModels,
                barModels,
                mediaModels,
                downloadModels,
            )
            newXInitHook.fingerprint.method.addInstruction(
                0,
                "invoke-static/range {p0 .. p0}, $EXTENSION->initialize(Landroid/content/Context;)V",
            )

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
                // Icon-size float slot; Compose inserts auxiliary params between releases.
                val sizeSlot = firstParameterSlot("F")
                val (entryRegister, sizeRegister) = freeRegisters4Bit(index = 0, count = 2)
                addInstructions(
                    0,
                    """
                        move-object/from16 v$entryRegister, p0
                        move/from16 v$sizeRegister, p$sizeSlot
                        invoke-static {v$entryRegister, v$sizeRegister}, $EXTENSION->markIconSize(Ljava/lang/Object;F)F
                        move-result v$sizeRegister
                        move/from16 p$sizeSlot, v$sizeRegister
                    """.trimIndent(),
                )
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
                val (incomingIconRegister,) =
                    freeRegisters4Bit(
                        index = iconAccess.index + 1,
                        count = 1,
                        excludedRegisters = parameterRegisters + iconRegister,
                    )

                addInstructions(
                    iconAccess.index + 1,
                    """
                        sget-object v$incomingIconRegister, $incomingIconField
                        invoke-static {v$iconRegister, v$incomingIconRegister}, $EXTENSION->selectIcon(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
                        move-result-object v$iconRegister
                        check-cast v$iconRegister, ${shareIconField.type}
                    """.trimIndent(),
                )

                val (displaySizeRegister,) =
                    freeRegisters4Bit(
                        index = sizeModifierCall.first,
                        count = 1,
                        excludedRegisters = parameterRegisters + sizeRegister,
                    )

                addInstructions(
                    sizeModifierCall.first,
                    """
                        move/from16 v$displaySizeRegister, v$sizeRegister
                        invoke-static {v$displaySizeRegister}, $EXTENSION->displayIconSize(F)F
                        move-result v$displaySizeRegister
                        move/from16 v$sizeRegister, v$displaySizeRegister
                    """.trimIndent(),
                )
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
            inlineEventHandler.method.apply {
                requireStatic("NewX inline-action event handler")
                if (parameterTypes.firstOrNull().toString() != inlinePresenterType) {
                    throw PatchException("NewX inline event handler presenter parameter changed: $this")
                }
                val eventParameter =
                    parameterTypes.dropLast(1).sumOf { type ->
                        if (type.toString() == "J" || type.toString() == "D") 2 else 1
                    }
                val (presenterRegister, eventRegister) = freeRegisters4Bit(index = 0, count = 2)
                val nativeStart = instructions.first()
                addInstructionsWithLabels(
                    0,
                    """
                        move-object/from16 v$presenterRegister, p0
                        move-object/from16 v$eventRegister, p$eventParameter
                        invoke-static {v$presenterRegister, v$eventRegister}, $EXTENSION->handleEvent(Ljava/lang/Object;Ljava/lang/Object;)Z
                        move-result v$presenterRegister
                        if-eqz v$presenterRegister, :piko_newx_inline_download_continue
                        return-void
                    """.trimIndent(),
                    ExternalLabel("piko_newx_inline_download_continue", nativeStart),
                )
            }
        }
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
    extensionClass.requireHelper(PRESENTER_POST_HELPER, listOf("Ljava/lang/Object;")).addInstructions(
        0,
        """
            check-cast p0, ${barModels.inlineActionBarDescriptor}
            iget-object p0, p0, $presenterPostField
            return-object p0
        """.trimIndent(),
    )
    extensionClass.requireHelper(CANONICAL_POST_HELPER, listOf("Ljava/lang/Object;")).addInstructions(
        0,
        """
            check-cast p0, ${postModels.contextualPostDescriptor}
            iget-object p0, p0, $contextualCanonicalPostField
            return-object p0
        """.trimIndent(),
    )
    extensionClass.requireHelper(POST_MEDIA_HELPER, listOf("Ljava/lang/Object;")).addInstructions(
        0,
        """
            check-cast p0, ${postModels.canonicalPostDescriptor}
            iget-object p0, p0, $canonicalPostMediaField
            return-object p0
        """.trimIndent(),
    )
    extensionClass.requireHelper(REPOSTED_POST_HELPER, listOf("Ljava/lang/Object;")).addInstructions(
        0,
        """
            check-cast p0, ${postModels.contextualPostDescriptor}
            iget-object p0, p0, $contextualRepostedPostField
            return-object p0
        """.trimIndent(),
    )
    extensionClass.requireHelper(
        REPOSTED_CANONICAL_POST_HELPER,
        listOf("Ljava/lang/Object;"),
    ).addInstructions(
        0,
        """
            check-cast p0, ${postModels.contextualRepostedPostField.type}
            iget-object p0, p0, $repostedCanonicalPostField
            return-object p0
        """.trimIndent(),
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
    createActionHelper.addInstructions(
        0,
        """
            new-instance v0, ${entryModels.inlineActionEntryDescriptor}
            sget-object v1, $carrierField
            const/4 v2, 0x0
            const/4 v3, 0x1
            invoke-direct {v0, v1, v2, v3}, $actionConstructor
            return-object v0
        """.trimIndent(),
    )
}

private fun app.morphe.patcher.util.proxy.mutableTypes.MutableClass.requireHelper(
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
