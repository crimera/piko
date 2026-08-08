package app.crimera.patches.xlite.misc.shareimage

import app.crimera.patches.xlite.misc.postoptions.SHARE_IMAGE_ACTION
import app.crimera.patches.xlite.misc.postoptions.xLitePostOption
import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteToggle
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val POST_IDENTIFIER = "Lcom/x/models/PostIdentifier;"
private const val MODIFIER = "Landroidx/compose/ui/Modifier;"
private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
private const val FUNCTION1 = "Lkotlin/jvm/functions/Function1;"
private const val POINTER_INPUT_HANDLER = "Landroidx/compose/ui/input/pointer/PointerInputEventHandler;"
private const val SHARE_IMAGE_HANDLER = "Lapp/morphe/extension/xlite/misc/XLiteShareImageHandler;"

private object TimelinePostStateFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    filters =
        listOf(
            app.morphe.patcher.string("AvailablePost(entryId="),
            app.morphe.patcher.string(", postId="),
            app.morphe.patcher.string(", timelinePostMediaState="),
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
        compatibleWith(COMPATIBILITY_X_LITE)

        xLiteToggle(
            id = "xlite.content.share_post_as_image",
            category = Categories.POST_ACTIONS_MEDIA,
            strings = settingStrings("piko_xlite_share_image"),
            order = 400,
            defaultValue = true,
        )

        xLitePostOption(
            handlerDescriptor = SHARE_IMAGE_HANDLER,
            actionName = SHARE_IMAGE_ACTION,
            iconResourceName = "ic_vector_share",
            order = 250,
        )

        execute {
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
        }
    }
