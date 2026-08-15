package app.crimera.patches.xlite.misc.inlineactions

import app.crimera.patches.xlite.models.makeFieldsPublic
import app.crimera.patches.xlite.models.resolveMutableMethodOwner
import app.crimera.patches.xlite.models.resolvedXLiteInlineActionBarModels
import app.crimera.patches.xlite.models.resolvedXLiteInlineActionModels
import app.crimera.patches.xlite.models.xLiteInlineActionBarModelResolutionPatch
import app.crimera.patches.xlite.models.xLiteInlineActionModelResolutionPatch
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
val customizeXLiteInlineActionsPatch =
    bytecodePatch(
        name = "X-Lite: Customize inline actions",
        description = "Lets you hide selected actions from X-Lite post action bars.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)
        dependsOn(xLiteInlineActionModelResolutionPatch, xLiteInlineActionBarModelResolutionPatch)

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
            val entryModels = resolvedXLiteInlineActionModels()
            val barModels = resolvedXLiteInlineActionBarModels()
            prepareInlineActionFields(entryModels)
            patchActionNameBridge(entryModels)

            val (inlineActionBarClass, originalMethod) =
                barModels.inlineActionStateBuilder.resolveMutableMethodOwner(
                    "inline action state builder",
                )
            val method = originalMethod.cloneMutable(additionalRegisters = 2)
            inlineActionBarClass.methods.remove(originalMethod)
            inlineActionBarClass.methods.add(method)
            if (AccessFlags.STATIC.isSet(method.accessFlags)) {
                throw PatchException("X-Lite inline action state builder is unexpectedly static: $method")
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
                    ?: throw PatchException("Expected one X-Lite inline action list conversion in $method")
            val conversionIndex = conversionInstruction.first
            val conversionReference = conversionInstruction.second
            val resultIndex = conversionIndex + 1
            val resultInstruction = method.getInstruction<OneRegisterInstruction>(resultIndex)
            if (resultInstruction.opcode != Opcode.MOVE_RESULT_OBJECT) {
                throw PatchException("X-Lite inline action list conversion result not found in $method")
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
private fun prepareInlineActionFields(
    models: app.crimera.patches.xlite.models.ResolvedXLiteInlineActionModels,
) {
    context.mutableClassDefBy(models.inlineActionEntryDescriptor)
        .makeFieldsPublic(listOf(models.inlineActionTypeField))
}

context(context: BytecodePatchContext)
private fun patchActionNameBridge(models: app.crimera.patches.xlite.models.ResolvedXLiteInlineActionModels) {
    val extensionClass = context.mutableClassDefBy(INLINE_ACTION_FILTER_DESCRIPTOR)
    val helpers = extensionClass.methods.filter { method ->
        method.name == "getActionName" &&
            method.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/Object;") &&
            method.returnType == "Ljava/lang/String;"
    }
    if (helpers.size != 1) {
        throw PatchException(
            "Expected one X-Lite inline-action name bridge, found ${helpers.size}: " +
                helpers.joinToString(),
        )
    }
    helpers.single().addInstructions(
        0,
        """
            check-cast p0, ${models.inlineActionEntryDescriptor}
            iget-object p0, p0, ${models.inlineActionTypeField}
            invoke-virtual {p0}, Ljava/lang/Enum;->name()Ljava/lang/String;
            move-result-object p0
            return-object p0
        """.trimIndent(),
    )
}
