package app.crimera.patches.newx.misc.inlineactions

import app.crimera.patches.newx.models.requirePublicFields
import app.crimera.patches.newx.models.resolveMutableMethodOwner
import app.crimera.patches.newx.models.resolvedNewXInlineActionBarModels
import app.crimera.patches.newx.models.resolvedNewXInlineActionModels
import app.crimera.patches.newx.models.newXInlineActionBarModelResolutionPatch
import app.crimera.patches.newx.models.newXInlineActionModelResolutionPatch
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.SettingReadRegisterConstraint
import app.crimera.patches.newx.settings.choice
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.injectRead
import app.crimera.patches.newx.settings.multiChoice
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.INLINE_ACTION_FILTER_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.cloneMutable
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

@Suppress("unused")
val customizeNewXInlineActionsPatch =
    bytecodePatch(
        name = "NewX: Customize inline actions",
        description = "Lets you hide selected actions from NewX post action bars.",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXInlineActionModelResolutionPatch, newXInlineActionBarModelResolutionPatch)

        val hiddenInlineActions =
            newXSettings {
                category(Categories.POST_ACTIONS_MEDIA) {
                    group(Groups.INLINE_ACTIONS) {
                        multiChoice(
                            id = "newx.content.hidden_inline_actions",
                            strings = settingStrings("piko_newx_inline_actions"),
                            order = 100,
                            defaultValue = emptySet(),
                            options =
                                listOf(
                                    choice("Reply", "piko_newx_inline_action_reply"),
                                    choice("Retweet", "piko_newx_inline_action_repost"),
                                    choice("Favorite", "piko_newx_inline_action_like"),
                                    choice("ViewCount", "piko_newx_inline_action_view_count"),
                                    choice("AddRemoveBookmarks", "piko_newx_inline_action_bookmark"),
                                    choice("TwitterShare", "piko_newx_inline_action_share"),
                                ),
                        )
                    }
                }
            }

        execute {
            val entryModels = resolvedNewXInlineActionModels()
            val barModels = resolvedNewXInlineActionBarModels()
            patchActionNameBridge(entryModels)

            val (inlineActionBarClass, originalMethod) =
                barModels.inlineActionStateBuilder.resolveMutableMethodOwner(
                    "inline action state builder",
                )
            val method = originalMethod.cloneMutable(additionalRegisters = 2)
            inlineActionBarClass.methods.remove(originalMethod)
            inlineActionBarClass.methods.add(method)
            if (AccessFlags.STATIC.isSet(method.accessFlags)) {
                throw PatchException("NewX inline action state builder is unexpectedly static: $method")
            }

            val inlineActionListType = barModels.canonicalPostInlineActionEntryField.type
            val conversionInstruction =
                method.instructions
                    .mapIndexedNotNull { index, instruction ->
                        val reference = instruction.getReference<MethodReference>()
                            ?: return@mapIndexedNotNull null
                        if (instruction.opcode !in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)) {
                            return@mapIndexedNotNull null
                        }
                        if (reference.returnType != inlineActionListType) return@mapIndexedNotNull null
                        index to reference
                    }.singleOrNull()
                    ?: throw PatchException("Expected one NewX inline action list conversion in $method")
            val conversionIndex = conversionInstruction.first
            val conversionReference = conversionInstruction.second
            val resultIndex = conversionIndex + 1
            val resultInstruction = method.getInstruction<OneRegisterInstruction>(resultIndex)
            if (resultInstruction.opcode != Opcode.MOVE_RESULT_OBJECT) {
                throw PatchException("NewX inline action list conversion result not found in $method")
            }
            val resultRegister = resultInstruction.registerA
            val freeRegisters = method.getFreeRegisterProvider(resultIndex + 1, 1, resultRegister)
            val listRegister = freeRegisters.getFreeRegister4Bit()
            val read =
                hiddenInlineActions.injectRead(
                    method = method,
                    index = resultIndex + 1,
                    excludedRegisters = listOf(resultRegister, listRegister),
                    registerConstraint = SettingReadRegisterConstraint.BYTE,
                )

            // Loop exits target the immutable conversion. Hook its result, then restore the
            // exact immutable representation before the consumer sees it.
            method.addInstructions(
                read.nextIndex,
                """
                    invoke-static/range {v${read.register} .. v${read.register}}, $INLINE_ACTION_FILTER_DESCRIPTOR->prepareHiddenActions(Ljava/util/Set;)V
                    invoke-static/range {p0 .. p0}, $INLINE_ACTION_FILTER_DESCRIPTOR->preparePresenter(Ljava/lang/Object;)V
                    move-object/from16 v$listRegister, v$resultRegister
                    invoke-static {v$listRegister}, $INLINE_ACTION_FILTER_DESCRIPTOR->filter(Ljava/util/List;)Ljava/util/List;
                    move-result-object v$resultRegister
                    invoke-static/range {v$resultRegister .. v$resultRegister}, $conversionReference
                    move-result-object v$resultRegister
                """.trimIndent(),
            )
        }
    }

context(context: BytecodePatchContext)
private fun patchActionNameBridge(models: app.crimera.patches.newx.models.ResolvedNewXInlineActionModels) {
    val inlineActionEntryClass = context.mutableClassDefBy(models.inlineActionEntryDescriptor)
    val actionTypeGetter = inlineActionEntryClass.methods.singleOrNull { method ->
        method.name == "getActionType" &&
            method.parameterTypes.isEmpty() &&
            method.returnType == models.postActionTypeDescriptor
    }
    val actionTypeRead =
        if (actionTypeGetter != null) {
            // BETA PATH: action type is private and exposed through getActionType().
            // Keep this branch as the source for future model updates.
            "invoke-virtual {p0}, $actionTypeGetter\nmove-result-object p0"
        } else {
            // ALPHA PATH: action type remains a public field.
            // TODO: Remove this fallback when alpha compatibility is deprecated.
            inlineActionEntryClass.requirePublicFields(listOf(models.inlineActionTypeField))
            "iget-object p0, p0, ${models.inlineActionTypeField}"
        }
    val extensionClass = context.mutableClassDefBy(INLINE_ACTION_FILTER_DESCRIPTOR)
    val helpers = extensionClass.methods.filter { method ->
        method.name == "getActionName" &&
            method.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/Object;") &&
            method.returnType == "Ljava/lang/String;"
    }
    if (helpers.size != 1) {
        throw PatchException(
            "Expected one NewX inline-action name bridge, found ${helpers.size}: " +
                helpers.joinToString(),
        )
    }
    helpers.single().addInstructions(
        0,
        """
            check-cast p0, ${models.inlineActionEntryDescriptor}
            $actionTypeRead
            invoke-virtual {p0}, Ljava/lang/Enum;->name()Ljava/lang/String;
            move-result-object p0
            return-object p0
        """.trimIndent(),
    )
}
