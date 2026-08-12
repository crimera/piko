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
import app.crimera.patches.xlite.timeline.fieldForToStringLabel
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.xlite.utils.Constants.INLINE_ACTION_FILTER_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.cloneMutable
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private object CanonicalPostFingerprint : Fingerprint(
    name = "toString",
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    strings = listOf("CanonicalPost(id=", ", inlineActionEntry="),
)

internal lateinit var xLiteInlineActionBarClassType: String
    private set
internal lateinit var xLiteInlineActionEntryType: String
    private set
internal lateinit var xLitePostActionType: String
    private set
internal lateinit var xLiteCanonicalPostType: String
    private set
internal lateinit var xLiteCanonicalPostMediaField: String
    private set
internal lateinit var xLiteContextualPostType: String
    private set
internal lateinit var xLiteContextualCanonicalPostField: String
    private set

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
            val inlineActionEntryMatch =
                Fingerprint(
                    name = "toString",
                    returnType = "Ljava/lang/String;",
                    parameters = emptyList(),
                    strings = listOf("InlineActionEntry(actionType=", ", isEnabled="),
                ).requireSingle("inline-action entry model")
            xLiteInlineActionEntryType = inlineActionEntryMatch.originalClassDef.type
            xLitePostActionType =
                inlineActionEntryMatch.fieldForFirstToStringValue().type

            val contextualPostMatch =
                Fingerprint(
                    name = "toString",
                    returnType = "Ljava/lang/String;",
                    parameters = emptyList(),
                    strings = listOf("ContextualPost(canonicalPost=", ", quotedPost="),
                ).requireSingle("contextual post model")

            val canonicalPostMatch = CanonicalPostFingerprint.requireSingle("canonical post model")
            val canonicalPostClass = canonicalPostMatch.originalClassDef
            xLiteCanonicalPostType = canonicalPostClass.type
            xLiteContextualPostType = contextualPostMatch.originalClassDef.type
            xLiteContextualCanonicalPostField =
                contextualPostMatch.originalClassDef.fields.singleOrNull { field ->
                    !AccessFlags.STATIC.isSet(field.accessFlags) && field.type == xLiteCanonicalPostType
                }?.toString() ?: throw PatchException("Expected one X-Lite contextual canonical-post field")
            xLiteCanonicalPostMediaField =
                canonicalPostMatch.fieldForToStringLabel(", media=").toString()
            val canonicalPostInterface =
                canonicalPostClass.interfaces.singleOrNull()
                    ?: throw PatchException(
                        "Expected one X-Lite canonical-post interface, found ${canonicalPostClass.interfaces}",
                    )
            val inlineActionListType =
                canonicalPostMatch.fieldForToStringLabel(", inlineActionEntry=").type
            val matches =
                Fingerprint(
                    parameters = listOf("Landroidx/compose/runtime/Composer;"),
                    custom = { _, classDef -> classDef.type.startsWith("Lcom/x/inlineactionbar/") },
                    filters =
                        listOf(
                            methodCall(
                                definingClass = canonicalPostInterface,
                                parameters = emptyList(),
                                returnType = "L",
                            ),
                            methodCall(smali = "Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z"),
                        ),
                ).matchAll()
            if (matches.size != 1) {
                throw PatchException(
                    "Expected one X-Lite inline action state builder, found ${matches.size}: " +
                        matches.joinToString { it.originalMethod.toString() },
                )
            }

            matches.single().let { match ->
                xLiteInlineActionBarClassType = match.originalClassDef.type
                val originalMethod = match.method
                val method = originalMethod.cloneMutable(additionalRegisters = 2)
                match.classDef.methods.remove(originalMethod)
                match.classDef.methods.add(method)
                if (AccessFlags.STATIC.isSet(method.accessFlags)) {
                    throw PatchException("X-Lite inline action state builder is unexpectedly static: $method")
                }
                val conversionInstruction =
                    method.instructions
                        .mapIndexedNotNull { index, instruction ->
                            val reference = instruction.getReference<MethodReference>() ?: return@mapIndexedNotNull null
                            if (instruction.opcode !in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)) {
                                return@mapIndexedNotNull null
                            }
                            if (reference.returnType != inlineActionListType) return@mapIndexedNotNull null
                            index to reference
                        }.singleOrNull()
                        ?: throw PatchException("Expected one X-Lite inline action list conversion")
                val conversionIndex = conversionInstruction.first
                val conversionReference = conversionInstruction.second
                val resultIndex = conversionIndex + 1
                val resultInstruction = method.getInstruction<OneRegisterInstruction>(resultIndex)
                if (resultInstruction.opcode != Opcode.MOVE_RESULT_OBJECT) {
                    throw PatchException("X-Lite inline action list conversion result not found")
                }
                val resultRegister = resultInstruction.registerA
                val freeRegisters =
                    method.getFreeRegisterProvider(resultIndex + 1, 1, resultRegister)
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
    }

private fun app.morphe.patcher.Match.fieldForFirstToStringValue(): com.android.tools.smali.dexlib2.iface.reference.FieldReference {
    val appendIndex =
        method.instructions.indexOfFirst { instruction ->
            instruction.getReference<MethodReference>()?.let { reference ->
                reference.definingClass == "Ljava/lang/StringBuilder;" &&
                    reference.name == "append" &&
                    reference.parameterTypes.size == 1 &&
                    reference.parameterTypes.single().toString().startsWith("L")
            } == true
        }
    if (appendIndex < 0) throw PatchException("X-Lite inline-action toString value append was not found")
    val append = method.getInstruction<FiveRegisterInstruction>(appendIndex)
    return method.instructions.take(appendIndex).asReversed().firstNotNullOfOrNull { instruction ->
        val read = instruction as? TwoRegisterInstruction ?: return@firstNotNullOfOrNull null
        if (read.registerA != append.registerD) return@firstNotNullOfOrNull null
        instruction.getReference<com.android.tools.smali.dexlib2.iface.reference.FieldReference>()?.takeIf { field ->
            field.definingClass == originalMethod.definingClass
        }
    } ?: throw PatchException("X-Lite inline-action type field was not found")
}

context(_: BytecodePatchContext)
private fun Fingerprint.requireSingle(label: String): app.morphe.patcher.Match {
    val matches = matchAll()
    if (matches.size == 1) return matches.single()
    throw PatchException(
        "Expected one X-Lite $label, found ${matches.size}: " +
            matches.joinToString { it.originalMethod.toString() },
    )
}
