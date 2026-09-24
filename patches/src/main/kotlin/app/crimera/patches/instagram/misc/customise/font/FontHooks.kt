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
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

internal const val TYPEFACE_CLASS = "Landroid/graphics/Typeface;"

internal const val LOAD_CUSTOM_FONT = "$CUSTOM_FONT_DESCRIPTOR->load()V"

private const val APPLY_CUSTOM_FONT =
    "$CUSTOM_FONT_DESCRIPTOR->apply($TYPEFACE_CLASS)$TYPEFACE_CLASS"

private const val APPLY_CUSTOM_FONT_FOR_DESCRIPTOR =
    "$CUSTOM_FONT_DESCRIPTOR->apply(Ljava/lang/Object;$TYPEFACE_CLASS)$TYPEFACE_CLASS"

private const val BEGIN_CONTENT_FONT_REQUEST =
    "$CUSTOM_FONT_DESCRIPTOR->beginContentFontRequest()V"

private const val END_CONTENT_FONT_REQUEST =
    "$CUSTOM_FONT_DESCRIPTOR->endContentFontRequest()V"

/**
 * Replaces every typeface a method hands back with the custom font.
 *
 * The return instruction itself is replaced rather than instructions being inserted before it,
 * because a return is often a branch target: instructions inserted in front of it are skipped by
 * every branch that jumps straight to the return.
 *
 * @param hook the extension method to hand the typeface to.
 * @param leadingArgument a register passed ahead of the typeface, empty for none.
 */
internal fun MutableMethod.hookReturnedTypefaces(
    hook: String = APPLY_CUSTOM_FONT,
    leadingArgument: String = "",
) {
    returnIndices().forEach { index ->
        val register = getInstruction(index).registersUsed[0]
        val arguments =
            if (leadingArgument.isEmpty()) "v$register" else "$leadingArgument, v$register"

        replaceInstruction(index, "invoke-static {$arguments}, $hook")
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
 * Hooks the typeface repository, whose first parameter describes the font being resolved. The
 * description is what tells an interface font apart from a font the user picked inside the app.
 *
 * The descriptor is read at the returns, out of the parameter register it arrived in, rather than
 * being put on record when the method is entered - which saves a call and a thread local write on
 * every single piece of text the app draws. That only holds while nothing writes over the register
 * on the way there, so it is checked rather than assumed.
 */
internal fun MutableMethod.hookResolvedTypefaces() {
    val descriptorRegister = declaredParameterRegister(this, 0)
    if (isOverwritten(descriptorRegister)) {
        throw PatchException(
            "$definingClass->$name writes over the font descriptor it was passed",
        )
    }

    hookReturnedTypefaces(APPLY_CUSTOM_FONT_FOR_DESCRIPTOR, "v$descriptorRegister")
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
 * repository for are left as they are.
 */
internal fun MutableMethod.markAsContentFontResolver() {
    returnIndices().forEach { index ->
        val register = getInstruction(index).registersUsed[0]

        replaceInstruction(index, "invoke-static {}, $END_CONTENT_FONT_REQUEST")
        addInstructions(index + 1, "return-object v$register")
    }
    addInstruction(0, "invoke-static {}, $BEGIN_CONTENT_FONT_REQUEST")
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
