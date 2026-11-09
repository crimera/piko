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
import app.crimera.patches.common.requireExactlyOne
import app.crimera.patches.common.scopedMatchAllOrNull
import app.crimera.patches.newx.misc.inlineactions.provesParameterValue
import app.crimera.patches.newx.misc.inlineactions.provesValueFromInstruction
import app.crimera.patches.newx.models.fieldForToStringLabel
import app.crimera.patches.newx.models.patchBridge
import app.crimera.patches.newx.models.requireBridge
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.numberOfParameterRegisters
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val POST_TRANSLATOR_DESCRIPTOR = "Lapp/morphe/extension/newx/translation/PostTranslator;"
private const val OBJECT_DESCRIPTOR = "Ljava/lang/Object;"
private const val STRING_DESCRIPTOR = "Ljava/lang/String;"
private const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val COMPOSER_DESCRIPTOR = "Landroidx/compose/runtime/Composer;"
private const val GROK_PACKAGE = "Lcom/x/urt/items/post/translate/grok/"
private const val POST_AUTO_TRANSLATION_FEATURE_FLAG = "grok_translations_post_auto_translation_is_enabled"
private const val AUTO_TRANSLATOR_DESCRIPTOR = "Lapp/morphe/extension/newx/translation/AutoTranslate;"

internal data class ResolvedAutomaticTranslationPath(
    val quoteCompose: MutableMethod,
    val quoteModel: FieldReference,
    val stateFactory: MethodReference,
    val stateGetter: MethodReference,
    val stateSetter: MethodReference,
    val initial: FieldReference,
    val nativeTranslationGetter: ResolvedGetter,
    val nativeTranslatedType: String,
    val startGroup: MethodReference,
    val endGroup: MethodReference,
    val publishContent: MethodReference,
    val postIdGetter: ResolvedGetter,
    val postRepository: FieldReference,
    val quoteRepository: FieldReference,
    val completedContent: FieldReference,
    val uiConstructor: MethodReference,
    val hideEvent: String,
    val showEvent: String,
)

/** Resolve from the already-proven post request/state path, not from obfuscated presenter names. */
context(context: BytecodePatchContext)
internal fun resolveAutomaticTranslationPath(path: ResolvedTranslationPath): ResolvedAutomaticTranslationPath {
    val compose = path.presenterComposeMethod
    val presenter = context.mutableClassDefBy(path.presenterDescriptor)
    val uiState = compose.returnType
    val quote = requireExactlyOne(
        "NewX read-only translation presenter Compose path",
        Fingerprint(definingClass = GROK_PACKAGE, returnType = uiState,
            filters = listOf(string(POST_AUTO_TRANSLATION_FEATURE_FLAG))).scopedMatchAllOrNull().orEmpty()
            .map { it.method }.filter { method ->
                method.definingClass != path.presenterDescriptor &&
                    COMPOSER_DESCRIPTOR in method.parameterTypes.map(CharSequence::toString) &&
                    method.instructions.any { it.getReference<FieldReference>()?.type == path.presenterModelField.type }
            },
    )
    val quoteClass = context.mutableClassDefBy(quote.definingClass)
    val quoteCompose = requireExactlyOne("NewX mutable quote Compose method", quoteClass.methods.filter { it.toString() == quote.toString() })
    val quoteModel = requireExactlyOne("NewX quote translation model field", quoteClass.fields.filter {
        it.type == path.presenterModelField.type && !AccessFlags.STATIC.isSet(it.accessFlags)
    }).also { exposeField(it) }
    val stateInterface = path.loadingSingleton.definingClass.let { loading ->
        requireExactlyOne("NewX translation state interface", context.classDefBy(loading).interfaces)
    }
    val stateRead = requireExactlyOne("NewX presenter translation-state accessor", presenter.methods.filter {
        it.parameterTypes.isEmpty() && it.returnType == stateInterface
    })
    val stateGetter = requireExactlyOne("NewX Compose state read", stateRead.instructions.mapNotNull {
        it.getReference<MethodReference>()?.takeIf { ref -> ref.parameterTypes.isEmpty() && ref.returnType == OBJECT_DESCRIPTOR }
    }.distinctBy { it.toString() })
    val stateClass = context.mutableClassDefBy(stateGetter.definingClass)
    val stateSetter = requireExactlyOne("NewX Compose mutable-state setter", stateClass.methods.filter {
        it.name == "setValue" && it.parameterTypes.map(CharSequence::toString) == listOf(OBJECT_DESCRIPTOR) && it.returnType == "V"
    }).toReference().also { exposeMethod(it) }
    val constructors = presenter.methods.filter { it.name == "<init>" }
    val stateFactory = requireExactlyOne("NewX Compose mutable-state factory", constructors.flatMap { ctor ->
        ctor.instructions.mapNotNull { ins -> ins.getReference<MethodReference>()?.takeIf { ref ->
            ins.opcode in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) &&
                ref.parameterTypes.map(CharSequence::toString) == listOf(OBJECT_DESCRIPTOR) &&
                ref.returnType == stateGetter.definingClass
        } }
    }.distinctBy { it.toString() }).also { exposeMethod(it) }
    val initial = resolveStateSingleton("Idle", stateInterface)
    val translatedType = path.translatedPostConstructor.definingClass
    val nativeAdapter = requireExactlyOne("NewX native translated-post adapter", constructors.flatMap { ctor ->
        ctor.instructions.mapNotNull { ins -> ins.getReference<MethodReference>()?.takeIf { ref ->
            ins.opcode in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) && ref.returnType == translatedType &&
                ref.parameterTypes.size == 1
        } }
    }.distinctBy { it.toString() })
    val nativeType = nativeAdapter.parameterTypes.single().toString()
    val nativeGetter = requireExactlyOne("NewX original translation model read", constructors.flatMap { ctor ->
        val instructions = ctor.instructions.toList()
        instructions.withIndex().mapNotNull { (index, ins) ->
            val ref = ins.getReference<MethodReference>() ?: return@mapNotNull null
            val result = instructions.getOrNull(index + 1) as? OneRegisterInstruction ?: return@mapNotNull null
            val check = instructions.getOrNull(index + 2) as? TwoRegisterInstruction ?: return@mapNotNull null
            ref.takeIf {
                ins.opcode in invokeOpcodes() && ref.parameterTypes.isEmpty() &&
                    ref.definingClass in context.methodsForModel(path.presenterModelField.type).map { it.definingClass } &&
                    instructions[index + 1].opcode == Opcode.MOVE_RESULT_OBJECT &&
                    instructions[index + 2].opcode == Opcode.INSTANCE_OF && check.registerB == result.registerA &&
                    instructions[index + 2].getReference<TypeReference>()?.type == nativeType
            }
        }
    }.distinctBy { it.toString() }).toResolvedGetter(context).also { exposeGetter(it) }

    // The frame is anchored to the actual Composer argument before any branch in the native path.
    val composerParam = compose.parameterRegister(requireExactlyOne("NewX translation Composer parameter",
        compose.parameterTypes.withIndex().filter { it.value.toString() == COMPOSER_DESCRIPTOR }).index)
    val ins = compose.instructions.toList()
    val frame = requireExactlyOne("NewX translation Composer frame", ins.withIndex().mapNotNull { (index, instruction) ->
        val type = instruction.getReference<TypeReference>()?.type ?: return@mapNotNull null
        val register = (instruction as? OneRegisterInstruction)?.registerA ?: return@mapNotNull null
        type.takeIf {
            instruction.opcode == Opcode.CHECK_CAST && type.startsWith("Landroidx/compose/runtime/") &&
                compose.provesParameterValue(index, register, composerParam)
        }
    })
    val start = requireExactlyOne("NewX translation Compose group start", ins.mapNotNull {
        it.getReference<MethodReference>()?.takeIf { ref -> ref.definingClass == frame &&
            ref.parameterTypes.map(CharSequence::toString) == listOf("I") && ref.returnType == "V" }
    }.distinctBy { it.toString() })
    val end = requireExactlyOne("NewX translation Compose group end", ins.mapNotNull {
        it.getReference<MethodReference>()?.takeIf { ref -> ref.definingClass == frame &&
            ref.parameterTypes.map(CharSequence::toString) == listOf("Z") && ref.returnType == "V" }
    }.distinctBy { it.toString() })
    val publishContent = requireExactlyOne("NewX translated content publication", ins.mapNotNull { instruction ->
        instruction.getReference<MethodReference>()?.takeIf { ref ->
            instruction.opcode in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) && ref.returnType == "V" &&
                translatedType in ref.parameterTypes.map(CharSequence::toString) && COMPOSER_DESCRIPTOR in ref.parameterTypes.map(CharSequence::toString)
        }
    }.distinctBy { it.toString() })
    val publishParams = publishContent.parameterTypes.map(CharSequence::toString)
    if (publishParams.size != 5 || publishParams[2] != translatedType || publishParams[3] != COMPOSER_DESCRIPTOR || publishParams[4] != "I") {
        throw PatchException("NewX translation content publication contract changed: $publishContent")
    }
    fun repository(owner: MutableClass, method: Method): FieldReference {
        val code = method.instructions.toList()
        val call = requireExactlyOne("NewX translated content publication call in $method", code.withIndex().filter {
            it.value.getReference<MethodReference>()?.toString() == publishContent.toString()
        })
        val argument = call.value.registersUsed[0]
        return requireExactlyOne("NewX translation repository flowing to publication in $method", code.withIndex().mapNotNull { (index, instruction) ->
            val field = instruction.getReference<FieldReference>() ?: return@mapNotNull null
            val read = instruction as? TwoRegisterInstruction ?: return@mapNotNull null
            field.takeIf {
                instruction.opcode == Opcode.IGET_OBJECT && field.definingClass == owner.type &&
                    field.type == publishParams[0] && method.provesParameterValue(index, read.registerB, method.p0Register) &&
                    method.provesValueFromInstruction(index, read.registerA, call.index, argument)
            }
        }.distinctBy { it.toString() }).also { exposeField(it) }
    }
    val idGetter = requireExactlyOne("NewX translated-post id getter", ins.mapNotNull { it.getReference<MethodReference>() }.filter {
        it.returnType == publishParams[1] && it.parameterTypes.isEmpty() &&
            it.definingClass in context.methodsForModel(path.presenterModelField.type).map { model -> model.definingClass }
    }.distinctBy { it.toString() }).toResolvedGetter(context).also { exposeGetter(it) }
    val completedClass = context.mutableClassDefBy(path.completedConstructor.definingClass)
    val completedContent = requireExactlyOne("NewX completed translation content label", completedClass.methods.filter {
        it.name == "toString" && it.hasString("Completed(content=")
    }).fieldForToStringLabel("Completed(content=").also { exposeField(it) }
    val uiConstructor = requireExactlyOne("NewX translation UI-state constructor", context.mutableClassDefBy(uiState).methods.filter {
        it.name == "<init>" && it.parameterTypes.map(CharSequence::toString) == listOf(
            "Z", stateInterface, "Z", STRING_DESCRIPTOR, "Z", path.presenterComposeMethod.returnType.let {
                val match = GrokPostStateFingerprint.scopedMatchAllOrNull().orEmpty()
                requireExactlyOne("NewX translation UI state", match).fieldForToStringLabel(", settingsSheetState=").type
            }, FUNCTION1_DESCRIPTOR,
        )
    }).toReference().also { exposeMethod(it) }
    fun event(label: String) = requireExactlyOne("NewX translation '$label' event",
        Fingerprint(definingClass = GROK_PACKAGE, name = "toString", returnType = STRING_DESCRIPTOR,
            parameters = emptyList(), filters = listOf(string(label))).scopedMatchAllOrNull().orEmpty()).originalClassDef.type
    return ResolvedAutomaticTranslationPath(quoteCompose, quoteModel, stateFactory, stateGetter, stateSetter, initial,
        nativeGetter, nativeType, start, end, publishContent, idGetter, repository(presenter, compose), repository(quoteClass, quoteCompose),
        completedContent, uiConstructor, event("HideTranslation"), event("RequestTranslation"))
}

/** Both native presenter kinds carry the same model contract. Unknown objects fail at the cast. */
internal fun Block.readAutomaticModel(path: ResolvedTranslationPath, automatic: ResolvedAutomaticTranslationPath,
                                      value: Int, scratch: Int) {
    instanceOf(scratch, value, path.presenterDescriptor)
    ifEqz(scratch, Target.Local("quote_model"))
    checkCast(value, path.presenterDescriptor)
    iget(value, value, path.presenterModelField)
    goto(Target.Local("model_ready"))
    label("quote_model")
    checkCast(value, automatic.quoteCompose.definingClass)
    iget(value, value, automatic.quoteModel)
    label("model_ready")
}

context(context: BytecodePatchContext)
internal fun patchAutomaticTranslation(path: ResolvedTranslationPath, automatic: ResolvedAutomaticTranslationPath) {
    val extension = context.mutableClassDefBy(AUTO_TRANSLATOR_DESCRIPTOR)
    fun bridge(name: String, params: String, result: String, locals: Int = 1) =
        extension.requireBridge(name, params, result).withLocals(extension, locals)
    val factory = bridge("newState", OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR)
    factory.patchBridge {
        invokeStatic(automatic.stateFactory, factory.p0Register)
        moveResult(0, OBJECT_DESCRIPTOR)
        returnObject(0)
    }
    val getter = bridge("getState", OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR)
    getter.patchBridge {
        checkCast(getter.p0Register, automatic.stateGetter.definingClass)
        invokeVirtual(automatic.stateGetter, getter.p0Register)
        moveResult(0, OBJECT_DESCRIPTOR)
        returnObject(0)
    }
    val setter = bridge("setState", OBJECT_DESCRIPTOR.repeat(2), "V")
    setter.patchBridge {
        checkCast(setter.p0Register, automatic.stateSetter.definingClass)
        invokeVirtual(automatic.stateSetter, setter.p0Register, setter.p0Register + 1)
        returnVoid()
    }
    listOf("initialState" to automatic.initial, "loadingState" to path.loadingSingleton).forEach { (name, field) ->
        bridge(name, "", OBJECT_DESCRIPTOR).patchBridge { sget(0, field)
        returnObject(0) }
    }
    listOf("isError" to path.errorSingleton.definingClass, "isCompleted" to path.completedConstructor.definingClass).forEach { (name, type) ->
        val method = bridge(name, OBJECT_DESCRIPTOR, "Z")
        method.patchBridge { instanceOf(0, method.p0Register, type)
        returnValue(0) }
    }
    val identity = bridge("readIdentity", OBJECT_DESCRIPTOR, OBJECT_DESCRIPTOR)
    identity.patchBridge {
        checkCast(identity.p0Register, path.presenterModelField.type)
        invokeGetter(automatic.postIdGetter, identity.p0Register)
        moveResult(0, OBJECT_DESCRIPTOR)
        returnObject(0)
    }
    val native = bridge("hasNativeTranslation", OBJECT_DESCRIPTOR, "Z")
    native.patchBridge {
        checkCast(native.p0Register, path.presenterModelField.type)
        invokeGetter(automatic.nativeTranslationGetter, native.p0Register)
        moveResult(0, OBJECT_DESCRIPTOR)
        instanceOf(0, 0, automatic.nativeTranslatedType)
        returnValue(0)
    }
    val event = bridge("eventKind", OBJECT_DESCRIPTOR, "I")
    event.patchBridge {
        instanceOf(0, event.p0Register, automatic.hideEvent)
        ifEqz(0, Target.Local("show"))
        constInt(0, 1)
        returnValue(0)
        label("show")
        instanceOf(0, event.p0Register, automatic.showEvent)
        ifEqz(0, Target.Local("other"))
        constInt(0, 2)
        returnValue(0)
        label("other")
        constInt(0, 0)
        returnValue(0)
    }
    val render = bridge("render", OBJECT_DESCRIPTOR.repeat(5) + "Z", OBJECT_DESCRIPTOR, 10)
    render.patchBridge {
        val p = render.p0Register // v10..v15: state, shown, presenter, composer, callback, enabled
        move(3, p + 3, OBJECT_DESCRIPTOR)
        checkCast(3, automatic.startGroup.definingClass)
        constInt(4, 0x70696b6f)
        invokeVirtual(automatic.startGroup, 3, 4)
        invokeStatic(methodReference("$AUTO_TRANSLATOR_DESCRIPTOR->getState($OBJECT_DESCRIPTOR)$OBJECT_DESCRIPTOR"), p)
        moveResult(8, OBJECT_DESCRIPTOR)
        checkCast(8, path.loadingSingleton.let { context.classDefBy(it.definingClass).interfaces.single() })
        invokeStatic(methodReference("$AUTO_TRANSLATOR_DESCRIPTOR->getState($OBJECT_DESCRIPTOR)$OBJECT_DESCRIPTOR"), p + 1)
        moveResult(9, OBJECT_DESCRIPTOR)
        checkCast(9, "Ljava/lang/Boolean;")
        invokeVirtual(methodReference("Ljava/lang/Boolean;->booleanValue()Z"), 9)
        moveResult(9, "Z")
        constInt(2, 0)
        ifEqz(p + 5, Target.Local("publish"))
        ifEqz(9, Target.Local("publish"))
        instanceOf(0, 8, path.completedConstructor.definingClass)
        ifEqz(0, Target.Local("publish"))
        move(2, 8, OBJECT_DESCRIPTOR)
        checkCast(2, path.completedConstructor.definingClass)
        iget(2, 2, automatic.completedContent)
        checkCast(2, path.translatedPostConstructor.definingClass)
        label("publish")
        move(1, p + 2, OBJECT_DESCRIPTOR)
        invokeStatic(methodReference("$POST_TRANSLATOR_DESCRIPTOR->readPostModel($OBJECT_DESCRIPTOR)$OBJECT_DESCRIPTOR"), 1)
        moveResult(1, OBJECT_DESCRIPTOR)
        checkCast(1, path.presenterModelField.type)
        invokeGetter(automatic.postIdGetter, 1)
        moveResult(1, automatic.postIdGetter.reference.returnType)
        move(0, p + 2, OBJECT_DESCRIPTOR)
        instanceOf(4, 0, path.presenterDescriptor)
        ifEqz(4, Target.Local("quote_repo"))
        checkCast(0, path.presenterDescriptor)
        iget(0, 0, automatic.postRepository)
        goto(Target.Local("repo_ready"))
        label("quote_repo")
        checkCast(0, automatic.quoteCompose.definingClass)
        iget(0, 0, automatic.quoteRepository)
        label("repo_ready")
        constInt(4, 0)
        invokeStatic(automatic.publishContent, 0, 1, 2, 3, 4)
        invokeVirtual(automatic.endGroup, 3, 4)
        // Native UI state retains the app's loading/error/toggle behavior.
        newInstance(0, automatic.uiConstructor.definingClass)
        move(1, 9, "Z")
        move(2, 8, OBJECT_DESCRIPTOR)
        constInt(3, 1)
        constInt(4, 0)
        constInt(5, 0)
        constInt(6, 0)
        move(7, p + 4, OBJECT_DESCRIPTOR)
        checkCast(7, FUNCTION1_DESCRIPTOR)
        invokeDirect(automatic.uiConstructor, 0, 1, 2, 3, 4, 5, 6, 7)
        returnObject(0)
    }
    listOf(path.presenterComposeMethod, automatic.quoteCompose).forEach { method ->
        val composer = method.parameterRegister(requireExactlyOne("NewX automatic translation Composer argument",
            method.parameterTypes.withIndex().filter { it.value.toString() == COMPOSER_DESCRIPTOR }).index)
        method.insertHook(0, excludedRegisters = (method.p0Register until method.p0Register + method.numberOfParameterRegisters).toSet(),
            relocateBranchTargets = false) {
            val owner = scratchRegister(RegisterLimit.FOUR_BIT)
            val frame = scratchRegister(RegisterLimit.FOUR_BIT)
            val result = scratchRegister(RegisterLimit.FOUR_BIT)
            move(owner, method.p0Register, OBJECT_DESCRIPTOR)
            move(frame, composer, OBJECT_DESCRIPTOR)
            invokeStatic(methodReference("$AUTO_TRANSLATOR_DESCRIPTOR->compose($OBJECT_DESCRIPTOR$OBJECT_DESCRIPTOR)$OBJECT_DESCRIPTOR"), owner, frame)
            moveResult(result, OBJECT_DESCRIPTOR)
            ifEqz(result, Target.Original)
            checkCast(result, method.returnType)
            returnObject(result)
        }
    }
    patchAutomaticQuoteHeader(path)
}

context(context: BytecodePatchContext)
private fun patchAutomaticQuoteHeader(path: ResolvedTranslationPath) {
    val stateType = path.presenterComposeMethod.returnType
    val modifier = "Landroidx/compose/ui/Modifier;"
    // The full renderer consumes the same semantic UI state plus Modifier. The read-only renderer
    // lacks Modifier and only emits TranslatedReadOnly; redirect it only for our own states.
    val readOnly = requireExactlyOne("NewX read-only translation header",
        Fingerprint(definingClass = "Lcom/x/urt/", returnType = "V",
            filters = listOf(fieldAccess(name = "TranslatedReadOnly"))).scopedMatchAllOrNull().orEmpty()
            .map { it.method }.filter { method ->
                AccessFlags.STATIC.isSet(method.accessFlags) &&
                    method.parameterTypes.map(CharSequence::toString) == listOf(stateType, COMPOSER_DESCRIPTOR, "I")
            })
    val header = requireExactlyOne("NewX common translation header call", readOnly.instructions.mapNotNull { ins ->
        ins.getReference<MethodReference>()?.takeIf { ref ->
            ins.opcode in setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) && ref.returnType == "V" &&
                modifier in ref.parameterTypes.map(CharSequence::toString) &&
                COMPOSER_DESCRIPTOR in ref.parameterTypes.map(CharSequence::toString) &&
                STRING_DESCRIPTOR in ref.parameterTypes.map(CharSequence::toString)
        }
    }.distinctBy { it.toString() })
    val full = requireExactlyOne("NewX interactive translation header",
        Fingerprint(definingClass = "Lcom/x/urt/items/post/", returnType = "V",
            filters = listOf(methodCall(smali = header.toString()))).scopedMatchAllOrNull().orEmpty()
            .map { it.method }.filter { method ->
                AccessFlags.STATIC.isSet(method.accessFlags) &&
                    method.parameterTypes.map(CharSequence::toString) == listOf(stateType, modifier, COMPOSER_DESCRIPTOR, "I")
            })
    val modifierSingleton = requireExactlyOne("NewX translation header empty Modifier", full.instructions.mapNotNull {
        it.getReference<FieldReference>()?.takeIf { ref -> ref.type == modifier || context.isAssignableTo(ref.type, modifier) }
    }.distinctBy { it.toString() })
    val owner = context.mutableClassDefBy(readOnly.definingClass)
    val method = requireExactlyOne("NewX mutable read-only translation header", owner.methods.filter { it.toString() == readOnly.toString() })
    method.insertHook(0, excludedRegisters = (method.p0Register until method.p0Register + method.numberOfParameterRegisters).toSet(),
        relocateBranchTargets = false) {
        val state = scratchRegister(RegisterLimit.FOUR_BIT)
        val handled = scratchRegister(RegisterLimit.FOUR_BIT)
        val frame = scratchRegister(RegisterLimit.FOUR_BIT)
        val mod = scratchRegister(RegisterLimit.FOUR_BIT)
        move(state, method.p0Register, OBJECT_DESCRIPTOR)
        invokeStatic(methodReference("$AUTO_TRANSLATOR_DESCRIPTOR->ownsState($OBJECT_DESCRIPTOR)Z"), state)
        moveResult(handled, "Z")
        ifEqz(handled, Target.Original)
        move(frame, method.p0Register + 1, OBJECT_DESCRIPTOR)
        sget(mod, modifierSingleton)
        constInt(handled, 0)
        invokeStatic(full.toReference(), state, mod, frame, handled)
        returnVoid()
    }
}
