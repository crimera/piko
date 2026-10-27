/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.utils.Constants.DOWNLOAD_DESCRIPTOR
import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.p0Register
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.literal
import app.morphe.patcher.string
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.all.misc.resources.ResourceType
import app.morphe.patches.all.misc.resources.getResourceId
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.patcher.util.getReference
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.Field
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod

private const val REEL_DOWNLOAD_DESCRIPTOR = "$DOWNLOAD_DESCRIPTOR/ReelDownload;"

private const val CONTEXT_DESCRIPTOR = "Landroid/content/Context;"
private const val COLOR_FILTER_DESCRIPTOR = "Landroid/graphics/ColorFilter;"
private const val COLOR_STATE_LIST_DESCRIPTOR = "Landroid/content/res/ColorStateList;"
private const val DRAWABLE_DESCRIPTOR = "Landroid/graphics/drawable/Drawable;"
private const val SCALE_TYPE_DESCRIPTOR = "Landroid/widget/ImageView\$ScaleType;"
private const val CHAR_SEQUENCE_DESCRIPTOR = "Ljava/lang/CharSequence;"
private const val FUNCTION1_DESCRIPTOR = "Lkotlin/jvm/functions/Function1;"
private const val MEDIA_DESCRIPTOR = "Lcom/instagram/feed/media/Media;"

/** Names of the view properties the tap and long press handlers are stored under. */
private const val ON_CLICK_PROPERTY = "ON_CLICK"
private const val ON_LONG_CLICK_PROPERTY = "ON_LONG_CLICK"

/** The reels save button: its component sets this id and tag on its icon. */
private const val REEL_SAVE_BUTTON_ID = "save_button"

private const val IS_ENABLED = "$REEL_DOWNLOAD_DESCRIPTOR->isEnabled()Z"
private const val REGISTER = "$REEL_DOWNLOAD_DESCRIPTOR->register($OBJECT_DESCRIPTOR)V"
private const val BEGIN = "$REEL_DOWNLOAD_DESCRIPTOR->begin($OBJECT_DESCRIPTOR$OBJECT_DESCRIPTOR)V"
private const val ICON = "$REEL_DOWNLOAD_DESCRIPTOR->icon($DRAWABLE_DESCRIPTOR)$DRAWABLE_DESCRIPTOR"
private const val SELECTED = "$REEL_DOWNLOAD_DESCRIPTOR->selected(Z)Z"
private const val DESCRIPTION =
    "$REEL_DOWNLOAD_DESCRIPTOR->description($CHAR_SEQUENCE_DESCRIPTOR)$CHAR_SEQUENCE_DESCRIPTOR"
private const val VIEW_ID = "$REEL_DOWNLOAD_DESCRIPTOR->viewId(I)I"
private const val CLICK = "$REEL_DOWNLOAD_DESCRIPTOR->click($FUNCTION1_DESCRIPTOR)$FUNCTION1_DESCRIPTOR"
private const val LONG_CLICK = "$REEL_DOWNLOAD_DESCRIPTOR->longClick($FUNCTION1_DESCRIPTOR)$FUNCTION1_DESCRIPTOR"

private val STATIC_INVOKE_OPCODES = setOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE)

/**
 * Reels download button. The reels action column is a Litho UFI: like, comment, repost, share and save
 * are separate components, and views added by hand are rejected. The save button component (the
 * `ClipsSaveButtonComponent`) is therefore built a second time, right before the real one, and
 * registered with the extension. The hooks in its render method change only registered copies: the
 * icon, the tap and long press handlers, the selected state and the view id. The other handlers the
 * render method sets (impression logging) are left alone: the impression one fires whenever a reel is
 * shown.
 *
 * Every obfuscated member is resolved by behavior:
 *  - the component is the one render method that sets the `save_button` id and tag,
 *  - the builder is the one method that returns that component by constructing it,
 *  - the `Media` is reached through the one field path from the component to a `Media`
 *    (component state, then the item model), and the context through the one field path from the
 *    render composer to a `Context`.
 */
val reelDownloadButtonPatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    dependsOn(sharedExtensionPatch, settingsPatch, resourceMappingPatch)
    execute {
        injectReelDownloadButton(getResourceId(ResourceType.ID, REEL_SAVE_BUTTON_ID))
        enableSettings("reelDownloadButton")
    }
}

/** The route from the save component to its `Media`: component field, then model field, then `Media` field. */
private class ReelMediaPath(
    val state: FieldReference,
    val model: FieldReference,
    val media: FieldReference,
)

private class ReelContextPath(
    val holder: FieldReference,
    val context: FieldReference,
)

private fun ClassDef.fieldsOfType(type: String): List<Field> = instanceFields.filter { it.type == type }

context(patchContext: BytecodePatchContext)
private fun injectReelDownloadButton(saveButtonId: Long) {
    val render = reelSaveRender(saveButtonId)
    val componentType = render.definingClass
    val componentClass = patchContext.classDefBy(componentType)
    val composerType = render.parameterTypes.single().toString()

    val userSessionField =
        requireOne("UserSession field of $componentType", componentClass.fieldsOfType(USER_SESSION_DESCRIPTOR))
    val tintField =
        requireOne("ColorFilter field of $componentType", componentClass.fieldsOfType(COLOR_FILTER_DESCRIPTOR))
    val mediaPath = reelMediaPath(componentClass)
    val contextPath = reelContextPath(composerType)

    val builder = reelSaveBuilder(componentType)
    val builderCallSites = reelBuilderCallSites(builder, componentType)

    hookSaveRender(render, composerType, mediaPath.state.type)
    hookBuilderCallSites(builder, builderCallSites)

    bridge("media", 2, """
        check-cast p0, $componentType
        iget-object v0, p0, ${mediaPath.state}
        if-eqz v0, :none
        iget-object v0, v0, ${mediaPath.model}
        if-eqz v0, :none
        iget-object v0, v0, ${mediaPath.media}
        return-object v0
        :none
        const/4 v0, 0
        return-object v0
    """)
    bridge("session", 2, """
        check-cast p0, $componentType
        iget-object v0, p0, $userSessionField
        return-object v0
    """)
    bridge("tint", 2, """
        check-cast p0, $componentType
        iget-object v0, p0, $tintField
        return-object v0
    """)
    bridge("context", 2, """
        check-cast p0, $composerType
        iget-object v0, p0, ${contextPath.holder}
        if-eqz v0, :none
        iget-object v0, v0, ${contextPath.context}
        return-object v0
        :none
        const/4 v0, 0
        return-object v0
    """)
}

/**
 * The save button component's render method: the only one that carries the `save_button` id literal
 * together with the `save_button` tag string.
 */
context(patchContext: BytecodePatchContext)
private fun reelSaveRender(saveButtonId: Long): Method =
    requireOne(
        "reels save button render method",
        Fingerprint(filters = listOf(literal(saveButtonId)))
            .matchAll()
            .map { it.method }
            .filter { method ->
                !AccessFlags.STATIC.isSet(method.accessFlags) &&
                    method.parameterTypes.size == 1 &&
                    method.implementation?.instructions.orEmpty().any {
                        it.getReference<StringReference>()?.string == REEL_SAVE_BUTTON_ID
                    }
            },
    )

context(patchContext: BytecodePatchContext)
private fun reelMediaPath(componentClass: ClassDef): ReelMediaPath =
    requireOne(
        "Media field path from ${componentClass.type}",
        componentClass.instanceFields.flatMap { state ->
            val stateClass = patchContext.classDefByOrNull(state.type) ?: return@flatMap emptyList()
            stateClass.instanceFields.flatMap { model ->
                val modelClass = patchContext.classDefByOrNull(model.type) ?: return@flatMap emptyList()
                modelClass.fieldsOfType(MEDIA_DESCRIPTOR).map { media -> ReelMediaPath(state, model, media) }
            }
        },
        describe = { "${it.state}.${it.model}.${it.media}" },
    )

context(patchContext: BytecodePatchContext)
private fun reelContextPath(composerType: String): ReelContextPath =
    requireOne(
        "Context field path from $composerType",
        patchContext.classDefBy(composerType).instanceFields.flatMap { holder ->
            val holderClass = patchContext.classDefByOrNull(holder.type) ?: return@flatMap emptyList()
            holderClass.fieldsOfType(CONTEXT_DESCRIPTOR).map { context -> ReelContextPath(holder, context) }
        },
        describe = { "${it.holder}.${it.context}" },
    )

/** The method outside the component that returns it by constructing it. */
context(patchContext: BytecodePatchContext)
private fun reelSaveBuilder(componentType: String): Method =
    requireOne(
        "reels save component builder",
        Fingerprint(returnType = componentType)
            .matchAll()
            .map { it.method }
            .filter { method ->
                method.definingClass != componentType &&
                    method.implementation?.instructions.orEmpty().any {
                        it.opcode == Opcode.NEW_INSTANCE && it.getReference<TypeReference>()?.type == componentType
                    }
            },
    )

private class BuilderCallSite(
    val method: Method,
    val index: Int,
)

/** Every call of [builder] in its own class: the action column builds the save button in each of its layouts. */
context(patchContext: BytecodePatchContext)
private fun reelBuilderCallSites(
    builder: Method,
    componentType: String,
): List<BuilderCallSite> {
    val sites =
        patchContext.classDefBy(builder.definingClass).methods.flatMap { method ->
            val instructions = method.implementation?.instructions?.toList().orEmpty()
            instructions.indices
                .filter { index -> instructions[index].getReference<MethodReference>()?.sameSignatureAs(builder) == true }
                .map { index -> BuilderCallSite(method, index) }
        }
    if (sites.isEmpty()) throw PatchException("No call of the reels save component builder $builder")
    if (builder.returnType != componentType) throw PatchException("Builder $builder does not return $componentType")
    return sites
}

/**
 * Builds a second save component before every real one and registers it. The call is re-issued with the
 * registers it already uses, and the copy goes into the same list right before the real component.
 */
context(patchContext: BytecodePatchContext)
private fun hookBuilderCallSites(
    builder: Method,
    sites: List<BuilderCallSite>,
) {
    val caller = requireOne("caller of $builder", sites.map { it.method }.distinct())
    val instructions = caller.implementation!!.instructions.toList()
    val mutableCaller = mutable(caller)
    val countBuilder = Fingerprint(
        definingClass = builder.definingClass,
        filters = listOf(string("save_count")),
    ).matchSingle().method
    sites.sortedByDescending { it.index }.forEach { site ->
        val call = instructions[site.index]
        val result = instructions[site.index + 1]
        if (result.opcode != Opcode.MOVE_RESULT_OBJECT || result.registersUsed.size != 1) {
            throw PatchException("Builder call does not keep its result in $caller")
        }
        val resultRegister = result.registersUsed.single()
        val addIndex = site.index + 2
        val add = instructions[addIndex]
        val addReference = add.getReference<MethodReference>()
            ?: throw PatchException("Missing collector after $builder")
        val addRegisters = add.registersUsed
        if (add.opcode != Opcode.INVOKE_VIRTUAL || addRegisters.size != 2 ||
            addRegisters[1] != resultRegister || addReference.parameterTypes.size != 1 ||
            addReference.returnType != "V"
        ) throw PatchException("Unexpected collector after $builder")
        val collector = addRegisters.first()
        val countCall = instructions[addIndex + 1]
        val countResult = instructions[addIndex + 2]
        val countAdd = instructions[addIndex + 3]
        if (countCall.getReference<MethodReference>()?.sameSignatureAs(countBuilder) != true ||
            countCall.registersUsed != call.registersUsed ||
            countResult.opcode != Opcode.MOVE_RESULT_OBJECT ||
            countResult.registersUsed != listOf(resultRegister) ||
            countAdd.getReference<MethodReference>() != addReference ||
            countAdd.opcode != add.opcode || countAdd.registersUsed != addRegisters
        ) throw PatchException("Expected the save count immediately after the save icon in $caller")

        // The native save state also creates a separate count component. Hide both, keeping the download copy.
        val show = mutableCaller.reserveFreeRegisters(
            addIndex, 1, 255, mutableCaller.parameterBlock() + addRegisters,
        ).single()
        mutableCaller.addInstructionsWithLabels(
            addIndex,
            """
                const/16 v$show, 1
                invoke-static/range {v$show .. v$show}, $PREF_DESCRIPTOR->showSaveButton(Z)Z
                move-result v$show
                if-eqz v$show, :after_save_count
            """.trimIndent(),
            ExternalLabel("after_save_count", mutableCaller.getInstruction(addIndex + 4)),
        )

        val registers = mutableCaller.reserveFreeRegisters(
            site.index, 2, 15,
            mutableCaller.parameterBlock() + call.registersUsed + collector,
        )
        val copy = registers[0]
        val list = registers[1]
        mutableCaller.addInstructionsWithLabels(
            site.index,
            """
                invoke-static {}, $IS_ENABLED
                move-result v$copy
                if-eqz v$copy, :original_save
                ${invokeOriginal(call, builder)}
                move-result-object v$copy
                if-eqz v$copy, :original_save
                invoke-static {v$copy}, $REGISTER
                move-object/from16 v$list, v$collector
                invoke-virtual {v$list, v$copy}, $addReference
            """.trimIndent(),
            ExternalLabel("original_save", mutableCaller.getInstruction(site.index)),
        )
    }
}

private fun invokeOriginal(instruction: Instruction, method: MethodReference): String {
    val registers = instruction.registersUsed
    val opcode = instruction.opcode.name
    val operands = if (opcode.endsWith("/range")) {
        if (registers.zipWithNext().any { (a, b) -> b != a + 1 }) {
            throw PatchException("Nonconsecutive range invocation of $method")
        }
        "{v${registers.first()} .. v${registers.last()}}"
    } else registers.joinToString(prefix = "{", postfix = "}") { "v$it" }
    return "$opcode $operands, $method"
}

context(patchContext: BytecodePatchContext)
private fun mutable(method: Method): MutableMethod =
    requireOne("mutable method $method",
        patchContext.mutableClassDefBy(method.definingClass).methods.filter { it.sameSignatureAs(method) })

private fun MethodReference.sameSignatureAs(other: MethodReference): Boolean =
    definingClass == other.definingClass && name == other.name &&
        returnType == other.returnType &&
        parameterTypes.map { it.toString() } == other.parameterTypes.map { it.toString() }

context(patchContext: BytecodePatchContext)
private fun bridge(name: String, registers: Int, body: String) {
    val original = Fingerprint(definingClass = REEL_DOWNLOAD_DESCRIPTOR, name = name).matchSingle().method
    val replacement = ImmutableMethod(
        original.definingClass, original.name, original.parameters, original.returnType,
        original.accessFlags, original.annotations, original.hiddenApiRestrictions,
        MutableMethodImplementation(registers),
    ).toMutable().apply { addInstructions(0, body.trimIndent()) }
    patchContext.mutableClassDefBy(original.definingClass).methods.apply {
        remove(original)
        add(replacement)
    }
}

/**
 * True when the setter [reference] stores its value under the view property named [propertyName]: the
 * setter reads an enum constant, and that enum's static initializer names the constant.
 */
context(patchContext: BytecodePatchContext)
private fun setsViewProperty(
    reference: MethodReference,
    propertyName: String,
): Boolean {
    val setter =
        requireOne(
            "modifier setter $reference",
            patchContext.classDefBy(reference.definingClass).methods.filter { it.sameSignatureAs(reference) },
        )
    return setter.implementation?.instructions?.toList().orEmpty().any { instruction ->
        val field = instruction.getReference<FieldReference>()
        instruction.opcode == Opcode.SGET_OBJECT && field != null && enumConstantName(field) == propertyName
    }
}

/** The name an enum constant is created with, read from the static initializer that stores it. */
context(patchContext: BytecodePatchContext)
private fun enumConstantName(constant: FieldReference): String? {
    val enumClass = patchContext.classDefByOrNull(constant.definingClass) ?: return null
    if (!AccessFlags.ENUM.isSet(enumClass.accessFlags)) return null
    val initializer =
        requireOne(
            "static initializer of ${enumClass.type}",
            enumClass.methods.filter { it.name == "<clinit>" },
        )
    // The constant's name is the last string loaded before the store that keeps the constant.
    var lastString: String? = null
    initializer.implementation?.instructions?.toList().orEmpty().forEach { instruction ->
        instruction.getReference<StringReference>()?.let { lastString = it.string }
        val stored = instruction.getReference<FieldReference>()
        if (instruction.opcode == Opcode.SPUT_OBJECT && stored != null && stored.name == constant.name) return lastString
    }
    return null
}

/**
 * The render context identifies registered copies; ordinary save components keep their original values.
 */
context(patchContext: BytecodePatchContext)
private fun hookSaveRender(
    render: Method,
    composerType: String,
    stateType: String,
) {
    val instructions = render.implementation?.instructions?.toList().orEmpty()
    val thisRegister = mutable(render).p0Register
    val composerRegister = mutable(render).parameterRegister(composerType)

    fun staticCalls(matches: (MethodReference) -> Boolean): List<Int> =
        instructions.indices.filter { index ->
            val instruction = instructions[index]
            instruction.opcode in STATIC_INVOKE_OPCODES && instruction.getReference<MethodReference>()?.let(matches) == true
        }

    fun parameters(reference: MethodReference) = reference.parameterTypes.map { it.toString() }

    // Modifier setters take the modifier and a value and return the modifier.
    fun modifierSetter(
        reference: MethodReference,
        value: String,
    ) = parameters(reference).size == 2 &&
        parameters(reference)[1] == value &&
        reference.returnType == parameters(reference)[0]

    // Handler setters take a modifier and a Function1 and are told apart by the view property they set.
    val clickSetters =
        staticCalls { reference ->
            modifierSetter(reference, FUNCTION1_DESCRIPTOR) && setsViewProperty(reference, ON_CLICK_PROPERTY)
        }
    // The tap handler is set once on the icon's modifier chain, and once more in the layout that wraps
    // its handlers. The long press handler is set the same way.
    if (clickSetters.size != 2) {
        throw PatchException("Expected two tap handler setters in $render, found ${clickSetters.size}: $clickSetters")
    }
    val longClickSetters =
        staticCalls { reference ->
            modifierSetter(reference, FUNCTION1_DESCRIPTOR) && setsViewProperty(reference, ON_LONG_CLICK_PROPERTY)
        }
    if (longClickSetters.size != 2) {
        throw PatchException("Expected two long press handler setters in $render, found ${longClickSetters.size}: $longClickSetters")
    }
    val idSetters = staticCalls { modifierSetter(it, "I") }
    if (idSetters.size != 2) {
        throw PatchException("Expected two view id setters in $render, found ${idSetters.size}: $idSetters")
    }
    val descriptionSetter = requireOne("content description setter in $render", staticCalls { modifierSetter(it, CHAR_SEQUENCE_DESCRIPTOR) })

    val iconConstructor =
        requireOne(
            "icon constructor in $render",
            instructions.indices.filter { index ->
                val reference = instructions[index].getReference<MethodReference>() ?: return@filter false
                reference.name == "<init>" &&
                    parameters(reference).take(3) == listOf(COLOR_STATE_LIST_DESCRIPTOR, DRAWABLE_DESCRIPTOR, SCALE_TYPE_DESCRIPTOR)
            },
        )
    val savedFlagRead =
        requireOne(
            "saved flag read from $stateType in $render",
            instructions.indices.filter { index ->
                val instruction = instructions[index]
                instruction.opcode == Opcode.IGET_BOOLEAN &&
                    instruction.getReference<FieldReference>()?.definingClass == stateType
            },
        )

    val hooks = mutableListOf<Pair<Int, String>>()
    if (composerRegister != thisRegister + 1) throw PatchException("Unexpected render parameters")
    hooks += 0 to "invoke-static/range {v$thisRegister .. v$composerRegister}, $BEGIN"

    fun replace(index: Int, register: Int, reference: String, objectResult: Boolean) {
        if (register > 255) throw PatchException("Result register too high in $render")
        hooks += index to """
            invoke-static/range {v$register .. v$register}, $reference
            ${if (objectResult) "move-result-object" else "move-result"} v$register
        """.trimIndent()
    }

    replace(iconConstructor, instructions[iconConstructor].registersUsed[2], ICON, true)
    clickSetters.forEach { replace(it, instructions[it].registersUsed[1], CLICK, true) }
    longClickSetters.forEach { replace(it, instructions[it].registersUsed[1], LONG_CLICK, true) }
    idSetters.forEach { replace(it, instructions[it].registersUsed[1], VIEW_ID, false) }
    replace(descriptionSetter, instructions[descriptionSetter].registersUsed[1], DESCRIPTION, true)
    replace(savedFlagRead + 1, instructions[savedFlagRead].registersUsed[0], SELECTED, false)

    // Clear the rendering context on every normal exit, so the thread cannot retain an Activity.
    instructions.indices.filter { instructions[it].opcode == Opcode.RETURN_OBJECT }.forEach {
        hooks += it to "invoke-static {}, $REEL_DOWNLOAD_DESCRIPTOR->end()V"
    }
    val mutableRender = mutable(render)
    hooks.sortedByDescending { it.first }.forEach { (index, body) ->
        mutableRender.addInstructions(index, body)
    }
}
