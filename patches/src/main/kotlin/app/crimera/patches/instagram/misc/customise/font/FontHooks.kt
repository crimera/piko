/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.customise.font

import app.crimera.patches.instagram.utils.Constants.CUSTOM_FONT_DESCRIPTOR
import app.crimera.patches.shared.declaredParameterRegister
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

internal const val TYPEFACE_CLASS = "Landroid/graphics/Typeface;"

internal const val LOAD_CUSTOM_FONT = "$CUSTOM_FONT_DESCRIPTOR->load()V"

private const val APPLY_CUSTOM_FONT =
    "$CUSTOM_FONT_DESCRIPTOR->apply($TYPEFACE_CLASS)$TYPEFACE_CLASS"

private const val APPLY_CUSTOM_FONT_FOR_DESCRIPTOR =
    "$CUSTOM_FONT_DESCRIPTOR->apply(Ljava/lang/Object;$TYPEFACE_CLASS)$TYPEFACE_CLASS"

private const val BEGIN_CONTENT_FONT_REQUEST =
    "$CUSTOM_FONT_DESCRIPTOR->beginContentFontRequest()V"

private const val END_CONTENT_FONT_REQUEST =
    "$CUSTOM_FONT_DESCRIPTOR->endContentFontRequest($TYPEFACE_CLASS)$TYPEFACE_CLASS"

private const val ASSIGN_CUSTOM_FONT =
    "$CUSTOM_FONT_DESCRIPTOR->assign($TYPEFACE_CLASS)$TYPEFACE_CLASS"

/** The platform text widgets whose direct subclasses get [overrideTypefaceAssignment]. */
internal val PLATFORM_TEXT_WIDGETS =
    listOf(
        "TextView", "EditText", "Button", "RadioButton", "CheckBox", "CheckedTextView",
        "AutoCompleteTextView", "MultiAutoCompleteTextView", "Switch", "ToggleButton",
    ).map { "Landroid/widget/$it;" }.toSet()

/**
 * Hooks the typeface repository, whose first parameter describes the font being resolved. The
 * description is what tells an interface font apart from a font the user picked inside the app.
 *
 * The descriptor is read at the returns, out of the parameter register it arrived in, rather than
 * being put on record when the method is entered - which saves a call and a thread local write on
 * every single piece of text the app draws. That only holds while nothing writes over the register
 * on the way there, so it is checked rather than assumed.
 *
 * Each return is replaced rather than preceded, since a return is often a branch target.
 */
internal fun MutableMethod.hookResolvedTypefaces() {
    val descriptorRegister = declaredParameterRegister(this, 0)
    if (isOverwritten(descriptorRegister)) {
        throw PatchException(
            "$definingClass->$name writes over the font descriptor it was passed",
        )
    }

    returnIndices().forEach { index ->
        val register = getInstruction(index).registersUsed[0]
        replaceInstruction(
            index,
            "invoke-static {v$descriptorRegister, v$register}, $APPLY_CUSTOM_FONT_FOR_DESCRIPTOR",
        )
        addInstructions(
            index + 1,
            """
            move-result-object v$register
            return-object v$register
            """.trimIndent(),
        )
    }
}

/**
 * Replaces the typeface a method wraps in the object it hands back, for a method that returns a
 * result rather than the typeface itself.
 *
 * Usually every branch of such a method meets at one point where the wrapper is built - but that
 * is not asserted anywhere the app builds it, so every constructor call for the wrapper type is
 * hooked rather than only the first: a second, non-converging construction site is then still
 * covered instead of silently keeping the app's own font.
 */
internal fun MutableMethod.hookWrappedTypeface() {
    val wrapperType = returnType
    val wrapIndices =
        instructions
            .filter {
                it.opcode == Opcode.INVOKE_DIRECT &&
                    it.getReference<MethodReference>()?.let { reference ->
                        reference.name == "<init>" && reference.definingClass == wrapperType
                    } == true
            }
            .map { it.location.index }

    if (wrapIndices.isEmpty()) {
        throw PatchException("$definingClass->$name has no $wrapperType constructor call to hook")
    }

    // Last index first, so earlier indices stay valid while instructions are inserted.
    wrapIndices.sortedDescending().forEach { wrapIndex ->
        // The instance is the first register of the constructor call, the typeface the second. The
        // insertion goes on the call itself, not on the `new-instance` before it, which is where the
        // branches land.
        val typefaceRegister = getInstruction(wrapIndex).registersUsed[1]

        addInstructions(
            wrapIndex,
            """
            invoke-static {v$typefaceRegister}, $APPLY_CUSTOM_FONT
            move-result-object v$typefaceRegister
            """.trimIndent(),
        )
    }
}

/**
 * Marks a method as resolving a font the user picked inside the app, so the typefaces it asks the
 * repository for, and the one it returns, are left as they are.
 */
internal fun MutableMethod.markAsContentFontResolver() {
    returnIndices().forEach { index ->
        val register = getInstruction(index).registersUsed[0]

        replaceInstruction(index, "invoke-static/range {v$register .. v$register}, $END_CONTENT_FONT_REQUEST")
        addInstructions(
            index + 1,
            """
            move-result-object v$register
            return-object v$register
            """.trimIndent(),
        )
    }
    addInstruction(0, "invoke-static {}, $BEGIN_CONTENT_FONT_REQUEST")
}

/**
 * Overrides `setTypeface(Typeface)`, which every way a view gets its font ends in - XML,
 * `setTextAppearance`, `setTypeface(null)` or a resolved typeface.
 */
internal fun MutableClass.overrideTypefaceAssignment() {
    val existing = methods.firstOrNull {
        it.name == "setTypeface" && it.returnType == "V" &&
            it.parameterTypes.map(CharSequence::toString) == listOf(TYPEFACE_CLASS)
    }
    if (existing != null) {
        if (existing.implementation != null) {
            existing.addInstructions(
                0,
                """
                invoke-static/range {p1 .. p1}, $ASSIGN_CUSTOM_FONT
                move-result-object p1
                """.trimIndent(),
            )
        }
        return
    }

    // Registers: v0 is this, v1 the typeface.
    val implementation = MethodImplementationBuilder(2).apply {
        addInstruction("invoke-static {v1}, $ASSIGN_CUSTOM_FONT".toInstruction())
        addInstruction("move-result-object v1".toInstruction())
        addInstruction("invoke-super {v0, v1}, $superclass->setTypeface($TYPEFACE_CLASS)V".toInstruction())
        addInstruction("return-void".toInstruction())
    }.methodImplementation
    methods.add(
        MutableMethod(
            ImmutableMethod(
                type, "setTypeface", listOf(ImmutableMethodParameter(TYPEFACE_CLASS, null, null)),
                "V", AccessFlags.PUBLIC.value, emptySet(), emptySet(), implementation,
            ),
        ),
    )
}

/** A `Paint.setTypeface` call, where spans and custom-drawn text set their font. */
internal fun Instruction.isPaintTypefaceCall() =
    (opcode == Opcode.INVOKE_VIRTUAL || opcode == Opcode.INVOKE_VIRTUAL_RANGE) &&
        getReference<MethodReference>()?.let { reference ->
            reference.name == "setTypeface" &&
                reference.definingClass in PAINT_CLASSES &&
                reference.parameterTypes.map(CharSequence::toString) == listOf(TYPEFACE_CLASS)
        } == true

private val PAINT_CLASSES = setOf("Landroid/graphics/Paint;", "Landroid/text/TextPaint;")

/** Passes the typeface of every `Paint.setTypeface` call the method makes through the custom font. */
internal fun MutableMethod.hookPaintTypefaceCalls() {
    // Last index first, so earlier indices stay valid while instructions are inserted.
    instructions
        .filter { it.isPaintTypefaceCall() }
        .map { it.location.index }
        .sortedDescending()
        .forEach { callIndex ->
            // The paint is the first register of the call, the typeface the second.
            val register = getInstruction(callIndex).registersUsed[1]
            addInstructions(
                callIndex,
                """
                invoke-static/range {v$register .. v$register}, $ASSIGN_CUSTOM_FONT
                move-result-object v$register
                """.trimIndent(),
            )
        }
}

internal val FONT_EXTENSION_PACKAGE = CUSTOM_FONT_DESCRIPTOR.removeSuffix("CustomFont;")

/** A platform class, the piko subclass that replaces it, and the constructors that subclass has. */
internal typealias Replacements = Map<String, Pair<String, Set<List<String>>>>

/** Platform spans that set their font inside the framework while drawing. */
internal val PLATFORM_TYPEFACE_SPANS: Replacements =
    mapOf(
        "Landroid/text/style/TypefaceSpan;" to
            ("${FONT_EXTENSION_PACKAGE}CustomFontTypefaceSpan;" to
                setOf(listOf("Ljava/lang/String;"), listOf(TYPEFACE_CLASS))),
        "Landroid/text/style/TextAppearanceSpan;" to
            ("${FONT_EXTENSION_PACKAGE}CustomFontTextAppearanceSpan;" to
                setOf(
                    listOf("Landroid/content/Context;", "I"),
                    listOf("Landroid/content/Context;", "I", "I"),
                    listOf(
                        "Ljava/lang/String;", "I", "I",
                        "Landroid/content/res/ColorStateList;", "Landroid/content/res/ColorStateList;",
                    ),
                )),
    )

/** The platform text widgets piko's own code creates. */
internal val PIKO_TEXT_WIDGETS: Replacements =
    listOf("TextView", "Button", "CheckBox", "EditText").associate {
        "Landroid/widget/$it;" to
            ("${FONT_EXTENSION_PACKAGE}CustomFont$it;" to
                setOf(
                    listOf("Landroid/content/Context;"),
                    listOf("Landroid/content/Context;", "Landroid/util/AttributeSet;", "I"),
                ))
    }

/** A `new-instance` of one of the [replacements]. */
internal fun Instruction.createsOneOf(replacements: Replacements) =
    opcode == Opcode.NEW_INSTANCE &&
        getReference<TypeReference>()?.type in replacements

/**
 * Creates piko's subclass wherever the method creates one of the [replacements], unless a
 * constructor call there is one the subclass lacks.
 */
internal fun MutableMethod.replaceCreations(replacements: Replacements) {
    val creations = instructions.filter { it.createsOneOf(replacements) }
    val created = creations.map { it.registersUsed[0] }.toSet()
    val constructions =
        instructions.filter { instruction ->
            (instruction.opcode == Opcode.INVOKE_DIRECT || instruction.opcode == Opcode.INVOKE_DIRECT_RANGE) &&
                instruction.getReference<MethodReference>()?.let {
                    it.name == "<init>" && it.definingClass in replacements
                } == true &&
                instruction.registersUsed[0] in created
        }
    val supported =
        constructions.all {
            val reference = it.getReference<MethodReference>()!!
            reference.parameterTypes.map(CharSequence::toString) in
                replacements.getValue(reference.definingClass).second
        }
    if (constructions.isEmpty() || !supported) return

    // Last index first, so earlier indices stay valid while instructions are replaced.
    (creations + constructions).sortedByDescending { it.location.index }.forEach { instruction ->
        val index = instruction.location.index
        val registers = instruction.registersUsed
        if (instruction.opcode == Opcode.NEW_INSTANCE) {
            val replacement = replacements.getValue(instruction.getReference<TypeReference>()!!.type).first
            replaceInstruction(index, "new-instance v${registers[0]}, $replacement")
        } else {
            val reference = instruction.getReference<MethodReference>()!!
            val replacement = replacements.getValue(reference.definingClass).first
            val constructor = "$replacement-><init>(${reference.parameterTypes.joinToString("")})V"
            replaceInstruction(
                index,
                if (instruction.opcode == Opcode.INVOKE_DIRECT_RANGE) {
                    "invoke-direct/range {v${registers.first()} .. v${registers.last()}}, $constructor"
                } else {
                    "invoke-direct {${registers.joinToString { "v$it" }}}, $constructor"
                },
            )
        }
    }
}

/**
 * Hooks React Native's "Optimistic VF App Lite" font registration, found by walking back from its
 * log string to the nearest call that turns a `Context` into a `Typeface` - the same way piko's
 * closed Force System Font patch (#1795) found it.
 */
internal fun MutableMethod.hookReactNativeFontRegistration(stringIndex: Int) {
    val searchStart = maxOf(0, stringIndex - 12)
    val factoryIndex =
        (searchStart until stringIndex).lastOrNull { index ->
            val reference = getInstruction(index).getReference<MethodReference>()
            getInstruction(index).opcode == Opcode.INVOKE_VIRTUAL &&
                reference?.returnType == TYPEFACE_CLASS &&
                reference.parameterTypes.singleOrNull()?.toString() == "Landroid/content/Context;"
        } ?: throw PatchException(
            "$definingClass->$name has no Typeface factory call to hook",
        )

    val resultIndex = factoryIndex + 1
    if (getInstruction(resultIndex).opcode != Opcode.MOVE_RESULT_OBJECT) {
        throw PatchException(
            "$definingClass->$name's Typeface factory result has an unexpected shape",
        )
    }
    val register = getInstruction(resultIndex).registersUsed[0]

    addInstructions(
        resultIndex + 1,
        """
        invoke-static {v$register}, $APPLY_CUSTOM_FONT
        move-result-object v$register
        """.trimIndent(),
    )
}

/** Return instruction indices, last one first so earlier indices stay valid while patching. */
private fun MutableMethod.returnIndices() =
    instructions
        .filter { it.opcode == Opcode.RETURN_OBJECT }
        .map { it.location.index }
        .reversed()

/**
 * Whether anything in the method writes over a register it was handed a parameter in.
 *
 * A check-cast is excluded: it re-asserts the value's type in place rather than replacing it, the
 * same exclusion this codebase already established for the identical question in
 * DisableAutoScrollPatch.kt.
 */
private fun MutableMethod.isOverwritten(register: Int) =
    instructions.any {
        it.opcode.setsRegister() && it.opcode != Opcode.CHECK_CAST &&
            it.registersUsed.firstOrNull() == register
    }
