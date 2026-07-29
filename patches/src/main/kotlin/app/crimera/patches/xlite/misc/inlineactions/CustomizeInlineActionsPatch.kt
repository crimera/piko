package app.crimera.patches.xlite.misc.inlineactions

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.Groups
import app.crimera.patches.xlite.settings.SettingReadRegisterConstraint
import app.crimera.patches.xlite.settings.choice
import app.crimera.patches.xlite.settings.group
import app.crimera.patches.xlite.settings.injectRead
import app.crimera.patches.xlite.settings.multiChoice
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.xLiteSettings
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
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val CONTEXTUAL_POST = "Lcom/x/models/ContextualPost;"

internal object XLiteInlineActionBarClassFingerprint : Fingerprint(
    name = "<init>",
    returnType = "V",
    filters =
        listOf(
            fieldAccess(
                opcode = Opcode.IPUT_OBJECT,
                definingClass = "this",
                type = CONTEXTUAL_POST,
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
                    "$CONTEXTUAL_POST->getCanonicalPost()Lcom/x/models/CanonicalPost;",
            ),
            methodCall(
                smali =
                    "$CONTEXTUAL_POST->getInlineActionEntry()Lkotlinx/collections/immutable/c;",
            ),
            methodCall(smali = "Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z"),
        ),
)

@Suppress("unused")
val customizeXLiteInlineActionsPatch =
    bytecodePatch(
        name = "X-Lite: Customize inline actions",
        description = "Lets you hide selected actions from X-Lite post action bars.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        val hiddenInlineActions =
            xLiteSettings {
                category(Categories.POST_ACTIONS_MEDIA) {
                    group(Groups.INLINE_ACTIONS) {
                        multiChoice(
                            id = "xlite.content.hidden_inline_actions",
                            strings = settingStrings("piko_xlite_inline_actions"),
                            order = 100,
                            defaultValue = emptySet(),
                            options =
                                listOf(
                                    choice("Reply", "piko_xlite_inline_action_reply"),
                                    choice("Retweet", "piko_xlite_inline_action_repost"),
                                    choice("Favorite", "piko_xlite_inline_action_like"),
                                    choice("ViewCount", "piko_xlite_inline_action_view_count"),
                                    choice("AddRemoveBookmarks", "piko_xlite_inline_action_bookmark"),
                                    choice("TwitterShare", "piko_xlite_inline_action_share"),
                                ),
                        )
                    }
                }
            }

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
                if (AccessFlags.STATIC.isSet(method.accessFlags)) {
                    throw PatchException("X-Lite inline action state builder is unexpectedly static: $method")
                }
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
                val freeRegisters =
                    method.getFreeRegisterProvider(resultIndex + 1, 2, resultRegister)
                val listRegister = freeRegisters.getFreeRegister4Bit()
                val presenterRegister = freeRegisters.getFreeRegister4Bit()
                val read =
                    hiddenInlineActions.injectRead(
                        method = method,
                        index = resultIndex + 1,
                        excludedRegisters = listOf(resultRegister, listRegister, presenterRegister),
                        registerConstraint = SettingReadRegisterConstraint.FOUR_BIT,
                    )

                // Loop exits target the immutable conversion. Hook its result, then restore the
                // exact immutable representation before the consumer sees it.
                method.addInstructions(
                    read.nextIndex,
                    """
                        move-object/from16 v$listRegister, v$resultRegister
                        move-object/from16 v$presenterRegister, p0
                        invoke-static {v$listRegister, v${read.register}, v$presenterRegister}, $INLINE_ACTION_FILTER_DESCRIPTOR->filter(Ljava/util/List;Ljava/util/Set;Ljava/lang/Object;)Ljava/util/List;
                        move-result-object v$resultRegister
                        invoke-static/range {v$resultRegister .. v$resultRegister}, $conversionReference
                        move-result-object v$resultRegister
                    """.trimIndent(),
                )
            }
        }
    }
