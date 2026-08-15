package app.crimera.patches.xlite.misc.inlineactions

import app.crimera.patches.xlite.misc.extension.xLiteInitHook
import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.Groups
import app.crimera.patches.xlite.settings.choice
import app.crimera.patches.xlite.settings.group
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.singleChoice
import app.crimera.patches.xlite.settings.toggle
import app.crimera.patches.xlite.settings.xLiteSettings
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
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
private const val EXTENSION = "Lapp/morphe/extension/xlite/misc/InlineDownloadButton;"
private const val POST_CLASS_NAME_HELPER = "getPresenterPostClassName"
private const val CANONICAL_POST_HELPER = "getCanonicalPost"
private const val POST_MEDIA_HELPER = "getPostMedia"
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
                    singleChoice(
                        id = "xlite.content.inline_download_conflict",
                        strings = settingStrings("piko_xlite_inline_download_conflict"),
                        order = 400,
                        defaultValue = "skip",
                        options =
                            listOf(
                                choice("overwrite", "piko_xlite_inline_download_conflict_overwrite"),
                                choice("rename", "piko_xlite_inline_download_conflict_rename"),
                                choice("skip", "piko_xlite_inline_download_conflict_skip"),
                            ),
                    )
                }
            }
        }

        execute {
            patchPostModelBridges()
            xLiteInitHook.fingerprint.method.addInstruction(
                0,
                "invoke-static/range {p0 .. p0}, $EXTENSION->initialize(Landroid/content/Context;)V",
            )

            val inlineRenderer =
                requireSingle(
                    "X-Lite inline-action entry renderer",
                    Fingerprint(
                        definingClass = "Lcom/x/inlineactionbar/",
                        parameters =
                            listOf(
                                xLiteInlineActionEntryType,
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
                                    definingClass = xLiteInlineActionEntryType,
                                    type = xLitePostActionType,
                                ),
                                fieldAccess(
                                    opcode = Opcode.IGET_BOOLEAN,
                                    definingClass = xLiteInlineActionEntryType,
                                    type = "Z",
                                ),
                            ),
                    ).matchAll(),
                )
            inlineRenderer.method.apply {
                requireStatic("X-Lite inline-action entry renderer")
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
            }

            val shareIconField = resolveIconField("ic_vector_share")
            val incomingIconField = resolveIconField("ic_vector_incoming_stroke")
            if (shareIconField.type != incomingIconField.type) {
                throw PatchException("X-Lite inline icon types differ")
            }
            val iconRenderer =
                requireSingle(
                    "X-Lite TwitterShare icon lambda",
                    Fingerprint(
                        definingClass = "Lcom/x/compose/",
                        filters =
                            listOf(
                                fieldAccess(opcode = Opcode.SGET_OBJECT, reference = shareIconField),
                                fieldAccess(opcode = Opcode.IGET, definingClass = "this", type = "F"),
                            ),
                    ).matchAll(),
                )
            iconRenderer.method.apply {
                val iconAccess = iconRenderer.instructionMatches[0]
                val iconRegister =
                    (iconAccess.instruction as? OneRegisterInstruction)?.registerA
                        ?: throw PatchException("X-Lite share icon access has no register")
                val sizeAccess = iconRenderer.instructionMatches[1]
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

            val inlinePresenterType = xLiteInlineActionBarClassType
            val inlineEventHandler =
                requireSingle(
                    "X-Lite inline-action event handler",
                    Fingerprint(
                        definingClass = inlinePresenterType,
                        returnType = "V",
                        filters =
                            listOf(
                                fieldAccess(
                                    opcode = Opcode.IGET_OBJECT,
                                    definingClass = xLiteInlineActionEntryType,
                                    type = xLitePostActionType,
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

context(context: BytecodePatchContext)
private fun patchPostModelBridges() {
    val presenterClass = context.mutableClassDefBy(xLiteInlineActionBarClassType)
    val constructor = presenterClass.methods.singleOrNull { method -> method.name == "<init>" }
        ?: throw PatchException("Expected one X-Lite inline presenter constructor")
    val postType = constructor.parameterTypes.firstOrNull { type -> type.toString().startsWith("Lcom/x/models/") }
        ?: throw PatchException("X-Lite inline presenter post parameter was not found")
    val extensionClass = context.mutableClassDefBy(EXTENSION)
    val classNameHelper = extensionClass.requireHelper(POST_CLASS_NAME_HELPER, emptyList())
    classNameHelper.addInstructions(
        0,
        """
            const-string v0, "${postType.toString().removePrefix("L").removeSuffix(";").replace('/', '.')}"
            return-object v0
        """.trimIndent(),
    )
    extensionClass.requireHelper(CANONICAL_POST_HELPER, listOf("Ljava/lang/Object;")).addInstructions(
        0,
        """
            check-cast p0, $xLiteContextualPostType
            iget-object p0, p0, $xLiteContextualCanonicalPostField
            return-object p0
        """.trimIndent(),
    )
    extensionClass.requireHelper(POST_MEDIA_HELPER, listOf("Ljava/lang/Object;")).addInstructions(
        0,
        """
            check-cast p0, $xLiteCanonicalPostType
            iget-object p0, p0, $xLiteCanonicalPostMediaField
            return-object p0
        """.trimIndent(),
    )
    val entryClass = context.mutableClassDefBy(xLiteInlineActionEntryType)
    val actionConstructor = entryClass.methods.singleOrNull { method ->
        method.name == "<init>" && method.parameterTypes.map { it.toString() } ==
            listOf(xLitePostActionType, "Ljava/lang/Long;", "Z")
    } ?: throw PatchException("Expected one X-Lite inline-action constructor")
    val actionTypeClass = context.mutableClassDefBy(xLitePostActionType)
    val carrierField = actionTypeClass.fields.singleOrNull { field ->
        AccessFlags.STATIC.isSet(field.accessFlags) && field.type == xLitePostActionType &&
            field.name == "TwitterShare"
    } ?: throw PatchException("Expected X-Lite TwitterShare action constant")
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
            new-instance v0, $xLiteInlineActionEntryType
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
        method.name == name && method.parameterTypes.map { it.toString() } == parameters &&
            method.returnType == "Ljava/lang/Object;" ||
            method.name == name && parameters.isEmpty() && method.parameterTypes.isEmpty() &&
                method.returnType == "Ljava/lang/String;"
    } ?: throw PatchException("X-Lite inline helper $name was not found")

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
