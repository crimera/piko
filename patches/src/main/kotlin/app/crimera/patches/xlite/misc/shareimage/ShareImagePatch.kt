package app.crimera.patches.xlite.misc.shareimage

import app.crimera.patches.xlite.misc.extension.xLiteInitHook
import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteToggle
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val POST_ACTION_TYPE = "Lcom/x/models/PostActionType;"
private const val POST_OPTIONS_STATE_PREFIX = "PostOptionsState(showOptionsDialog="
private const val POST_OPTIONS_LIST_PREFIX = ", options="
private const val URT_POST = "Lcom/x/models/timelines/items/UrtTimelinePost;"
private const val CONTEXT = "Landroid/content/Context;"
private const val COROUTINE_SCOPE = "Lkotlinx/coroutines/k0;"
private const val POST_IDENTIFIER = "Lcom/x/models/PostIdentifier;"
private const val MODIFIER = "Landroidx/compose/ui/Modifier;"
private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
private const val FUNCTION1 = "Lkotlin/jvm/functions/Function1;"
private const val POINTER_INPUT_HANDLER = "Landroidx/compose/ui/input/pointer/PointerInputEventHandler;"
private const val SHARE_IMAGE_HANDLER = "Lapp/morphe/extension/xlite/misc/XLiteShareImageHandler;"

private val compatibility =
    Compatibility(
        name = "X-Lite",
        packageName = "com.twitter.android",
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x000000,
        targets =
            listOf(
                AppTarget(version = "12.10.1-release.0"),
                AppTarget(version = "12.11.0-release.0"),
            ),
    )

private object PostOptionsStateFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    filters =
        listOf(
            app.morphe.patcher.string(POST_OPTIONS_STATE_PREFIX),
            app.morphe.patcher.string(POST_OPTIONS_LIST_PREFIX),
        ),
)

private object TimelinePostStateFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    filters =
        listOf(
            app.morphe.patcher.string("AvailablePost(entryId="),
            app.morphe.patcher.string(", postId="),
            app.morphe.patcher.string(", timelinePostMediaState="),
        ),
)

private object PostOptionsPresenterFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.IPUT_OBJECT,
                definingClass = "this",
                type = URT_POST,
            ),
            fieldAccess(
                opcode = Opcode.IPUT_OBJECT,
                definingClass = "this",
                type = CONTEXT,
            ),
            fieldAccess(
                opcode = Opcode.IPUT_OBJECT,
                definingClass = "this",
                type = "Lkotlinx/coroutines/channels/e;",
            ),
        ),
)

private fun requireMatches(label: String, matches: Collection<Match>, expected: Int = 1): List<Match> {
    if (matches.size == expected) return matches.toList()
    throw PatchException(
        "Expected $expected $label matches, found ${matches.size}: " +
            matches.joinToString { it.originalMethod.toString() },
    )
}

@Suppress("unused")
val xLiteShareImagePatch =
    bytecodePatch(
        name = "X-Lite: Share post as image",
        description = "Adds a rendered-image share action to X-Lite post menus.",
    ) {
        compatibleWith(compatibility)

        xLiteToggle(
            id = "xlite.content.share_post_as_image",
            category = Categories.POST_ACTIONS_MEDIA,
            strings = settingStrings("piko_xlite_share_image"),
            order = 250,
            defaultValue = true,
        )

        execute {
            xLiteInitHook.fingerprint.method.addInstruction(
                0,
                "invoke-static/range {p0 .. p0}, $SHARE_IMAGE_HANDLER->initialize(Landroid/content/Context;)V",
            )

            val timelinePostStateMatch =
                requireMatches(
                    "X-Lite timeline-post state",
                    TimelinePostStateFingerprint.matchAll(),
                ).single()
            val timelinePostStateType = timelinePostStateMatch.originalClassDef.type
            val postIdentifierField =
                timelinePostStateMatch.originalClassDef.fields.singleOrNull { it.type == POST_IDENTIFIER }
                    ?: throw PatchException("X-Lite timeline-post state has no unique PostIdentifier field")
            val renderedPostMethod =
                requireMatches(
                    "X-Lite individual post renderer",
                    Fingerprint(
                        returnType = "V",
                        parameters =
                            listOf(
                                timelinePostStateType,
                                "L",
                                "L",
                                "L",
                                COMPOSER,
                                "I",
                            ),
                        filters =
                            listOf(
                                methodCall(
                                    opcode = Opcode.INVOKE_STATIC,
                                    parameters = listOf(MODIFIER, "Ljava/lang/Object;", POINTER_INPUT_HANDLER),
                                    returnType = MODIFIER,
                                ),
                                opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
                                methodCall(
                                    opcode = Opcode.INVOKE_STATIC,
                                    parameters = listOf(COMPOSER, MODIFIER),
                                    returnType = MODIFIER,
                                ),
                            ),
                    ).matchAll(),
                ).single()
            val pointerCallMatch = renderedPostMethod.instructionMatches[0]
            val pointerCallReference =
                pointerCallMatch.instruction.getReference<MethodReference>()
                    ?: throw PatchException("X-Lite post pointer modifier has no method reference")
            val pointerRegisters = pointerCallMatch.instruction.registersUsed
            val callbackRegister =
                pointerRegisters.getOrNull(2)
                    ?: throw PatchException("X-Lite post pointer modifier has no callback register")
            val modifierResult =
                renderedPostMethod.instructionMatches[1].instruction as? OneRegisterInstruction
                    ?: throw PatchException("X-Lite post pointer modifier has no result register")
            if (callbackRegister !in 0..15 || modifierResult.registerA !in 0..15) {
                throw PatchException("X-Lite post capture registers exceed 4-bit encoding")
            }
            val onPositionedReference =
                renderedPostMethod.method.instructions
                    .take(pointerCallMatch.index)
                    .asReversed()
                    .firstNotNullOfOrNull { instruction ->
                        instruction.getReference<MethodReference>()?.takeIf { reference ->
                            instruction.opcode == Opcode.INVOKE_STATIC &&
                                reference.parameterTypes.map { it.toString() } == listOf(MODIFIER, FUNCTION1) &&
                                reference.returnType == MODIFIER
                        }
                    } ?: throw PatchException("X-Lite post on-positioned modifier was not found")
            if (pointerCallReference.parameterTypes.map { it.toString() } !=
                listOf(MODIFIER, "Ljava/lang/Object;", POINTER_INPUT_HANDLER)
            ) {
                throw PatchException("X-Lite post pointer modifier signature changed")
            }
            renderedPostMethod.method.addInstructions(
                renderedPostMethod.instructionMatches[1].index + 1,
                """
                    move-object/from16 v$callbackRegister, p0
                    iget-object v$callbackRegister, v$callbackRegister, $postIdentifierField
                    invoke-static {v$callbackRegister}, $SHARE_IMAGE_HANDLER->positionCallback(Ljava/lang/Object;)$FUNCTION1
                    move-result-object v$callbackRegister
                    invoke-static {v${modifierResult.registerA}, v$callbackRegister}, $onPositionedReference
                    move-result-object v${modifierResult.registerA}
                """.trimIndent(),
            )

            val stateMatch = requireMatches("X-Lite post-options state", PostOptionsStateFingerprint.matchAll()).single()
            val stateType = stateMatch.originalClassDef.type
            val stateConstructor =
                requireMatches(
                    "X-Lite post-options state constructor",
                    Fingerprint(
                        classFingerprint = PostOptionsStateFingerprint,
                        name = "<init>",
                        returnType = "V",
                        parameters =
                            listOf(
                                "Z",
                                "Lcom/x/models/UserResult;",
                                "Ljava/util/List;",
                                "Ljava/util/Map;",
                                "Lkotlinx/coroutines/flow/g;",
                                "Lkotlin/jvm/functions/Function1;",
                                "L",
                                "L",
                            ),
                    ).matchAll(),
                ).single()

            stateConstructor.method.addInstructions(
                0,
                """
                    invoke-static {p3}, $SHARE_IMAGE_HANDLER->addOption(Ljava/util/List;)Ljava/util/List;
                    move-result-object p3
                """.trimIndent(),
            )

            val labelRenderer =
                requireMatches(
                    "X-Lite post-options label renderer",
                    Fingerprint(
                        filters =
                            listOf(
                                fieldAccess(
                                    opcode = Opcode.IGET_OBJECT,
                                    definingClass = stateType,
                                    type = "Ljava/util/Map;",
                                ),
                                methodCall(
                                    opcode = Opcode.INVOKE_INTERFACE,
                                    definingClass = "Ljava/util/Map;",
                                    name = "get",
                                    parameters = listOf("Ljava/lang/Object;"),
                                    returnType = "Ljava/lang/Object;",
                                    location = MatchAfterImmediately(),
                                ),
                                opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
                            ),
                    ).matchAll(),
                ).single()
            val mapGet = labelRenderer.instructionMatches[1].instruction
            val labelResult = labelRenderer.instructionMatches[2].instruction as? OneRegisterInstruction
                ?: throw PatchException("X-Lite post-options Map.get does not return an object register")
            val actionRegister = mapGet.registersUsed.getOrNull(1)
                ?: throw PatchException("X-Lite post-options Map.get has no action register")
            if (actionRegister !in 0..15 || labelResult.registerA !in 0..15) {
                throw PatchException("X-Lite post-options label registers exceed 4-bit encoding")
            }
            labelRenderer.method.addInstructions(
                labelRenderer.instructionMatches[2].index + 1,
                """
                    invoke-static {v$actionRegister, v${labelResult.registerA}}, $SHARE_IMAGE_HANDLER->labelFor(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/String;
                    move-result-object v${labelResult.registerA}
                """.trimIndent(),
            )

            val iconAssignmentInstruction =
                labelRenderer.method.instructions.withIndex().lastOrNull { (index, instruction) ->
                    instruction.opcode == Opcode.MOVE_OBJECT_FROM16 &&
                        index < labelRenderer.instructionMatches[1].index &&
                        labelRenderer.method.instructions.getOrNull(index + 1)?.opcode in
                        setOf(Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32)
                }?.value ?: throw PatchException("X-Lite post-options icon assignment was not found")
            val iconAssignment = iconAssignmentInstruction as? TwoRegisterInstruction
                ?: throw PatchException("X-Lite post-options icon assignment has no registers")
            val iconAssignmentIndex =
                labelRenderer.method.instructions.indexOf(iconAssignmentInstruction)
            val iconSourceRegister = iconAssignment.registerB
            val iconResultRegister = iconAssignment.registerA
            val iconType =
                labelRenderer.method.instructions
                    .take(iconAssignmentIndex)
                    .asReversed()
                    .firstNotNullOfOrNull { instruction ->
                        if (instruction.opcode != Opcode.SGET_OBJECT) return@firstNotNullOfOrNull null
                        val register = (instruction as? OneRegisterInstruction)?.registerA
                        instruction.getReference<FieldReference>()?.takeIf {
                            register == iconSourceRegister
                        }?.type
                    } ?: throw PatchException("X-Lite post-options icon type was not found")
            val shareIconField = resolveShareIconField(iconType)
            val nativeIconContinuation =
                labelRenderer.method.instructions[iconAssignmentIndex + 1]
            labelRenderer.method.addInstructionsWithLabels(
                iconAssignmentIndex + 1,
                """
                    invoke-static {v$actionRegister}, $SHARE_IMAGE_HANDLER->usesShareIcon(Ljava/lang/Object;)Z
                    move-result v$iconSourceRegister
                    if-eqz v$iconSourceRegister, :piko_xlite_share_image_native_icon
                    sget-object v$iconResultRegister, $shareIconField
                """.trimIndent(),
                ExternalLabel("piko_xlite_share_image_native_icon", nativeIconContinuation),
            )

            val presenterMatch =
                requireMatches(
                    "X-Lite post-options presenter",
                    PostOptionsPresenterFingerprint.matchAll(),
                ).single()
            val eventHandlerClass =
                requireMatches(
                    "X-Lite post-options event handler class",
                    Fingerprint(
                        name = "<init>",
                        returnType = "V",
                        parameters =
                            listOf(
                                presenterMatch.originalClassDef.type,
                                COROUTINE_SCOPE,
                                "Landroidx/compose/runtime/v2;",
                                "Landroidx/compose/runtime/v2;",
                                "Landroidx/compose/runtime/v2;",
                                "Landroidx/compose/runtime/v2;",
                            ),
                    ).matchAll(),
                ).single()
            val eventHandler =
                requireMatches(
                    "X-Lite post-options event handler",
                    Fingerprint(
                        classFingerprint = Fingerprint(
                            definingClass = eventHandlerClass.originalClassDef.type,
                        ),
                        parameters = listOf("Ljava/lang/Object;"),
                        returnType = "Ljava/lang/Object;",
                    ).matchAll(),
                ).single()
            val presenterField =
                eventHandler.originalClassDef.fields.singleOrNull {
                    it.type == presenterMatch.originalClassDef.type
                } ?: throw PatchException("X-Lite post-options event handler has no presenter field")

            val actionFieldInstruction =
                eventHandler.method.instructions.withIndex().singleOrNull { (index, instruction) ->
                    val field = instruction.getReference<FieldReference>()
                    if (instruction.opcode != Opcode.IGET_OBJECT || field?.type != POST_ACTION_TYPE) return@singleOrNull false

                    val nextReference =
                        eventHandler.method.instructions
                            .getOrNull(index + 1)
                            ?.getReference<MethodReference>()
                    if (nextReference?.definingClass != "Ljava/lang/Enum;" || nextReference.name != "ordinal") {
                        return@singleOrNull false
                    }

                    eventHandler.method.instructions
                        .subList(maxOf(0, index - 12), index)
                        .any { previous ->
                            previous.getReference<MethodReference>()?.let { reference ->
                                reference.name == "setValue" &&
                                    reference.parameterTypes.map { it.toString() } == listOf("Ljava/lang/Object;") &&
                                    reference.returnType == "V"
                            } == true
                        }
                } ?: throw PatchException("X-Lite confirmed post-option action extraction was not found uniquely")
            val clickActionRegister =
                (actionFieldInstruction.value as? OneRegisterInstruction)?.registerA
                    ?: throw PatchException("X-Lite confirmed post-option action has no register")
            val scratchRegister =
                eventHandler.method.instructions
                    .getOrNull(actionFieldInstruction.index + 2)
                    ?.let { it as? OneRegisterInstruction }
                    ?.registerA
                    ?: throw PatchException("X-Lite post-option ordinal result has no register")
            if (clickActionRegister !in 0..15 || scratchRegister !in 0..15) {
                throw PatchException("X-Lite post-option hook registers exceed 4-bit encoding")
            }

            val nativeActionDispatch =
                eventHandler.method.instructions[actionFieldInstruction.index + 1]
            eventHandler.method.addInstructionsWithLabels(
                actionFieldInstruction.index + 1,
                """
                    move-object/from16 v$scratchRegister, p0
                    iget-object v$scratchRegister, v$scratchRegister, $presenterField
                    invoke-static {v$scratchRegister, v$clickActionRegister}, $SHARE_IMAGE_HANDLER->handleOptionAction(Ljava/lang/Object;Ljava/lang/Object;)Z
                    move-result v$scratchRegister
                    if-eqz v$scratchRegister, :piko_xlite_share_image_continue
                    sget-object v$scratchRegister, Lkotlin/Unit;->a:Lkotlin/Unit;
                    return-object v$scratchRegister
                """.trimIndent(),
                ExternalLabel("piko_xlite_share_image_continue", nativeActionDispatch),
            )
        }
    }

context(_: BytecodePatchContext)
private fun resolveShareIconField(iconType: String): FieldReference {
    val drawableId = getResourceId(ResourceType.DRAWABLE, "ic_vector_share")
    val matches =
        Fingerprint(
            name = "<clinit>",
            returnType = "V",
            parameters = emptyList(),
            filters = listOf(literal(drawableId)),
        ).matchAll()
    val fields =
        matches.mapNotNull { match ->
            val literalIndex = match.instructionMatches.single().index
            match.method.instructions
                .drop(literalIndex + 1)
                .take(4)
                .firstOrNull { instruction ->
                    instruction.opcode == Opcode.SPUT_OBJECT &&
                        instruction.getReference<FieldReference>()?.type == iconType
                }?.getReference<FieldReference>()
        }.distinctBy(FieldReference::toString)

    if (fields.size != 1) {
        throw PatchException(
            "Expected one X-Lite share icon field, found ${fields.size}: " +
                fields.joinToString(),
        )
    }
    return fields.single()
}
