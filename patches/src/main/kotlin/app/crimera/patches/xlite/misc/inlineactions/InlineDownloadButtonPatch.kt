package app.crimera.patches.xlite.misc.inlineactions

import app.crimera.patches.xlite.misc.extension.xLiteInitHook
import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.Groups
import app.crimera.patches.xlite.settings.group
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.toggle
import app.crimera.patches.xlite.settings.xLiteSettings
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val INLINE_ACTION_ENTRY = "Lcom/x/models/InlineActionEntry;"
private const val POST_ACTION_TYPE = "Lcom/x/models/PostActionType;"
private const val MODIFIER = "Landroidx/compose/ui/Modifier;"
private const val COMPOSER = "Landroidx/compose/runtime/Composer;"
private const val EXTENSION = "Lapp/morphe/extension/xlite/misc/InlineDownloadButton;"

private object InlineActionEntryRendererFingerprint : Fingerprint(
    parameters =
        listOf(
            INLINE_ACTION_ENTRY,
            "L",
            "J",
            "F",
            "L",
            "J",
            "L",
            "Z",
            MODIFIER,
            COMPOSER,
            "I",
        ),
    returnType = "V",
    filters =
        listOf(
            methodCall(
                smali = "$INLINE_ACTION_ENTRY->getActionType()$POST_ACTION_TYPE",
            ),
            methodCall(
                smali = "$INLINE_ACTION_ENTRY->isEnabled()Z",
            ),
        ),
)

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
val xLiteInlineDownloadButtonPatch =
    bytecodePatch(
        name = "X-Lite: Inline download button",
        description = "Adds a Download button below X-Lite posts and saves media to Pictures/Twitter.",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)
        dependsOn(customizeXLiteInlineActionsPatch)

        xLiteSettings {
            category(Categories.POST_ACTIONS_MEDIA) {
                group(Groups.INLINE_ACTIONS) {
                    toggle(
                        id = "xlite.content.inline_download_button",
                        strings = settingStrings("piko_xlite_inline_download_button"),
                        order = 200,
                        defaultValue = true,
                    )
                    toggle(
                        id = "xlite.content.media_picker_copy_link",
                        strings = settingStrings("piko_xlite_media_picker_copy_link"),
                        order = 300,
                        defaultValue = true,
                    )
                }
            }
        }

        execute {
            xLiteInitHook.fingerprint.method.addInstruction(
                0,
                "invoke-static/range {p0 .. p0}, $EXTENSION->initialize(Landroid/content/Context;)V",
            )

            val inlineRenderer =
                requireSingle(
                    "X-Lite inline-action entry renderer",
                    InlineActionEntryRendererFingerprint.matchAll(),
                )
            inlineRenderer.method.apply {
                requireStatic("X-Lite inline-action entry renderer")
                val (entryRegister, sizeRegister) = freeRegisters4Bit(index = 0, count = 2)
                addInstructions(
                    0,
                    """
                        move-object/from16 v$entryRegister, p0
                        move/from16 v$sizeRegister, p4
                        invoke-static {v$entryRegister, v$sizeRegister}, $EXTENSION->markIconSize(${INLINE_ACTION_ENTRY}F)F
                        move-result v$sizeRegister
                        move/from16 p4, v$sizeRegister
                    """.trimIndent(),
                )
            }

            val shareIconField = resolveIconField("ic_vector_share")
            val incomingIconField = resolveIconField("ic_vector_incoming_stroke")
            if (shareIconField.type != incomingIconField.type) {
                throw PatchException(
                    "X-Lite inline icon types differ: ${shareIconField.type} and ${incomingIconField.type}",
                )
            }

            val iconRenderer =
                requireSingle(
                    "X-Lite TwitterShare icon renderer",
                    Fingerprint(
                        parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
                        returnType = "Ljava/lang/Object;",
                        filters =
                            listOf(
                                fieldAccess(
                                    opcode = Opcode.SGET_OBJECT,
                                    reference = shareIconField,
                                ),
                                methodCall(
                                    parameters = listOf(COMPOSER, "I"),
                                    returnType = "Ljava/lang/String;",
                                ),
                                opcode(
                                    Opcode.MOVE_RESULT_OBJECT,
                                    MatchAfterImmediately(),
                                ),
                                fieldAccess(
                                    opcode = Opcode.IGET,
                                    definingClass = "this",
                                    type = "F",
                                ),
                            ),
                    ).matchAll(),
                )
            iconRenderer.method.apply {
                val iconAccess = iconRenderer.instructionMatches[0]
                val iconRegister =
                    (iconAccess.instruction as? OneRegisterInstruction)?.registerA
                        ?: throw PatchException("X-Lite share icon access has no register")
                val descriptionResult = iconRenderer.instructionMatches[2]
                val descriptionRegister =
                    (descriptionResult.instruction as? OneRegisterInstruction)?.registerA
                        ?: throw PatchException("X-Lite share description has no result register")
                val sizeAccess = iconRenderer.instructionMatches[3]
                val sizeRegister =
                    (sizeAccess.instruction as? OneRegisterInstruction)?.registerA
                        ?: throw PatchException("X-Lite share icon size has no register")
                val sizeField =
                    sizeAccess.instruction.getReference<FieldReference>()
                        ?: throw PatchException("X-Lite share icon size field was not found")

                addInstructions(
                    sizeAccess.index + 1,
                    """
                        invoke-static {v$sizeRegister}, $EXTENSION->normalizeIconSize(F)F
                        move-result v$sizeRegister
                    """.trimIndent(),
                )
                addInstructions(
                    descriptionResult.index + 1,
                    """
                        iget p1, p0, $sizeField
                        invoke-static {v$descriptionRegister, p1}, $EXTENSION->contentDescription(Ljava/lang/String;F)Ljava/lang/String;
                        move-result-object v$descriptionRegister
                    """.trimIndent(),
                )
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

            val inlinePresenterType =
                XLiteInlineActionBarClassFingerprint.originalClassDef.type
            val inlineEventHandler =
                requireSingle(
                    "X-Lite inline-action event handler",
                    Fingerprint(
                        definingClass = inlinePresenterType,
                        returnType = "V",
                        filters =
                            listOf(
                                methodCall(
                                    smali = "$INLINE_ACTION_ENTRY->getActionType()$POST_ACTION_TYPE",
                                ),
                                methodCall(
                                    definingClass = "Ljava/lang/Enum;",
                                    name = "ordinal",
                                    parameters = emptyList(),
                                    returnType = "I",
                                ),
                            ),
                    ).matchAll(),
                )
            inlineEventHandler.method.apply {
                requireStatic("X-Lite inline-action event handler")
                if (parameterTypes.firstOrNull().toString() != inlinePresenterType) {
                    throw PatchException("X-Lite inline event handler presenter parameter changed: $this")
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
                        if-eqz v$presenterRegister, :piko_xlite_inline_download_continue
                        return-void
                    """.trimIndent(),
                    ExternalLabel("piko_xlite_inline_download_continue", nativeStart),
                )
            }

        }
    }

context(_: BytecodePatchContext)
private fun resolveIconField(resourceName: String): FieldReference {
    val resourceId = getResourceId(ResourceType.DRAWABLE, resourceName)
    val fields =
        Fingerprint(
            name = "<clinit>",
            returnType = "V",
            parameters = emptyList(),
            filters = listOf(literal(resourceId)),
        ).matchAll().mapNotNull { match ->
            val literalIndex = match.instructionMatches.single().index
            match.method.instructions
                .drop(literalIndex + 1)
                .take(4)
                .firstOrNull { instruction -> instruction.opcode == Opcode.SPUT_OBJECT }
                ?.getReference<FieldReference>()
        }.distinctBy(FieldReference::toString)

    if (fields.size == 1) return fields.single()
    throw PatchException(
        "Expected one X-Lite $resourceName icon field, found ${fields.size}: " +
            fields.joinToString(),
    )
}
