package app.crimera.patches.newx.misc.shareimage

import app.crimera.patches.newx.misc.postoptions.SHARE_IMAGE_ACTION
import app.crimera.patches.newx.misc.postoptions.newXPostOption
import app.crimera.patches.newx.models.fieldForToStringLabel
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.utils.requireAtMostOne
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.p0Register
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val MODIFIER = "Landroidx/compose/ui/Modifier;"
private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
private const val FUNCTION1 = "Lkotlin/jvm/functions/Function1;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val POINTER_INPUT_HANDLER = "Landroidx/compose/ui/input/pointer/PointerInputEventHandler;"
private const val SHARE_IMAGE_HANDLER = "Lapp/morphe/extension/newx/misc/NewXShareImageHandler;"
private const val POSITION_CALLBACK_DESCRIPTOR =
    "$SHARE_IMAGE_HANDLER->positionCallbackFromIdentifier($OBJECT_DESCRIPTOR)$FUNCTION1"

private object TimelinePostStateFingerprint : Fingerprint(
    definingClass = "Lcom/x/urt/items/post/",
    returnType = "Ljava/lang/String;",
    filters =
        listOf(
            app.morphe.patcher.string("AvailablePost(entryId="),
            app.morphe.patcher.string(", postId="),
            app.morphe.patcher.string(", timelinePostMediaState="),
        ),
)

@Suppress("unused")
val newXShareImagePatch =
    bytecodePatch(
        name = "NewX: Share post as image",
        description = "Adds a rendered-image share action to NewX post menus.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        newXToggle(
            id = "newx.content.share_post_as_image",
            category = Categories.POST_ACTIONS_MEDIA,
            strings = settingStrings("piko_newx_share_image"),
            order = 400,
            defaultValue = true,
        )

        newXPostOption(
            handlerDescriptor = SHARE_IMAGE_HANDLER,
            actionName = SHARE_IMAGE_ACTION,
            iconResourceName = "ic_vector_share",
            order = 250,
        )

        execute {
            val timelinePostStateMatch =
                requireExactlyOne(
                    "NewX timeline-post state",
                    TimelinePostStateFingerprint.scopedMatchAll(),
                )
            val timelinePostStateType = timelinePostStateMatch.originalClassDef.type
            val postIdentifierField =
                timelinePostStateMatch.fieldForToStringLabel(", postId=")
            requireExactlyOne(
                "NewX post-identifier string accessor",
                mutableClassDefBy(postIdentifierField.type).methods.filter { method ->
                    method.name == "toString" &&
                        method.parameterTypes.isEmpty() &&
                        method.returnType == "Ljava/lang/String;"
                },
            )
            val renderedPostMethod =
                requireExactlyOne(
                    "NewX individual post renderer",
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
                    ).scopedMatchAll(),
                )
            val pointerCallMatch = renderedPostMethod.instructionMatches[0]
            val pointerCallReference =
                pointerCallMatch.instruction.getReference<MethodReference>()
                    ?: throw PatchException("NewX post pointer modifier has no method reference")
            val pointerRegisters = pointerCallMatch.instruction.registersUsed
            val callbackRegister =
                pointerRegisters.getOrNull(2)
                    ?: throw PatchException("NewX post pointer modifier has no callback register")
            val modifierResult =
                renderedPostMethod.instructionMatches[1].instruction as? OneRegisterInstruction
                    ?: throw PatchException("NewX post pointer modifier has no result register")
            if (callbackRegister !in 0..15 || modifierResult.registerA !in 0..15) {
                throw PatchException("NewX post capture registers exceed 4-bit encoding")
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
                    } ?: throw PatchException("NewX post on-positioned modifier was not found")
            if (pointerCallReference.parameterTypes.map { it.toString() } !=
                listOf(MODIFIER, "Ljava/lang/Object;", POINTER_INPUT_HANDLER)
            ) {
                throw PatchException("NewX post pointer modifier signature changed")
            }
            // The callback register is dead once the pointer modifier consumed it, so it carries the
            // identifier, the new callback and the result, exactly as the smali hook did.
            renderedPostMethod.method.insertHook(
                index = renderedPostMethod.instructionMatches[1].index + 1,
                // The hook sits after the pointer modifier's `move-result-object`, so a label on the
                // following instruction stays there: the old plain insertion did not move it either.
                relocateBranchTargets = false,
            ) {
                move(callbackRegister, renderedPostMethod.method.p0Register, OBJECT_DESCRIPTOR)
                iget(callbackRegister, callbackRegister, postIdentifierField)
                invokeStatic(methodReference(POSITION_CALLBACK_DESCRIPTOR), callbackRegister)
                moveResult(callbackRegister, FUNCTION1)
                invokeStatic(onPositionedReference, modifierResult.registerA, callbackRegister)
                moveResult(modifierResult.registerA, MODIFIER)
            }
            // Detail rows reuse the same timeline-post state but render through wider composables
            // (thread view in 12.27, post view in 12.29, both with follow affordances). Timeline-only
            // tracking leaves their bounds stale, which captures the wrong post at the stale
            // position. Hooking each detail renderer keeps the same postId key fresh for all
            // screens; the runtime keeps the largest rect per composition burst (outer over inner)
            // and the most recent across screens.
            val detailThreadRenderer =
                requireAtMostOne(
                    "NewX post-detail thread renderer",
                    Fingerprint(
                        returnType = "V",
                        parameters =
                            listOf(
                                timelinePostStateType,
                                "L",
                                "L",
                                "L",
                                "L",
                                "L",
                                "L",
                                "L",
                                "Z",
                                "Z",
                                "L",
                                "L",
                                "L",
                                "L",
                                "Z",
                                "L",
                                "L",
                                "L",
                                "L",
                                "L",
                                MODIFIER,
                                COMPOSER,
                                "I",
                            ),
                        filters =
                            listOf(
                                methodCall(
                                    opcode = Opcode.INVOKE_STATIC,
                                    parameters = listOf(COMPOSER, MODIFIER),
                                    returnType = MODIFIER,
                                ),
                                opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
                            ),
                    ).scopedMatchAllOrNull().orEmpty().filter { match ->
                        match.originalMethod.toString() != renderedPostMethod.originalMethod.toString()
                    },
                )
            val detailPostRenderer =
                requireAtMostOne(
                    "NewX post-detail post renderer",
                    Fingerprint(
                        returnType = "V",
                        parameters =
                            listOf(
                                timelinePostStateType,
                                "L",
                                "L",
                                "L",
                                "L",
                                "L",
                                "L",
                                "L",
                                "Z",
                                "Z",
                                "Z",
                                "L",
                                "L",
                                "L",
                                "L",
                                "L",
                                "Z",
                                "L",
                                "L",
                                "L",
                                "L",
                                "L",
                                MODIFIER,
                                COMPOSER,
                                "I",
                            ),
                        filters =
                            listOf(
                                methodCall(
                                    opcode = Opcode.INVOKE_STATIC,
                                    parameters = listOf(COMPOSER, MODIFIER),
                                    returnType = MODIFIER,
                                ),
                                opcode(Opcode.MOVE_RESULT_OBJECT, MatchAfterImmediately()),
                            ),
                    ).scopedMatchAllOrNull().orEmpty().filter { match ->
                        match.originalMethod.toString() != renderedPostMethod.originalMethod.toString()
                    },
                )
            listOfNotNull(detailThreadRenderer, detailPostRenderer).forEach { detailRenderer ->
                val detailSites = detailRenderer.instructionMatches
                if (detailSites.isEmpty() || detailSites.size % 2 != 0) {
                    throw PatchException(
                        "NewX post-detail renderer has ${detailSites.size} composer modifier matches, " +
                            "expected call/result pairs"
                    )
                }
                detailSites.chunked(2).forEach { (callMatch, resultMatch) ->
                    val callReference =
                        callMatch.instruction.getReference<MethodReference>()
                            ?: throw PatchException("NewX post-detail composer modifier has no method reference")
                    if (callReference.parameterTypes.map { it.toString() } != listOf(COMPOSER, MODIFIER) ||
                        callReference.returnType != MODIFIER
                    ) {
                        throw PatchException("NewX post-detail composer modifier signature changed")
                    }
                    val modifierResult =
                        resultMatch.instruction as? OneRegisterInstruction
                            ?: throw PatchException("NewX post-detail composer modifier has no result register")
                    if (modifierResult.registerA !in 0..15) {
                        throw PatchException("NewX post-detail capture register exceeds 4-bit encoding")
                    }
                    detailRenderer.method.insertHook(
                        index = resultMatch.index + 1,
                        relocateBranchTargets = false,
                    ) {
                        val identifierRegister = scratchRegister()
                        move(identifierRegister, detailRenderer.method.p0Register, OBJECT_DESCRIPTOR)
                        iget(identifierRegister, identifierRegister, postIdentifierField)
                        invokeStatic(methodReference(POSITION_CALLBACK_DESCRIPTOR), identifierRegister)
                        moveResult(identifierRegister, FUNCTION1)
                        invokeStatic(onPositionedReference, modifierResult.registerA, identifierRegister)
                        moveResult(modifierResult.registerA, MODIFIER)
                    }
                }
            }
        }
    }
