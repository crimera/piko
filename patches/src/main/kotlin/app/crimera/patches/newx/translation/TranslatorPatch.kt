/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.translation

import app.crimera.bytecode.Block
import app.crimera.bytecode.RegisterLimit
import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.common.isAssignableTo
import app.crimera.patches.common.requireAtMostOne
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.scopedMatchAllOrNull
import app.crimera.patches.common.valueReachesRegister
import app.crimera.patches.newx.misc.canonicalurls.resolveUrlEntityFields
import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.misc.postoptions.TRANSLATE_ACTION
import app.crimera.patches.newx.misc.postoptions.newXPostOption
import app.crimera.patches.newx.models.ResolvedNewXPostModels
import app.crimera.patches.newx.models.ResolvedNewXTimelineModels
import app.crimera.patches.newx.models.fieldForToStringLabel
import app.crimera.patches.newx.models.newXPostModelResolutionPatch
import app.crimera.patches.newx.models.newXTimelineModelResolutionPatch
import app.crimera.patches.newx.models.patchBridge
import app.crimera.patches.newx.models.requireBridge
import app.crimera.patches.newx.models.requirePublicFields
import app.crimera.patches.newx.models.resolveFieldAccessor
import app.crimera.patches.newx.models.resolvedNewXPostModels
import app.crimera.patches.newx.models.resolvedNewXTimelineModels
import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.Groups
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.settings.customScreen
import app.crimera.patches.settings.group
import app.crimera.patches.settings.settingStrings
import app.crimera.patches.settings.toggle
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.numberOfParameterRegisters
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.cloneMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference

private const val POST_TRANSLATOR_DESCRIPTOR =
    "Lapp/morphe/extension/newx/translation/PostTranslator;"
private const val TRANSLATOR_FRAGMENT_DESCRIPTOR =
    "Lapp/morphe/extension/newx/translation/TranslatorFragment;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val LIST_DESCRIPTOR = "Ljava/util/List;"
private const val MAP_DESCRIPTOR = "Ljava/util/Map;"
private const val UNIT_DESCRIPTOR = "Lkotlin/Unit;"
private const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val GROK_PACKAGE = "Lcom/x/urt/items/post/translate/grok/"

private const val TRANSLATE_METHOD = "translate(Ljava/lang/Object;Ljava/lang/Object;)Z"
private const val SHOULD_ENABLE_FEATURE_METHOD = "shouldEnableFeature(Z)Z"
private const val SHOULD_ENABLE_POLL_METHOD = "shouldEnablePollTranslation(Z)Z"
private const val SHOULD_SHOW_BUTTON_METHOD = "shouldShowButton(Z)Z"
private const val USE_ORIGINAL_SETTINGS_METHOD = "useOriginalSettings(Z)Z"
private const val POLL_CHOICES_METHOD = "pollChoicesFromBindings(Ljava/util/Map;)Ljava/util/List;"

private const val POST_TRANSLATION_FEATURE_FLAG = "grok_translations_post_translation_is_enabled"
private const val POST_AUTO_TRANSLATION_FEATURE_FLAG = "grok_translations_post_auto_translation_is_enabled"
private const val POLL_TRANSLATION_FEATURE_FLAG = "grok_translations_poll_translation_is_enabled"

private const val NO_POLL_LABEL = "piko_translation_no_poll"
private const val NO_CANONICAL_POST_LABEL = "piko_translation_no_canonical_post"
private const val NO_NOTE_TRANSLATION_TEXT_LABEL = "piko_translation_no_note_text"
private const val NO_NOTE_TRANSLATION_ENTITIES_LABEL = "piko_translation_no_note_entities"

private val FIELD_READ_OPCODES =
    setOf(
        Opcode.IGET,
        Opcode.IGET_WIDE,
        Opcode.IGET_OBJECT,
        Opcode.IGET_BOOLEAN,
        Opcode.IGET_BYTE,
        Opcode.IGET_CHAR,
        Opcode.IGET_SHORT,
    )

private object TranslatedPostFingerprint : Fingerprint(
    definingClass = "Lcom/x/groktranslate/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("TranslatedPost(text=")),
)

private object PostEntityListFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/text/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("PostEntityList(mentions=")),
)

private object DisplayTextRangeFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/text/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("DisplayTextRange(startIdx=")),
)

private object UrlEntityFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/text/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("UrlEntity(displayUrl=")),
)

private object NotePostFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/notes/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(
        string("NotePost(isExpandable="),
        string(", noteTweetResult="),
    ),
)

private object NotePostBodyFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/notes/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(
        string("NotePost(postEntityList="),
        string(", text="),
    ),
)

private object GrokTranslateRequestFingerprint : Fingerprint(
    definingClass = "Lcom/x/ai/grok/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("GrokTranslateRequest(id=")),
)

private object LegacyCardFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/cards/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("LegacyCard(cardPlatform=")),
)

private object StringValueFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/cards/",
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("StringValue(value=")),
)

private object GrokPostStateFingerprint : Fingerprint(
    definingClass = GROK_PACKAGE,
    name = "toString",
    returnType = STRING_DESCRIPTOR,
    parameters = emptyList(),
    filters = listOf(string("GrokTranslatePostState(shouldShowTranslation=")),
)

private data class ResolvedGetter(
    val reference: MethodReference,
    val isInterface: Boolean,
)

private data class BooleanResult(
    val resultIndex: Int,
    val resultRegister: Int,
)

private data class ResolvedTranslationPath(
    val fetchMethod: MutableMethod,
    val presenterDescriptor: String,
    val presenterParameterRegister: Int,
    val callbackParameterRegister: Int,
    val presenterModelField: FieldReference,
    val modelTextGetter: ResolvedGetter,
    val modelSourceGetter: ResolvedGetter,
    val modelCardGetter: ResolvedGetter,
    val cardBindingsGetter: ResolvedGetter,
    val stringValueClass: String,
    val stringValueField: FieldReference,
    val translatedPostConstructor: MethodReference,
    val postEntityListConstructor: MethodReference,
    val completedConstructor: MethodReference,
    val presenterComposeMethod: MutableMethod,
    val loadingSingleton: FieldReference,
    val errorSingleton: FieldReference,
    val unitSingleton: FieldReference,
    val modelEligibilityGetter: MethodReference,
)

@Suppress("unused")
val newXTranslatorPatch =
    bytecodePatch(
        name = "NewX: Translator",
        description = "Adds a Translate post-menu option and optional inline translation with free or custom " +
            "translators (Google, FxTwitter, OpenAI-compatible, DeepL, LibreTranslate).",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)
        dependsOn(newXExtensionPatch, newXTimelineModelResolutionPatch, newXPostModelResolutionPatch)

        newXSettings {
            category(Categories.CONTENT) {
                group(Groups.TRANSLATION) {
                    toggle(
                        id = "newx.translation.menu_option",
                        strings = settingStrings("piko_newx_translation_menu_option"),
                        order = 100,
                        defaultValue = true,
                    )
                    toggle(
                        id = "newx.translation.force_button",
                        strings = settingStrings("piko_newx_translation_force_button"),
                        order = 200,
                        defaultValue = false,
                    )
                    toggle(
                        id = "newx.translation.inline",
                        strings = settingStrings("piko_newx_translation_inline"),
                        order = 300,
                        defaultValue = false,
                    )
                    customScreen(
                        id = "newx.translation.translator",
                        strings = settingStrings("piko_newx_translation_translator"),
                        order = 400,
                        fragmentClassDescriptor = TRANSLATOR_FRAGMENT_DESCRIPTOR,
                    )
                }
            }
        }
        newXPostOption(
            handlerDescriptor = POST_TRANSLATOR_DESCRIPTOR,
            actionName = TRANSLATE_ACTION,
            iconResourceName = "ic_vector_globe_stroke",
            order = 250,
        )

        execute {
            val timelineModels = resolvedNewXTimelineModels()
            patchPostMenuBridges(timelineModels)
            patchDisplayTextBridges(timelineModels, resolvedNewXPostModels())
            val path = resolveTranslationPath()
            patchTranslationBridges(path)
            patchInlineRequest(path)
            patchVisibility(path)
        }
    }

context(context: BytecodePatchContext)
private fun patchPostMenuBridges(timelineModels: ResolvedNewXTimelineModels) {
    val extensionClass = context.mutableClassDefBy(POST_TRANSLATOR_DESCRIPTOR)
    // The casts throw for a non-post timeline item; the extension treats that as "no post".
    val readPostText = extensionClass.requireBridge("readPostText", OBJECT_DESCRIPTOR, STRING_DESCRIPTOR)
    readPostText.patchBridge {
        val post = readPostText.p0Register
        checkCast(post, timelineModels.postDescriptor)
        invokeVirtual(timelineModels.postTextGetter, post)
        moveResult(post, STRING_DESCRIPTOR)
        returnObject(post)
    }
    val readPostId = extensionClass.requireBridge("readPostId", OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR)
    readPostId.patchBridge {
        val post = readPostId.p0Register
        checkCast(post, timelineModels.postDescriptor)
        invokeVirtual(timelineModels.postIdGetter, post)
        moveResult(post, timelineModels.postIdGetter.returnType)
        returnObject(post)
    }
}

/**
 * Bridges for the text X actually renders: the post's display range (which hides leading reply
 * mentions and trailing media/quote links) and its URL entities (t.co links shown as display URLs).
 * The range and entity getters are resolved on the post interface shared by the canonical post and
 * the inline translation presenter's model.
 */
context(context: BytecodePatchContext)
private fun patchDisplayTextBridges(
    timelineModels: ResolvedNewXTimelineModels,
    postModels: ResolvedNewXPostModels,
) {
    val extensionClass = context.mutableClassDefBy(POST_TRANSLATOR_DESCRIPTOR)

    val rangeMatch = DisplayTextRangeFingerprint.scopedMatchAllOrNull().orEmpty().let { matches ->
        requireExactlyOne("NewX display text range model", matches)
    }
    val rangeStartField = rangeMatch.toStringField("DisplayTextRange(startIdx=")
    val rangeEndField = rangeMatch.toStringField(", endIdx=")
    val entityListMatch = PostEntityListFingerprint.scopedMatchAllOrNull().orEmpty().let { matches ->
        requireExactlyOne("NewX post entity-list model", matches)
    }
    val urlsField = entityListMatch.toStringField(", urls=")
    val urlEntityMatch = UrlEntityFingerprint.scopedMatchAllOrNull().orEmpty().let { matches ->
        requireExactlyOne("NewX URL entity model", matches)
    }
    val urlEntityFields = resolveUrlEntityFields(urlEntityMatch)
    val urlField = urlEntityFields.url
    val displayUrlField = urlEntityFields.displayUrl
    listOf(rangeStartField, rangeEndField, urlsField, urlField, displayUrlField).forEach { exposeField(it) }
    listOf(rangeMatch, entityListMatch, urlEntityMatch).forEach { requirePublicClass(it.originalClassDef.type) }

    val postMethods = context.methodsForModel(postModels.canonicalPostDescriptor)
    fun interfaceGetter(label: String, returnType: String): ResolvedGetter =
        requireExactlyOne(
            label,
            postMethods.filter { method ->
                method.parameterTypes.isEmpty() && method.returnType == returnType &&
                    context.mutableClassDefByOrNull(method.definingClass)
                        ?.let { AccessFlags.INTERFACE.isSet(it.accessFlags) } == true
            },
        ).toResolvedGetter(context).also { exposeGetter(it) }
    val rangeGetter = interfaceGetter("NewX post display-range getter", rangeMatch.originalClassDef.type)
    val entityListGetter = interfaceGetter("NewX post entity-list getter", entityListMatch.originalClassDef.type)

    val notePostMatch =
        NotePostFingerprint.scopedMatchAllOrNull().orEmpty().let { matches ->
            requireExactlyOne("NewX note post model", matches)
        }
    val notePostDescriptor = notePostMatch.originalClassDef.type
    val noteResultField = notePostMatch.toStringField(", noteTweetResult=")
    exposeField(noteResultField)
    val noteResultBase = context.mutableClassDefByOrNull(noteResultField.type)
        ?: throw PatchException("NewX note translation result type is unavailable: ${noteResultField.type}")
    requirePublicClass(noteResultField.type)
    if (!AccessFlags.INTERFACE.isSet(noteResultBase.accessFlags) &&
        !AccessFlags.ABSTRACT.isSet(noteResultBase.accessFlags)
    ) {
        throw PatchException("NewX note translation result type is not an interface or abstract class: $noteResultBase")
    }
    val notePostBodyMatch =
        NotePostBodyFingerprint.scopedMatchAllOrNull().orEmpty().filter { match ->
            context.isAssignableTo(match.originalClassDef.type, noteResultField.type)
        }.let { matches ->
            requireExactlyOne("NewX note translation body model", matches)
        }
    val notePostBodyDescriptor = notePostBodyMatch.originalClassDef.type
    val noteTextField = notePostBodyMatch.method.resolveNotePostBodyTextField()
    val noteEntityListField = notePostBodyMatch.toStringField("NotePost(postEntityList=")
    if (noteTextField.type != STRING_DESCRIPTOR) {
        throw PatchException("NewX note translation body text is not a String: $noteTextField")
    }
    if (noteEntityListField.type != entityListMatch.originalClassDef.type) {
        throw PatchException(
            "NewX note translation entity list does not match the post entity-list model: " +
                "$noteEntityListField vs ${entityListMatch.originalClassDef.type}",
        )
    }
    requirePublicClass(notePostDescriptor)
    requirePublicClass(notePostBodyDescriptor)
    exposeField(noteTextField)
    exposeField(noteEntityListField)
    val notePostGetter = interfaceGetter("NewX post note-body getter", notePostDescriptor)

    val resultAccessor =
        context.mutableClassDefBy(timelineModels.postDescriptor)
            .resolveFieldAccessor(timelineModels.postResultField, "timeline post result")
    context.mutableClassDefBy(postModels.contextualPostDescriptor)
        .requirePublicFields(listOf(postModels.contextualCanonicalPostField))
    val readCanonicalPost = extensionClass.requireBridge("readCanonicalPost", OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR)
        .withLocals(extensionClass, 1)
    readCanonicalPost.patchBridge {
        val post = readCanonicalPost.p0Register
        val isContextual = 0
        checkCast(post, timelineModels.postDescriptor)
        val getter = resultAccessor.getter
        if (getter == null) {
            iget(post, post, resultAccessor.field)
        } else {
            invokeVirtual(getter, post)
            moveResult(post, resultAccessor.field.type)
        }
        instanceOf(isContextual, post, postModels.contextualPostDescriptor)
        ifEqz(isContextual, Target.Local(NO_CANONICAL_POST_LABEL))
        iget(post, post, postModels.contextualCanonicalPostField)
        returnObject(post)
        label(NO_CANONICAL_POST_LABEL)
        constInt(isContextual, 0)
        returnObject(isContextual)
    }

    fun getterBridge(name: String, getter: ResolvedGetter) {
        val bridge = extensionClass.requireBridge(name, OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR)
        bridge.patchBridge {
            val value = bridge.p0Register
            checkCast(value, getter.reference.definingClass)
            invokeGetter(getter, value)
            moveResult(value, getter.reference.returnType)
            returnObject(value)
        }
    }
    getterBridge("readDisplayTextRange", rangeGetter)
    getterBridge("readEntityList", entityListGetter)

    fun noteFieldBridge(name: String, returnType: String, field: FieldReference, nullLabel: String) {
        val bridge = extensionClass.requireBridge(name, OBJECT_DESCRIPTOR, returnType)
            .withLocals(extensionClass, 1)
        bridge.patchBridge {
            val post = bridge.p0Register
            val isNoteBody = 0
            checkCast(post, notePostGetter.reference.definingClass)
            invokeGetter(notePostGetter, post)
            moveResult(post, notePostGetter.reference.returnType)
            ifEqz(post, Target.Local(nullLabel))
            checkCast(post, notePostDescriptor)
            iget(post, post, noteResultField)
            instanceOf(isNoteBody, post, notePostBodyDescriptor)
            ifEqz(isNoteBody, Target.Local(nullLabel))
            checkCast(post, notePostBodyDescriptor)
            iget(post, post, field)
            returnObject(post)
            label(nullLabel)
            constInt(isNoteBody, 0)
            returnObject(isNoteBody)
        }
    }
    noteFieldBridge(
        "readNoteText",
        STRING_DESCRIPTOR,
        noteTextField,
        NO_NOTE_TRANSLATION_TEXT_LABEL,
    )
    noteFieldBridge(
        "readNoteEntityList",
        OBJECT_DESCRIPTOR,
        noteEntityListField,
        NO_NOTE_TRANSLATION_ENTITIES_LABEL,
    )

    fun fieldBridge(name: String, owner: String, field: FieldReference, returnType: String) {
        val bridge = extensionClass.requireBridge(name, OBJECT_DESCRIPTOR, returnType)
        bridge.patchBridge {
            val value = bridge.p0Register
            checkCast(value, owner)
            iget(value, value, field)
            if (returnType == "I") returnValue(value) else returnObject(value)
        }
    }
    fieldBridge("readRangeStart", rangeMatch.originalClassDef.type, rangeStartField, "I")
    fieldBridge("readRangeEnd", rangeMatch.originalClassDef.type, rangeEndField, "I")
    fieldBridge("readUrlEntities", entityListMatch.originalClassDef.type, urlsField, LIST_DESCRIPTOR)
    fieldBridge("readUrlEntityUrl", urlEntityMatch.originalClassDef.type, urlField, STRING_DESCRIPTOR)
    fieldBridge("readUrlEntityDisplayUrl", urlEntityMatch.originalClassDef.type, displayUrlField, STRING_DESCRIPTOR)
}

/**
 * The model field printed after [label] in this match's `toString`. R8 may outline small
 * `toString` bodies into a shared static `StringBuilder` helper (e.g. `m(int, String, int, String,
 * String)` building `p1 + p0 + p3 + p2 + p4`); then the field is the argument the helper appends
 * immediately after the label's argument. That trace is exact, so it wins: the generic label lookup
 * pairs labels with reads by instruction order and picked `endIdx` for `startIdx` on 12.38.
 */
context(context: BytecodePatchContext)
private fun Match.toStringField(label: String): FieldReference =
    method.outlinedToStringField(label) ?: fieldForToStringLabel(label)

/**
 * The concrete note body reads its text field after the `, inlineMedia=` label, then passes both
 * values to an outlined formatter. Since the text label is not adjacent to the field read, use the
 * model invariant that text is the only String-typed instance field read in this `toString`.
 */
internal fun Method.resolveNotePostBodyTextField(): FieldReference {
    val candidates =
        instructions.mapNotNull { instruction ->
            if (instruction.opcode !in FIELD_READ_OPCODES) return@mapNotNull null
            instruction.getReference<FieldReference>()
        }.filter { field ->
            field.definingClass == definingClass && field.type == STRING_DESCRIPTOR
        }.distinctBy { field -> field.toString() }
    return requireExactlyOne("NewX note post body text field in $this", candidates)
}

context(context: BytecodePatchContext)
private fun Method.outlinedToStringField(label: String): FieldReference? {
    val instructions = instructions.toList()
    fun definedAt(register: Int, callIndex: Int, matches: (Int) -> Boolean): List<Int> =
        instructions.indices.filter { index ->
            index < callIndex && matches(index) &&
                instructions.valueReachesRegister(
                    index,
                    (instructions[index] as OneRegisterInstruction).registerA,
                    callIndex,
                    register,
                )
        }
    val candidates = instructions.withIndex().flatMap { (callIndex, call) ->
        if (call.opcode !in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)) return@flatMap emptyList()
        val helper = call.getReference<MethodReference>() ?: return@flatMap emptyList()
        val appendOrder = context.classDefByOrNull(helper.definingClass)?.methods
            ?.singleOrNull { it.toReference().toString() == helper.toString() }
            ?.stringBuilderParameterOrder() ?: return@flatMap emptyList()
        val arguments = call.registersUsed
        val labelSlots = arguments.indices.filter { slot ->
            definedAt(arguments[slot], callIndex) { index ->
                instructions[index].getReference<StringReference>()?.string == label
            }.isNotEmpty()
        }
        labelSlots.flatMap { labelSlot ->
            val fieldSlot = appendOrder.getOrNull(appendOrder.indexOf(labelSlot) + 1) ?: return@flatMap emptyList()
            val register = arguments.getOrNull(fieldSlot) ?: return@flatMap emptyList()
            definedAt(register, callIndex) { index ->
                instructions[index].opcode in FIELD_READ_OPCODES &&
                    instructions[index].getReference<FieldReference>()?.definingClass == definingClass
            }.mapNotNull { index -> instructions[index].getReference<FieldReference>() }
        }
    }.distinctBy { it.toString() }
    return requireAtMostOne("NewX outlined toString field for '$label' in $this", candidates)
}

/**
 * Parameter slots of a static `StringBuilder` helper in the order they are written: the
 * `StringBuilder(String)` seed, then each `append`. Null unless every write reads a parameter.
 */
private fun Method.stringBuilderParameterOrder(): List<Int>? {
    if (!AccessFlags.STATIC.isSet(accessFlags)) return null
    val implementation = implementation ?: return null
    val parameterStart = implementation.registerCount - parameterTypes.sumOf { type ->
        if (type.toString() == "J" || type.toString() == "D") 2 else 1
    }
    val order = mutableListOf<Int>()
    for (instruction in implementation.instructions) {
        val reference = instruction.getReference<MethodReference>() ?: continue
        if (reference.definingClass != "Ljava/lang/StringBuilder;") continue
        val isWrite = (reference.name == "<init>" && reference.parameterTypes.size == 1) || reference.name == "append"
        if (!isWrite) continue
        val slot = instruction.registersUsed.getOrNull(1)?.minus(parameterStart) ?: return null
        if (slot < 0) return null
        order += slot
    }
    return order.takeIf { it.isNotEmpty() }
}

context(context: BytecodePatchContext)
private fun resolveTranslationPath(): ResolvedTranslationPath {
    val requestClass =
        GrokTranslateRequestFingerprint.scopedMatchAllOrNull().orEmpty().let { matches ->
            requireExactlyOne("NewX Grok translation request model", matches).originalClassDef
        }
    val requestConstructor =
        requireExactlyOne(
            "NewX Grok translation request constructor",
            requestClass.methods.filter { method ->
                method.name == "<init>" &&
                    method.parameterTypes.map(CharSequence::toString) ==
                    listOf(STRING_DESCRIPTOR, STRING_DESCRIPTOR, STRING_DESCRIPTOR)
            },
        )
    val fetchMatches =
        Fingerprint(
            definingClass = GROK_PACKAGE,
            returnType = OBJECT_DESCRIPTOR,
            filters = listOf(string("POST"), methodCall(smali = requestConstructor.toString())),
        ).scopedMatchAllOrNull().orEmpty()
    val fetchMethod = requireExactlyOne("NewX post-specific Grok translation request handler", fetchMatches).method
    val isStatic = AccessFlags.STATIC.isSet(fetchMethod.accessFlags)
    val presenterDescriptor =
        if (isStatic) {
            requireExactlyOne(
                "NewX static post-translation presenter parameter",
                fetchMethod.parameterTypes.withIndex().filter { (_, type) ->
                    type.toString() == fetchMethod.definingClass &&
                        context.mutableClassDefByOrNull(type.toString())?.methods?.any { candidate ->
                            candidate.parameterTypes.any { it.toString() == COMPOSER_DESCRIPTOR }
                        } == true
                },
            ).value.toString()
        } else {
            fetchMethod.definingClass
        }
    val presenterClass = context.mutableClassDefBy(presenterDescriptor)
    val presenterParameterRegister =
        if (isStatic) {
            val index =
                requireExactlyOne(
                    "NewX static Grok handler presenter parameter index",
                    fetchMethod.parameterTypes.withIndex().filter { (_, type) -> type.toString() == presenterDescriptor },
                ).index
            fetchMethod.parameterRegister(index)
        } else {
            fetchMethod.p0Register
        }

    val legacyCardClass =
        LegacyCardFingerprint.scopedMatchAllOrNull().orEmpty().let { matches ->
            requireExactlyOne("NewX legacy card model", matches).originalClassDef
        }
    val presenterModelField =
        requireExactlyOne(
            "NewX Grok post presenter model field",
            presenterClass.fields.filter { field ->
                if (AccessFlags.STATIC.isSet(field.accessFlags)) return@filter false
                val methods = context.methodsForModel(field.type)
                methods.any { method ->
                    method.name == "getText" && method.parameterTypes.isEmpty() && method.returnType == STRING_DESCRIPTOR
                } && methods.any { method -> method.parameterTypes.isEmpty() && method.returnType == legacyCardClass.type }
            },
        )
    val modelMethods = context.methodsForModel(presenterModelField.type)
    val modelMethodOwners = modelMethods.map { it.definingClass }.toSet()
    val modelTextGetter =
        requireExactlyOne(
            "NewX post-model text getter",
            modelMethods.filter { method ->
                method.name == "getText" && method.parameterTypes.isEmpty() && method.returnType == STRING_DESCRIPTOR
            },
        ).toResolvedGetter(context)
    val modelCardGetter =
        requireExactlyOne(
            "NewX post-model legacy-card getter",
            modelMethods.filter { method ->
                method.parameterTypes.isEmpty() && method.returnType == legacyCardClass.type
            },
        ).toResolvedGetter(context)
    val cardBindingsGetter =
        requireExactlyOne(
            "NewX legacy-card bindings getter",
            legacyCardClass.methods.filter { method ->
                method.parameterTypes.isEmpty() && method.returnType == MAP_DESCRIPTOR
            },
        ).toResolvedGetter(context)
    val modelSourceGetter =
        resolveGoogleSourceLanguageGetter(fetchMethod, presenterModelField, modelMethodOwners::contains)
            .toResolvedGetter(context)

    val composeMethod =
        requireExactlyOne(
            "NewX Grok post presenter translation Compose method",
            presenterClass.methods.filter { method ->
                method.parameterTypes.any { it.toString() == COMPOSER_DESCRIPTOR } &&
                    method.hasString(POLL_TRANSLATION_FEATURE_FLAG) &&
                    method.hasString(POST_AUTO_TRANSLATION_FEATURE_FLAG) &&
                    method.instructions.any { instruction ->
                        val reference = instruction.getReference<MethodReference>() ?: return@any false
                        reference.returnType == "Z" && reference.parameterTypes.isEmpty() &&
                            reference.definingClass in modelMethodOwners
                    }
            },
        )
    val modelEligibilityGetter = resolveModelEligibilityGetter(composeMethod, modelMethodOwners)

    exposeField(presenterModelField)
    listOf(modelTextGetter, modelSourceGetter, modelCardGetter, cardBindingsGetter).forEach { exposeGetter(it) }

    val stringValue =
        StringValueFingerprint.scopedMatchAllOrNull().orEmpty().let { matches ->
            requireExactlyOne("NewX poll binding StringValue model", matches)
        }
    val stringValueField = stringValue.fieldForToStringLabel("StringValue(value=")
    exposeField(stringValueField)
    requirePublicClass(stringValue.originalClassDef.type)

    val translatedPost =
        TranslatedPostFingerprint.scopedMatchAllOrNull().orEmpty().let { matches ->
            requireExactlyOne("NewX translated post model", matches).originalClassDef
        }
    val entityList =
        PostEntityListFingerprint.scopedMatchAllOrNull().orEmpty().let { matches ->
            requireExactlyOne("NewX translated post entity-list model", matches).originalClassDef
        }
    requirePublicClass(translatedPost.type)
    requirePublicClass(entityList.type)
    val translatedPostConstructor =
        requireExactlyOne(
            "NewX translated post constructor",
            translatedPost.methods.filter { method ->
                method.name == "<init>" &&
                    method.parameterTypes.map(CharSequence::toString) ==
                    listOf(STRING_DESCRIPTOR, entityList.type, LIST_DESCRIPTOR)
            },
        ).toReference().also { exposeMethod(it) }
    val postEntityListConstructor =
        requireExactlyOne(
            "NewX empty translated post entity-list constructor",
            entityList.methods.filter { method ->
                method.name == "<init>" && method.parameterTypes.size == 6 &&
                    method.parameterTypes.all { it.toString() == LIST_DESCRIPTOR }
            },
        ).toReference().also { exposeMethod(it) }

    val stateInterface = resolveTranslationStateInterface()
    val completedClass = resolveStateClass("Completed(content=", stateInterface)
    requirePublicClass(completedClass.type)
    val completedConstructor =
        requireExactlyOne(
            "NewX completed translation state constructor",
            completedClass.methods.filter { method ->
                method.name == "<init>" &&
                    method.parameterTypes.map(CharSequence::toString) == listOf(OBJECT_DESCRIPTOR, STRING_DESCRIPTOR)
            },
        ).toReference().also { exposeMethod(it) }
    val loadingSingleton = resolveStateSingleton("Loading", stateInterface)
    val errorSingleton = resolveStateSingleton("Error", stateInterface)

    val callbackParameterIndex =
        requireExactlyOne(
            "NewX Grok translation event callback parameter",
            fetchMethod.parameterTypes.withIndex().filter { (_, type) ->
                context.isAssignableTo(type.toString(), FUNCTION1_DESCRIPTOR)
            },
        ).index
    val unitSingleton =
        requireExactlyOne(
            "NewX Grok translation handler Unit return singleton",
            fetchMethod.instructions.mapNotNull { instruction ->
                instruction.getReference<FieldReference>()
                    ?.takeIf { it.definingClass == UNIT_DESCRIPTOR && it.type == UNIT_DESCRIPTOR }
            }.distinctBy { it.toString() },
        )

    return ResolvedTranslationPath(
        fetchMethod = fetchMethod,
        presenterDescriptor = presenterDescriptor,
        presenterParameterRegister = presenterParameterRegister,
        callbackParameterRegister = fetchMethod.parameterRegister(callbackParameterIndex),
        presenterModelField = presenterModelField,
        modelTextGetter = modelTextGetter,
        modelSourceGetter = modelSourceGetter,
        modelCardGetter = modelCardGetter,
        cardBindingsGetter = cardBindingsGetter,
        stringValueClass = stringValue.originalClassDef.type,
        stringValueField = stringValueField,
        translatedPostConstructor = translatedPostConstructor,
        postEntityListConstructor = postEntityListConstructor,
        completedConstructor = completedConstructor,
        presenterComposeMethod = composeMethod,
        loadingSingleton = loadingSingleton,
        errorSingleton = errorSingleton,
        unitSingleton = unitSingleton,
        modelEligibilityGetter = modelEligibilityGetter,
    )
}

private fun Method.hasString(value: String): Boolean =
    instructions.any { instruction -> instruction.getReference<StringReference>()?.string == value }

private fun BytecodePatchContext.methodsForModel(modelDescriptor: String): List<Method> {
    val classes = mutableListOf<ClassDef>()
    val visited = mutableSetOf<String>()
    fun visit(descriptor: String) {
        if (!visited.add(descriptor)) return
        val classDef = classDefByOrNull(descriptor) ?: return
        classes += classDef
        classDef.interfaces.forEach { visit(it.toString()) }
        classDef.superclass?.let(::visit)
    }
    visit(modelDescriptor)
    return classes.flatMap { it.methods }
}

private fun Method.toResolvedGetter(context: BytecodePatchContext): ResolvedGetter =
    toReference().toResolvedGetter(context)

private fun MethodReference.toResolvedGetter(context: BytecodePatchContext): ResolvedGetter {
    val owner = context.mutableClassDefByOrNull(definingClass)
        ?: throw PatchException("NewX model getter owner was not found: $this")
    return ResolvedGetter(this, AccessFlags.INTERFACE.isSet(owner.accessFlags))
}

/**
 * The source-language getter is the post-model String getter whose result reaches
 * `Locale.forLanguageTag`, on a receiver read from the presenter's model field. Coroutine resume arms
 * may overwrite the presenter register, so only the local field-read → getter → Locale flow is traced.
 */
internal fun resolveGoogleSourceLanguageGetter(
    method: Method,
    modelField: FieldReference,
    isModelOwner: (String) -> Boolean,
): MethodReference {
    val instructions = method.instructions.toList()
    val localeCalls = instructions.withIndex().filter { (_, instruction) ->
        val reference = instruction.getReference<MethodReference>() ?: return@filter false
        reference.definingClass == "Ljava/util/Locale;" && reference.name == "forLanguageTag" &&
            reference.parameterTypes.map(CharSequence::toString) == listOf(STRING_DESCRIPTOR) &&
            reference.returnType == "Ljava/util/Locale;"
    }
    val candidates = localeCalls.flatMap { (localeIndex, localeInstruction) ->
        val sourceArgument = localeInstruction.registersUsed.singleOrNull() ?: return@flatMap emptyList()
        instructions.withIndex().mapNotNull { (getterIndex, getterInstruction) ->
            val getter = getterInstruction.getReference<MethodReference>() ?: return@mapNotNull null
            if (getterIndex >= localeIndex || getter.parameterTypes.isNotEmpty() || getter.returnType != STRING_DESCRIPTOR ||
                getterInstruction.opcode !in invokeOpcodes() || !isModelOwner(getter.definingClass)
            ) return@mapNotNull null
            val moveResult = instructions.getOrNull(getterIndex + 1)
            if (moveResult?.opcode != Opcode.MOVE_RESULT_OBJECT ||
                !instructions.valueReachesRegister(
                    getterIndex + 1,
                    (moveResult as OneRegisterInstruction).registerA,
                    localeIndex,
                    sourceArgument,
                )
            ) return@mapNotNull null
            val receiver = getterInstruction.registersUsed.firstOrNull() ?: return@mapNotNull null
            val modelFieldRead = instructions.withIndex().any { (fieldIndex, fieldInstruction) ->
                val reference = fieldInstruction.getReference<FieldReference>() ?: return@any false
                val registers = fieldInstruction as? TwoRegisterInstruction ?: return@any false
                fieldInstruction.opcode == Opcode.IGET_OBJECT && reference.toString() == modelField.toString() &&
                    fieldIndex < getterIndex &&
                    instructions.valueReachesRegister(fieldIndex, registers.registerA, getterIndex, receiver)
            }
            getter.takeIf { modelFieldRead }
        }
    }.distinctBy { it.toString() }
    return requireExactlyOne("NewX post-model source-language getter", candidates)
}

private fun resolveModelEligibilityGetter(method: MutableMethod, modelMethodOwners: Set<String>): MethodReference {
    val candidates = method.instructions.withIndex().mapNotNull { (index, instruction) ->
        val reference = instruction.getReference<MethodReference>() ?: return@mapNotNull null
        if (reference.parameterTypes.isNotEmpty() || reference.returnType != "Z" ||
            instruction.opcode !in invokeOpcodes() || reference.definingClass !in modelMethodOwners
        ) return@mapNotNull null
        reference.takeIf { findBooleanResult(method, index) != null }
    }.distinctBy { it.toString() }
    return requireExactlyOne("NewX post-model translation eligibility getter", candidates)
}

private fun invokeOpcodes(): Set<Opcode> =
    setOf(
        Opcode.INVOKE_INTERFACE,
        Opcode.INVOKE_INTERFACE_RANGE,
        Opcode.INVOKE_VIRTUAL,
        Opcode.INVOKE_VIRTUAL_RANGE,
    )

/** A boolean result that feeds exactly one conditional branch. */
private fun findBooleanResult(method: MutableMethod, invokeIndex: Int): BooleanResult? {
    val instructions = method.instructions
    val resultInstruction = instructions.getOrNull(invokeIndex + 1)
    if (resultInstruction?.opcode != Opcode.MOVE_RESULT) return null
    val resultRegister = (resultInstruction as OneRegisterInstruction).registerA
    val consumers = instructions.withIndex().filter { (index, instruction) ->
        index > invokeIndex + 1 && instruction.opcode in setOf(Opcode.IF_EQZ, Opcode.IF_NEZ) &&
            instructions.valueReachesRegister(
                invokeIndex + 1,
                resultRegister,
                index,
                (instruction as OneRegisterInstruction).registerA,
            )
    }
    return if (consumers.size == 1) BooleanResult(invokeIndex + 1, resultRegister) else null
}

context(context: BytecodePatchContext)
private fun resolveTranslationStateInterface(): String {
    val grokStateMatch =
        GrokPostStateFingerprint.scopedMatchAllOrNull().orEmpty().let { matches ->
            requireExactlyOne("NewX Grok post translation state model", matches)
        }
    val stateInterface = grokStateMatch.fieldForToStringLabel(", translationState=").type
    val stateClass = context.mutableClassDefByOrNull(stateInterface)
        ?: throw PatchException("NewX Grok translation-state interface type is unavailable: $stateInterface")
    if (!AccessFlags.INTERFACE.isSet(stateClass.accessFlags)) {
        throw PatchException("NewX Grok translation-state contract is not an interface: $stateInterface")
    }
    return stateInterface
}

context(context: BytecodePatchContext)
private fun resolveStateClass(stateLabel: String, stateInterface: String): ClassDef {
    val matches =
        Fingerprint(
            definingClass = "Lcom/x/",
            name = "toString",
            returnType = STRING_DESCRIPTOR,
            parameters = emptyList(),
            filters = listOf(string(stateLabel)),
        ).scopedMatchAllOrNull().orEmpty().filter { match ->
            context.isAssignableTo(match.originalClassDef.type, stateInterface)
        }
    return requireExactlyOne("NewX Grok $stateLabel state model", matches).originalClassDef
}

context(context: BytecodePatchContext)
private fun resolveStateSingleton(stateLabel: String, stateInterface: String): FieldReference {
    val stateClass = resolveStateClass(stateLabel, stateInterface)
    requirePublicClass(stateClass.type)
    val singleton =
        requireExactlyOne(
            "NewX Grok $stateLabel singleton field",
            stateClass.fields.filter { field ->
                AccessFlags.STATIC.isSet(field.accessFlags) && field.type == stateClass.type
            },
        )
    exposeField(singleton)
    return singleton
}

context(context: BytecodePatchContext)
private fun patchInlineRequest(path: ResolvedTranslationPath) {
    val method = path.fetchMethod
    val parameterRegisters =
        (method.p0Register until method.p0Register + method.numberOfParameterRegisters).toSet()
    method.insertHook(
        index = 0,
        excludedRegisters = parameterRegisters,
        relocateBranchTargets = false,
    ) {
        val handled = scratchRegister(RegisterLimit.FOUR_BIT)
        val presenter = scratchRegister(RegisterLimit.FOUR_BIT)
        val callback = scratchRegister(RegisterLimit.FOUR_BIT)
        move(presenter, path.presenterParameterRegister, OBJECT_DESCRIPTOR)
        move(callback, path.callbackParameterRegister, OBJECT_DESCRIPTOR)
        invokeStatic(methodReference("$POST_TRANSLATOR_DESCRIPTOR->$TRANSLATE_METHOD"), presenter, callback)
        moveResult(handled, "Z")
        ifEqz(handled, Target.Original)
        sget(handled, path.unitSingleton)
        returnObject(handled)
    }
}

context(context: BytecodePatchContext)
private fun patchTranslationBridges(path: ResolvedTranslationPath) {
    val extensionClass = context.mutableClassDefBy(POST_TRANSLATOR_DESCRIPTOR)

    fun modelStringBridge(name: String, getter: ResolvedGetter) {
        val bridge = extensionClass.requireBridge(name, OBJECT_DESCRIPTOR, STRING_DESCRIPTOR)
        bridge.patchBridge {
            val value = bridge.p0Register
            checkCast(value, path.presenterDescriptor)
            iget(value, value, path.presenterModelField)
            invokeGetter(getter, value)
            moveResult(value, STRING_DESCRIPTOR)
            returnObject(value)
        }
    }
    modelStringBridge("readText", path.modelTextGetter)
    val readPostModel = extensionClass.requireBridge("readPostModel", OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR)
    readPostModel.patchBridge {
        val value = readPostModel.p0Register
        checkCast(value, path.presenterDescriptor)
        iget(value, value, path.presenterModelField)
        returnObject(value)
    }
    modelStringBridge("readSourceLanguage", path.modelSourceGetter)

    val readPollChoices = extensionClass.requireBridge("readPollChoices", OBJECT_DESCRIPTOR, LIST_DESCRIPTOR)
    readPollChoices.patchBridge {
        val card = readPollChoices.p0Register
        checkCast(card, path.presenterDescriptor)
        iget(card, card, path.presenterModelField)
        invokeGetter(path.modelCardGetter, card)
        moveResult(card, path.modelCardGetter.reference.returnType)
        ifEqz(card, Target.Local(NO_POLL_LABEL))
        invokeGetter(path.cardBindingsGetter, card)
        moveResult(card, MAP_DESCRIPTOR)
        invokeStatic(methodReference("$POST_TRANSLATOR_DESCRIPTOR->$POLL_CHOICES_METHOD"), card)
        moveResult(card, LIST_DESCRIPTOR)
        returnObject(card)
        label(NO_POLL_LABEL)
        constInt(card, 0)
        invokeStatic(methodReference("$POST_TRANSLATOR_DESCRIPTOR->$POLL_CHOICES_METHOD"), card)
        moveResult(card, LIST_DESCRIPTOR)
        returnObject(card)
    }

    val readBindingString = extensionClass.requireBridge("readBindingString", OBJECT_DESCRIPTOR, STRING_DESCRIPTOR)
    readBindingString.patchBridge {
        val value = readBindingString.p0Register
        checkCast(value, path.stringValueClass)
        iget(value, value, path.stringValueField)
        returnObject(value)
    }

    extensionClass.injectStateBridge(
        extensionClass.requireBridge("publishLoading", OBJECT_DESCRIPTOR, "V"),
        path.loadingSingleton,
    )
    extensionClass.injectStateBridge(
        extensionClass.requireBridge("publishError", OBJECT_DESCRIPTOR, "V"),
        path.errorSingleton,
    )

    val completedBridge =
        extensionClass.requireBridge(
            "publishCompleted",
            "$OBJECT_DESCRIPTOR$STRING_DESCRIPTOR$LIST_DESCRIPTOR$STRING_DESCRIPTOR",
            "V",
        )
    completedBridge.withLocals(extensionClass, 7).injectCompletedBridge(
        path.postEntityListConstructor,
        path.translatedPostConstructor,
        path.completedConstructor,
        methodReference("Ljava/util/Collections;->emptyList()Ljava/util/List;"),
    )
}

private fun Block.invokeGetter(getter: ResolvedGetter, receiver: Int) {
    if (getter.isInterface) {
        invokeInterface(getter.reference, receiver)
    } else {
        invokeVirtual(getter.reference, receiver)
    }
}

/** Returns the bridge with at least [locals] low local registers, replacing it with a widened clone if needed. */
private fun MutableMethod.withLocals(owner: MutableClass, locals: Int): MutableMethod {
    val implementation = implementation
        ?: throw PatchException("NewX translation bridge has no implementation: $this")
    val currentLocals = implementation.registerCount - numberOfParameterRegisters
    if (maxOf(currentLocals, locals) + numberOfParameterRegisters > 16) {
        throw PatchException("NewX translation bridge has no low-register headroom: $this")
    }
    if (currentLocals >= locals) return this
    return cloneMutable(additionalRegisters = locals - currentLocals).also { clone ->
        owner.methods.remove(this)
        owner.methods.add(clone)
    }
}

private fun MutableClass.injectStateBridge(bridge: MutableMethod, singleton: FieldReference) {
    if (!AccessFlags.STATIC.isSet(bridge.accessFlags)) {
        throw PatchException("NewX translation state bridge must be static: $bridge")
    }
    val expanded = bridge.withLocals(this, 2)
    expanded.patchBridge {
        val callbackLocal = 0
        val stateLocal = 1
        move(callbackLocal, expanded.p0Register, OBJECT_DESCRIPTOR)
        sget(stateLocal, singleton)
        checkCast(callbackLocal, FUNCTION1_DESCRIPTOR)
        invokeInterface(
            methodReference("$FUNCTION1_DESCRIPTOR->invoke(Ljava/lang/Object;)Ljava/lang/Object;"),
            callbackLocal,
            stateLocal,
        )
        returnVoid()
    }
}

context(context: BytecodePatchContext)
private fun patchVisibility(path: ResolvedTranslationPath) {
    val factory =
        requireExactlyOne(
            "NewX Grok translate-button presenter factory",
            Fingerprint(filters = listOf(string(POST_TRANSLATION_FEATURE_FLAG)))
                .scopedMatchAllOrNull().orEmpty()
                .filter { match -> match.constructsPresenter(path.presenterDescriptor) },
        ).method

    patchFlagResult(factory, POST_TRANSLATION_FEATURE_FLAG, SHOULD_ENABLE_FEATURE_METHOD)
    patchEligibilityResult(factory, path.modelEligibilityGetter)
    patchFlagResult(path.presenterComposeMethod, POLL_TRANSLATION_FEATURE_FLAG, SHOULD_ENABLE_POLL_METHOD)
    patchFlagResult(path.presenterComposeMethod, POST_AUTO_TRANSLATION_FEATURE_FLAG, USE_ORIGINAL_SETTINGS_METHOD)
    patchEligibilityResult(path.presenterComposeMethod, path.modelEligibilityGetter)
}

context(context: BytecodePatchContext)
private fun Match.constructsPresenter(presenterDescriptor: String): Boolean {
    val instructions = method.instructions
    val related = instructions.withIndex().filter { (constructorIndex, instruction) ->
        val reference = instruction.getReference<MethodReference>() ?: return@filter false
        if (instruction.opcode !in setOf(Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE) ||
            reference.name != "<init>" || !context.isAssignableTo(reference.definingClass, presenterDescriptor)
        ) return@filter false
        val receiver = instruction.registersUsed.firstOrNull() ?: return@filter false
        instructions.withIndex().any { (newIndex, candidate) ->
            newIndex < constructorIndex && candidate.opcode == Opcode.NEW_INSTANCE &&
                candidate.getReference<TypeReference>()?.type == reference.definingClass &&
                instructions.valueReachesRegister(
                    newIndex,
                    (candidate as OneRegisterInstruction).registerA,
                    constructorIndex,
                    receiver,
                )
        }
    }
    return related.size == 1
}

private fun patchEligibilityResult(method: MutableMethod, getter: MethodReference) {
    val call =
        requireExactlyOne(
            "NewX model translation eligibility call in $method",
            method.instructions.withIndex().filter { (_, instruction) ->
                instruction.getReference<MethodReference>()?.toString() == getter.toString()
            },
        )
    val result = findBooleanResult(method, call.index)
        ?: throw PatchException("NewX eligibility result is not consumed by one conditional for $getter in $method")
    method.patchBooleanResult(result, SHOULD_SHOW_BUTTON_METHOD)
}

private fun patchFlagResult(method: MutableMethod, flag: String, bridgeMethod: String) {
    val instructions = method.instructions
    val results = instructions.withIndex().flatMap { (stringIndex, instruction) ->
        if (instruction.getReference<StringReference>()?.string != flag) return@flatMap emptyList()
        val literal = (instruction as? OneRegisterInstruction)?.registerA ?: return@flatMap emptyList()
        instructions.withIndex().mapNotNull { (invokeIndex, invoke) ->
            val reference = invoke.getReference<MethodReference>() ?: return@mapNotNull null
            if (reference.name != "getBoolean" || reference.returnType != "Z" ||
                invoke.opcode !in setOf(
                    Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE,
                    Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE,
                    Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE,
                )
            ) return@mapNotNull null
            val keyRegister = invoke.registersUsed.getOrNull(reference.stringParameterRegisterIndex(invoke.opcode))
                ?: return@mapNotNull null
            if (invokeIndex <= stringIndex ||
                !instructions.valueReachesRegister(stringIndex, literal, invokeIndex, keyRegister)
            ) return@mapNotNull null
            val resultInstruction = instructions.getOrNull(invokeIndex + 1)
            if (resultInstruction?.opcode != Opcode.MOVE_RESULT) return@mapNotNull null
            BooleanResult(invokeIndex + 1, (resultInstruction as OneRegisterInstruction).registerA)
        }
    }
    val result = requireExactlyOne("NewX translation feature flag '$flag' result in $method", results)
    method.patchBooleanResult(result, bridgeMethod)
}

private fun MethodReference.stringParameterRegisterIndex(opcode: Opcode): Int {
    val parameters = parameterTypes.map(CharSequence::toString)
    val parameterIndex = parameters.indexOf(STRING_DESCRIPTOR)
    if (parameterIndex < 0) return -1
    val receiverRegisters = if (opcode in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)) 0 else 1
    return receiverRegisters + parameters.take(parameterIndex).sumOf { descriptor ->
        if (descriptor == "J" || descriptor == "D") 2 else 1
    }
}

private fun MutableMethod.patchBooleanResult(result: BooleanResult, bridgeMethod: String) {
    insertHook(index = result.resultIndex + 1, relocateBranchTargets = true) {
        invokeStatic(methodReference("$POST_TRANSLATOR_DESCRIPTOR->$bridgeMethod"), result.resultRegister)
        moveResult(result.resultRegister, "Z")
    }
}

context(context: BytecodePatchContext)
private fun exposeField(reference: FieldReference) {
    val owner = context.mutableClassDefBy(reference.definingClass)
    val field =
        requireExactlyOne(
            "NewX translation field declaration $reference",
            owner.fields.filter { it.toString() == reference.toString() },
        )
    if (!AccessFlags.PUBLIC.isSet(field.accessFlags)) {
        throw PatchException("NewX translation field is not public: $reference")
    }
    owner.requirePublicFields(listOf(reference))
}

context(context: BytecodePatchContext)
private fun exposeGetter(getter: ResolvedGetter) {
    exposeMethod(getter.reference)
    requirePublicClass(getter.reference.definingClass)
}

context(context: BytecodePatchContext)
private fun exposeMethod(reference: MethodReference) {
    val owner = context.mutableClassDefBy(reference.definingClass)
    val method =
        requireExactlyOne(
            "NewX translation method declaration $reference",
            owner.methods.filter { candidate ->
                candidate.name == reference.name &&
                    candidate.parameterTypes.map(CharSequence::toString) ==
                    reference.parameterTypes.map(CharSequence::toString) &&
                    candidate.returnType == reference.returnType
            },
        )
    if (!AccessFlags.PUBLIC.isSet(method.accessFlags)) {
        throw PatchException("NewX translation method is not public: $reference")
    }
}

context(context: BytecodePatchContext)
private fun requirePublicClass(descriptor: String) {
    if (!AccessFlags.PUBLIC.isSet(context.mutableClassDefBy(descriptor).accessFlags)) {
        throw PatchException("NewX translation model type is not public: $descriptor")
    }
}

private fun Method.toReference(): MethodReference =
    ImmutableMethodReference(
        definingClass,
        name,
        parameterTypes.map(CharSequence::toString),
        returnType,
    )

private fun MutableMethod.parameterRegister(parameterIndex: Int): Int {
    val parameterStart = p0Register + if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
    return parameterStart + parameterTypes.take(parameterIndex).sumOf { type ->
        if (type.toString() == "J" || type.toString() == "D") 2 else 1
    }
}
