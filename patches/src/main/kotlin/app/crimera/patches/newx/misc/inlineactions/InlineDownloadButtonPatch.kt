package app.crimera.patches.newx.misc.inlineactions

import app.crimera.patches.newx.misc.extension.newXInitHook
import app.crimera.patches.newx.models.ResolvedNewXInlineActionBarModels
import app.crimera.patches.newx.models.ResolvedNewXInlineActionModels
import app.crimera.patches.newx.models.ResolvedNewXInlineDownloadModels
import app.crimera.patches.newx.models.ResolvedNewXPostMediaModels
import app.crimera.patches.newx.models.ResolvedNewXPostModels
import app.crimera.patches.newx.models.readObject
import app.crimera.patches.newx.models.requirePublicFields
import app.crimera.patches.newx.models.resolvedNewXInlineActionBarModels
import app.crimera.patches.newx.models.resolvedNewXInlineActionModels
import app.crimera.patches.newx.models.resolvedNewXInlineDownloadModels
import app.crimera.patches.newx.models.resolvedNewXPostMediaModels
import app.crimera.patches.newx.models.resolvedNewXPostModels
import app.crimera.patches.newx.models.resolveFieldAccessor
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
import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
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
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val MODIFIER = "Landroidx/compose/ui/Modifier;"
private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
private const val EXTENSION = "Lapp/morphe/extension/newx/misc/InlineDownloadButton;"
private const val PRESENTER_POST_HELPER = "getPresenterPost"
private const val CANONICAL_POST_HELPER = "getCanonicalPost"
private const val POST_MEDIA_HELPER = "getPostMedia"
private const val REPOSTED_POST_HELPER = "getRepostedPost"
private const val REPOSTED_CANONICAL_POST_HELPER = "getRepostedCanonicalPost"
private const val CREATE_ACTION_HELPER = "createDownloadAction"

private fun requireSingle(
    label: String,
    matches: Collection<Match>,
): Match {
    if (matches.size == 1) return matches.single()
    throw PatchException(
        "Expected one $label, found ${matches.size}: " +
            matches.joinToString { it.originalMethod.toString() },
    )
}

private fun MutableMethod.requireStatic(label: String) {
    if (AccessFlags.STATIC.isSet(accessFlags)) return
    throw PatchException("$label is no longer static: $this")
}

private fun MutableMethod.freeRegisters4Bit(
    index: Int,
    count: Int,
): List<Int> =
    try {
        getFreeRegisterProvider(index, count).let { provider ->
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
        )

        newXSettings {
            category(Categories.POST_ACTIONS_MEDIA) {
                group(Groups.INLINE_ACTIONS) {
                    toggle(
                        id = "newx.content.inline_download_button",
                        strings = settingStrings("piko_newx_inline_download_button"),
                        order = 200,
                        defaultValue = true,
                    )
                    toggle(
                        id = "newx.content.media_picker_copy_link",
                        strings = settingStrings("piko_newx_media_picker_copy_link"),
                        order = 300,
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

            val inlineRendererMatches =
                listOf(
                    // ALPHA PATH: field-based inline-action model and legacy renderer shape.
                    // TODO: Remove this fingerprint when alpha compatibility is deprecated.
                    Fingerprint(
                        definingClass = "Lcom/x/inlineactionbar/",
                        parameters =
                            listOf(
                                entryModels.inlineActionEntryDescriptor,
                                "L",
                                "J",
                                "F",
                                "L",
                                "J",
                                "L",
                                "L",
                                MODIFIER,
                                COMPOSER,
                                "I",
                            ),
                        returnType = "V",
                        filters =
                            listOf(
                                fieldAccess(
                                    opcode = Opcode.IGET_OBJECT,
                                    definingClass = entryModels.inlineActionEntryDescriptor,
                                    type = entryModels.postActionTypeDescriptor,
                                ),
                                fieldAccess(
                                    opcode = Opcode.IGET_BOOLEAN,
                                    definingClass = entryModels.inlineActionEntryDescriptor,
                                    type = "Z",
                                ),
                            ),
                    ).scopedMatchAllOrNull().orEmpty(),
                    // BETA PATH: getter-based inline-action model and beta renderer shape.
                    Fingerprint(
                        definingClass = "Lcom/x/inlineactionbar/",
                        parameters =
                            listOf(
                                entryModels.inlineActionEntryDescriptor,
                                "L",
                                "J",
                                "F",
                                "L",
                                "J",
                                "L",
                                MODIFIER,
                                COMPOSER,
                                "I",
                            ),
                        returnType = "V",
                        filters =
                            listOf(
                                methodCall(
                                    smali =
                                        "${entryModels.inlineActionEntryDescriptor}->getActionType()" +
                                            entryModels.postActionTypeDescriptor,
                                ),
                                methodCall(
                                    smali = "${entryModels.inlineActionEntryDescriptor}->isEnabled()Z",
                                ),
                            ),
                    ).scopedMatchAllOrNull().orEmpty(),
                    // BETA PATH: getter-based inline-action model and the 11-parameter renderer
                    // introduced in 12.19.1.
                    Fingerprint(
                        definingClass = "Lcom/x/inlineactionbar/",
                        parameters =
                            listOf(
                                entryModels.inlineActionEntryDescriptor,
                                "L",
                                "J",
                                "F",
                                "L",
                                "J",
                                "L",
                                "L",
                                MODIFIER,
                                COMPOSER,
                                "I",
                            ),
                        returnType = "V",
                        filters =
                            listOf(
                                methodCall(
                                    smali =
                                        "${entryModels.inlineActionEntryDescriptor}->getActionType()" +
                                            entryModels.postActionTypeDescriptor,
                                ),
                                methodCall(
                                    smali = "${entryModels.inlineActionEntryDescriptor}->isEnabled()Z",
                                ),
                            ),
                    ).scopedMatchAllOrNull().orEmpty(),
                ).flatten()
                    .distinctBy { it.originalMethod.toString() }
            val inlineRenderer =
                requireSingle("NewX inline-action entry renderer", inlineRendererMatches)
            inlineRenderer.method.apply {
                requireStatic("NewX inline-action entry renderer")
                val (entryRegister, sizeRegister) = freeRegisters4Bit(index = 0, count = 2)
                addInstructions(
                    0,
                    """
                        move-object/from16 v$entryRegister, p0
                        move/from16 v$sizeRegister, p4
                        invoke-static {v$entryRegister, v$sizeRegister}, $EXTENSION->markIconSize(Ljava/lang/Object;F)F
                        move-result v$sizeRegister
                        move/from16 p4, v$sizeRegister
                    """.trimIndent(),
                )

                // Unconditional marker cleanup: replace the shared exit location (every
                // predecessor targets it) with finishRender and re-append the return. A
                // plain insertion before it would be skippable when a branch targets the
                // original return instruction.
                val exits = instructions.filter { it.opcode == Opcode.RETURN_VOID }
                if (exits.size != 1) {
                    throw PatchException(
                        "Expected one NewX inline-action entry renderer exit, found " +
                            "${exits.size}: $this",
                    )
                }
                val renderExit = instructions.indexOf(exits.single())
                replaceInstruction(
                    renderExit,
                    "invoke-static {}, $EXTENSION->finishRender()V",
                )
                addInstruction(renderExit + 1, "return-void")

                // The normal cleanup is outside this range so an exception from cleanup does
                // not re-enter the handler. The catch-all rethrows after clearing the marker.
                val implementation =
                    inlineRenderer.method.implementation
                        ?: throw PatchException("NewX inline-action entry renderer has no implementation")
                if (implementation.tryBlocks.isNotEmpty()) {
                    throw PatchException(
                        "NewX inline-action entry renderer already has exception handlers: $this",
                    )
                }
                val cleanupTryStart = implementation.newLabelForIndex(0)
                val cleanupTryEnd = implementation.newLabelForIndex(renderExit)
                val cleanupHandlerIndex = implementation.instructions.size
                addInstructions(
                    cleanupHandlerIndex,
                    """
                        move-exception v0
                        invoke-static {}, $EXTENSION->finishRender()V
                        throw v0
                    """.trimIndent(),
                )
                val cleanupHandler = implementation.newLabelForIndex(cleanupHandlerIndex)
                implementation.addCatch(cleanupTryStart, cleanupTryEnd, cleanupHandler)
            }

            val shareIconField = resolveIconField("ic_vector_share")
            val incomingIconField = resolveIconField("ic_vector_incoming_stroke")
            if (shareIconField.type != incomingIconField.type) {
                throw PatchException("NewX inline icon types differ")
            }
            val iconRendererMatches =
                // ALPHA PATH: icon lambda under com/x/compose/.
                // BETA PATH: icon lambda may move under com/x/ui/common/.
                // TODO: Remove the alpha owner scope when alpha compatibility is deprecated.
                listOf("Lcom/x/compose/", "Lcom/x/ui/common/").flatMap { ownerScope ->
                    Fingerprint(
                        definingClass = ownerScope,
                        filters =
                            listOf(
                                fieldAccess(opcode = Opcode.SGET_OBJECT, reference = shareIconField),
                                fieldAccess(opcode = Opcode.IGET, definingClass = "this", type = "F"),
                            ),
                    ).scopedMatchAllOrNull().orEmpty()
                }.distinctBy { it.originalMethod.toString() }
            val iconRenderer =
                requireSingle("NewX TwitterShare icon lambda", iconRendererMatches)
            iconRenderer.method.apply {
                val iconAccess = iconRenderer.instructionMatches[0]
                val iconRegister =
                    (iconAccess.instruction as? OneRegisterInstruction)?.registerA
                        ?: throw PatchException("NewX share icon access has no register")
                val sizeAccess = iconRenderer.instructionMatches[1]
                val sizeField =
                    sizeAccess.instruction.getReference<FieldReference>()
                        ?: throw PatchException("NewX share icon size field was not found")
                addInstructions(
                    iconAccess.index + 1,
                    """
                        sget-object p1, $incomingIconField
                        iget p2, p0, $sizeField
                        invoke-static {v$iconRegister, p2, p1}, $EXTENSION->selectIcon(Ljava/lang/Object;FLjava/lang/Object;)Ljava/lang/Object;
                        move-result-object v$iconRegister
                        check-cast v$iconRegister, ${shareIconField.type}
                    """.trimIndent(),
                )
            }

            val inlinePresenterType = barModels.inlineActionBarDescriptor
            val inlineEventHandlerMatches =
                listOf(
                    // ALPHA PATH: event handler reads the action-type field.
                    // TODO: Remove this fingerprint when alpha compatibility is deprecated.
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
                    ).scopedMatchAllOrNull().orEmpty(),
                    // BETA PATH: event handler calls getActionType().
                    Fingerprint(
                        definingClass = inlinePresenterType,
                        returnType = "V",
                        filters =
                            listOf(
                                methodCall(
                                    smali =
                                        "${entryModels.inlineActionEntryDescriptor}->getActionType()" +
                                            entryModels.postActionTypeDescriptor,
                                ),
                                methodCall(
                                    definingClass = "Ljava/lang/Enum;",
                                    name = "ordinal",
                                    parameters = emptyList(),
                                    returnType = "I",
                                ),
                            ),
                    ).scopedMatchAllOrNull().orEmpty(),
                ).flatten()
                    .distinctBy { it.originalMethod.toString() }
            val inlineEventHandler =
                requireSingle("NewX inline-action event handler", inlineEventHandlerMatches)
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
    val contextualPostClass = context.mutableClassDefBy(postModels.contextualPostDescriptor)
    // ALPHA PATH uses public fields; BETA PATH uses private-model getters.
    // See resolveFieldAccessor for the temporary compatibility split.
    val contextualCanonicalPostAccessor =
        contextualPostClass.resolveFieldAccessor(
            postModels.contextualCanonicalPostField,
            "contextual canonical post",
        )
    val contextualRepostedPostAccessor =
        contextualPostClass.resolveFieldAccessor(
            postModels.contextualRepostedPostField,
            "contextual reposted post",
        )
    val repostedPostClass = context.mutableClassDefBy(postModels.contextualRepostedPostField.type)
    val repostedCanonicalPostAccessor =
        repostedPostClass.resolveFieldAccessor(
            postModels.repostedCanonicalPostField,
            "reposted canonical post",
        )
    val canonicalPostClass = context.mutableClassDefBy(postModels.canonicalPostDescriptor)
    val canonicalPostMediaAccessor =
        canonicalPostClass.resolveFieldAccessor(
            mediaModels.canonicalPostMediaField,
            "canonical post media",
        )

    val presenterClass = context.mutableClassDefBy(barModels.inlineActionBarDescriptor)
    val presenterPostFields = presenterClass.fields.filter { field ->
        !AccessFlags.STATIC.isSet(field.accessFlags) &&
            field.type == postModels.contextualPostDescriptor
    }
    if (presenterPostFields.size != 1) {
        throw PatchException(
            "Expected one NewX inline presenter contextual-post field, found " +
                "${presenterPostFields.size}: ${presenterPostFields.joinToString()}",
        )
    }
    val presenterPostField = presenterPostFields.single()
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
            ${contextualCanonicalPostAccessor.readObject("p0")}
            return-object p0
        """.trimIndent(),
    )
    extensionClass.requireHelper(POST_MEDIA_HELPER, listOf("Ljava/lang/Object;")).addInstructions(
        0,
        """
            check-cast p0, ${postModels.canonicalPostDescriptor}
            ${canonicalPostMediaAccessor.readObject("p0")}
            return-object p0
        """.trimIndent(),
    )
    extensionClass.requireHelper(REPOSTED_POST_HELPER, listOf("Ljava/lang/Object;")).addInstructions(
        0,
        """
            check-cast p0, ${postModels.contextualPostDescriptor}
            ${contextualRepostedPostAccessor.readObject("p0")}
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
            ${repostedCanonicalPostAccessor.readObject("p0")}
            return-object p0
        """.trimIndent(),
    )

    val entryClass = context.mutableClassDefBy(entryModels.inlineActionEntryDescriptor)
    val actionConstructor = entryClass.methods.singleOrNull { method ->
        method.toString() == downloadModels.inlineActionEntryConstructor.toString()
    } ?: throw PatchException(
        "Resolved NewX inline-action constructor is no longer present: " +
            downloadModels.inlineActionEntryConstructor,
    )
    val actionTypeClass = context.mutableClassDefBy(entryModels.postActionTypeDescriptor)
    val carrierField = actionTypeClass.fields.singleOrNull { field ->
        field.toString() == downloadModels.twitterShareActionField.toString()
    } ?: throw PatchException(
        "Resolved NewX TwitterShare action constant is no longer present: " +
            downloadModels.twitterShareActionField,
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
    methods.singleOrNull { method ->
        method.name == name &&
            method.parameterTypes.map { it.toString() } == parameters &&
            method.returnType == "Ljava/lang/Object;"
    } ?: throw PatchException("NewX inline helper $name was not found")

context(_: BytecodePatchContext)
private fun resolveIconField(resourceName: String): FieldReference {
    val resourceId = getResourceId(ResourceType.DRAWABLE, resourceName)
    val fields =
        Fingerprint(
            definingClass = "Lcom/x/icons/",
            name = "<clinit>",
            returnType = "V",
            parameters = emptyList(),
            filters = listOf(literal(resourceId)),
        ).scopedMatchAll().mapNotNull { match ->
            val literalIndex = match.instructionMatches.single().index
            match.method.instructions
                .drop(literalIndex + 1)
                .take(4)
                .firstOrNull { instruction -> instruction.opcode == Opcode.SPUT_OBJECT }
                ?.getReference<FieldReference>()
        }.distinctBy(FieldReference::toString)

    if (fields.size == 1) return fields.single()
    throw PatchException(
        "Expected one NewX $resourceName icon field, found ${fields.size}: " +
            fields.joinToString(),
    )
}
