package app.crimera.patches.xlite.misc.inlineactions

import app.crimera.patches.xlite.settings.ChoiceOption
import app.crimera.patches.xlite.settings.MultiChoiceSettingDefinition
import app.crimera.patches.xlite.settings.XLiteSettingsCategory
import app.crimera.patches.xlite.settings.injectStringSetRead
import app.crimera.patches.xlite.settings.xLiteSettingsContributionPatch
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.xlite.utils.Constants.INLINE_ACTION_FILTER_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private object XLiteInlineActionBarClassFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.IPUT_OBJECT,
                definingClass = "this",
                type = "Lcom/x/models/ContextualPost;",
            ),
            fieldAccess(
                opcode = Opcode.IPUT_OBJECT,
                definingClass = "this",
                type = "Lcom/x/subscriptions/SubscriptionsFeatures;",
            ),
        ),
)

private object CustomizeXLiteInlineActionsFingerprint : Fingerprint(
    classFingerprint = XLiteInlineActionBarClassFingerprint,
    parameters = listOf("Landroidx/compose/runtime/Composer;"),
    filters =
        listOf(
            methodCall(
                smali =
                    "Lcom/x/models/ContextualPost;->getCanonicalPost()Lcom/x/models/CanonicalPost;",
            ),
            methodCall(
                smali =
                    "Lcom/x/models/ContextualPost;->getInlineActionEntry()Lkotlinx/collections/immutable/c;",
            ),
            methodCall(smali = "Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z"),
        ),
)

private val hiddenInlineActions =
    MultiChoiceSettingDefinition(
        id = "xlite.content.hidden_inline_actions",
        titleResourceName = "piko_xlite_inline_actions_title",
        summaryResourceName = "piko_xlite_inline_actions_summary",
        order = 200,
        defaultValue = emptySet(),
        options =
            listOf(
                ChoiceOption("Reply", "piko_xlite_inline_action_reply"),
                ChoiceOption("Retweet", "piko_xlite_inline_action_repost"),
                ChoiceOption("Favorite", "piko_xlite_inline_action_like"),
                ChoiceOption("ViewCount", "piko_xlite_inline_action_view_count"),
                ChoiceOption("AddRemoveBookmarks", "piko_xlite_inline_action_bookmark"),
                ChoiceOption("TwitterShare", "piko_xlite_inline_action_share"),
            ),
    )

private val hiddenInlineActionsSettingsPatch =
    xLiteSettingsContributionPatch {
        category(XLiteSettingsCategory.CONTENT) {
            add(hiddenInlineActions)
        }
    }

@Suppress("unused")
val customizeXLiteInlineActionsPatch =
    bytecodePatch(
        name = "X-Lite: Customize inline actions",
        description = "Lets you hide selected actions from X-Lite post action bars.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)
        dependsOn(hiddenInlineActionsSettingsPatch)

        execute {
            val matches = CustomizeXLiteInlineActionsFingerprint.matchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite inline action state builder, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            matches.single().let { match ->
                val method = match.method
                val addIndex = match.instructionMatches.last().index
                val builderListRegister = method.getInstruction(addIndex).registersUsed.first()
                val conversionInstruction =
                    method.instructions
                        .drop(addIndex + 1)
                        .firstOrNull { instruction ->
                            instruction.opcode == Opcode.INVOKE_STATIC &&
                                instruction.registersUsed.firstOrNull() == builderListRegister
                        } ?: throw PatchException("X-Lite inline action list conversion not found")
                val conversionIndex = conversionInstruction.location.index
                val conversionReference =
                    conversionInstruction.getReference<MethodReference>()
                        ?: throw PatchException("X-Lite inline action list conversion has no method reference")
                val resultIndex = conversionIndex + 1
                val resultInstruction = method.getInstruction<OneRegisterInstruction>(resultIndex)
                if (resultInstruction.opcode != Opcode.MOVE_RESULT_OBJECT) {
                    throw PatchException("X-Lite inline action list conversion result not found")
                }
                val resultRegister = resultInstruction.registerA
                val registerProvider =
                    method.getFreeRegisterProvider(resultIndex + 1, 2, resultRegister)
                val listRegister = registerProvider.getFreeRegister4Bit()
                val settingsRegister = registerProvider.getFreeRegister4Bit()
                val readInstructionCount =
                    hiddenInlineActions.injectStringSetRead(
                        method,
                        resultIndex + 1,
                        settingsRegister,
                    )

                // Loop exits target the immutable conversion. Hook its result, then restore the
                // exact immutable representation before the consumer sees it.
                method.addInstructions(
                    resultIndex + 1 + readInstructionCount,
                    """
                        move-object/from16 v$listRegister, v$resultRegister
                        invoke-static {v$listRegister, v$settingsRegister}, $INLINE_ACTION_FILTER_DESCRIPTOR->filter(Ljava/util/List;Ljava/util/Set;)Ljava/util/List;
                        move-result-object v$resultRegister
                        invoke-static/range {v$resultRegister .. v$resultRegister}, $conversionReference
                        move-result-object v$resultRegister
                    """.trimIndent(),
                )
            }
        }
    }
