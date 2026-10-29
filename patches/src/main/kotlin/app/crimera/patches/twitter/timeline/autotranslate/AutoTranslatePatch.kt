/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.timeline.autotranslate

import app.crimera.patches.shared.declaredParameterRegister
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod.Companion.toMutable
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val HELPER = "$PATCHES_DESCRIPTOR/autotranslate/AutoTranslate;"
private const val CORE = "Lcom/twitter/tweetview/core/ui/textcontent/"
private const val FOCAL = "Lcom/twitter/tweetview/focal/ui/textcontent/"
private const val TWEET = "Lcom/twitter/model/core/e;"
private const val QUOTE = "Lcom/twitter/tweetview/core/QuoteView;"
private const val BODY = "Lcom/twitter/ui/widget/TextContentView;"
private const val TEXT = "Ljava/lang/CharSequence;"
private const val TEXT_VIEW = "Landroid/widget/TextView;"
private const val BUFFER = "Landroid/widget/TextView\$BufferType;"

private fun unexpected(message: String): Nothing =
    throw PatchException("Google auto translate: $message (expected an unmodified X 12.19.1 rendering path)")

context(context: BytecodePatchContext)
private fun exactMethod(owner: String, name: String, parameters: List<String>, result: String = "V"): MutableMethod =
    context.mutableClassDefBy(owner).methods.singleOrNull {
        it.name == name && it.parameterTypes.map(CharSequence::toString) == parameters && it.returnType == result
    } ?: unexpected("missing or ambiguous $owner->$name")

private fun MutableMethod.assertNotHooked() {
    if (instructions.any { ((it as? ReferenceInstruction)?.reference as? MethodReference)?.definingClass == HELPER }) {
        unexpected("$definingClass->$name already contains automatic translation hooks")
    }
}

private fun registers(vararg values: Int): String {
    if (values.any { it !in 0..15 }) unexpected("hook registers no longer fit invoke-static/35c")
    return values.joinToString(", ") { "v$it" }
}

private fun MutableMethod.replaceSetter(
    owner: String,
    name: String,
    parameters: List<String>,
    helperName: String,
    textRegister: Int? = null,
) {
    val matches = instructions.indices.filter { index ->
        val instruction = instructions[index] as? Instruction35c ?: return@filter false
        val reference = instruction.reference as? MethodReference ?: return@filter false
        instruction.opcode == Opcode.INVOKE_VIRTUAL && reference.definingClass == owner &&
            reference.name == name && reference.parameterTypes.map(CharSequence::toString) == parameters &&
            reference.returnType == "V" && (textRegister == null || instruction.registerD == textRegister)
    }
    val index = matches.singleOrNull() ?: unexpected("missing or ambiguous final setter in $definingClass->${this.name}")
    val instruction = instructions[index] as Instruction35c
    val args = if (parameters.size == 1) registers(instruction.registerC, instruction.registerD)
        else registers(instruction.registerC, instruction.registerD, instruction.registerE)
    replaceInstruction(index, "invoke-static {$args}, $HELPER->$helperName($owner${parameters.joinToString("")})V")
}

/** Version-specific descriptors are intentional: the Legacy X target is frozen at 12.19.1. */
@Suppress("unused")
val googleAutoTranslatePatch = bytecodePatch(
    name = "Google auto translate (Italian)",
    description = "Automatically translates visible timeline, quoted and opened posts into Italian through Google's web service. Sends post text to Google.",
    default = false,
) {
    compatibleWith(COMPATIBILITY_X)
    dependsOn(settingsPatch)

    execute {
        val capture = exactMethod("${CORE}o;", "g", listOf(
            "Landroid/content/Context;", TWEET, "Lcom/twitter/model/core/entity/g1;",
            "Lcom/twitter/ui/view/m;", "${CORE}b;", "Z", "Z",
        ))
        val body = exactMethod("${CORE}d;", "f", listOf(TEXT))
        val focal = exactMethod("${FOCAL}f;", "f", listOf(TEXT))
        val attached = exactMethod("${FOCAL}f\$b;", "onViewAttachedToWindow", listOf("Landroid/view/View;"))
        val quote = exactMethod(QUOTE, "l", listOf(TWEET, "Lcom/twitter/ui/renderable/i;", "Ljava/lang/Integer;"))
        listOf(capture, body, focal, attached, quote).forEach { it.assertNotHooked() }

        capture.apply {
            val index = instructions.indices.singleOrNull { index ->
                val instruction = instructions[index]
                val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                instruction.opcode == Opcode.IGET_OBJECT && field?.definingClass == "${CORE}o;" &&
                    field.name == "a" && field.type == "Landroid/view/View;"
            } ?: unexpected("missing or ambiguous formatter view read")
            val view = (instructions[index] as TwoRegisterInstruction).registerA
            val args = registers(view, declaredParameterRegister(this, 1), declaredParameterRegister(this, 6))
            addInstructions(index + 1, "invoke-static {$args}, $HELPER->capture(Landroid/view/View;${TWEET}Z)V")
        }

        body.replaceSetter(BODY, "setTextWithVisibility", listOf(TEXT), "bind")

        // Preserve the intermediate null-text reset and native selection/movement setup.
        focal.replaceSetter(TEXT_VIEW, "setText", listOf(TEXT, BUFFER), "bindFocal", declaredParameterRegister(focal, 0))
        attached.replaceSetter(TEXT_VIEW, "setText", listOf(TEXT, BUFFER), "bindFocal")
        focal.apply {
            val index = instructions.indices.singleOrNull { index ->
                val reference = (instructions[index] as? ReferenceInstruction)?.reference as? MethodReference
                instructions[index].opcode == Opcode.INVOKE_VIRTUAL && reference?.definingClass == "Landroid/view/View;" &&
                    reference.name == "setVisibility" && reference.parameterTypes.map(CharSequence::toString) == listOf("I")
            } ?: unexpected("missing or ambiguous empty focal visibility path")
            val view = (instructions[index] as Instruction35c).registerC
            addInstructions(index, "invoke-static {${registers(view)}}, $HELPER->clearFocal(Landroid/view/View;)V")
        }

        quote.apply {
            val index = instructions.indices.singleOrNull { index ->
                val field = (instructions[index] as? ReferenceInstruction)?.reference as? FieldReference
                instructions[index].opcode == Opcode.IPUT_BOOLEAN && field?.definingClass == QUOTE && field.name == "Q" && field.type == "Z"
            } ?: unexpected("missing or ambiguous completed quote layout flag")
            val view = (instructions[index] as TwoRegisterInstruction).registerB
            addInstructions(index + 1, "invoke-static {${registers(view)}}, $HELPER->bindQuote($QUOTE)V")
        }

        mutableClassDefBy(QUOTE).apply {
            if (methods.any { it.name == "onTouchEvent" && it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/view/MotionEvent;") }) {
                unexpected("QuoteView already handles onTouchEvent; refusing to overwrite it")
            }
            val nativeSuper = superclass ?: unexpected("QuoteView has no superclass")
            methods.add(ImmutableMethod(type, "onTouchEvent",
                listOf(ImmutableMethodParameter("Landroid/view/MotionEvent;", null, null)), "Z",
                AccessFlags.PUBLIC.value, null, null, MutableMethodImplementation(3),
            ).toMutable().apply {
                addInstructions(0, """
                    invoke-static {p0, p1}, $HELPER->quoteTouch(${QUOTE}Landroid/view/MotionEvent;)Z
                    move-result v0
                    if-eqz v0, :native_touch
                    const/4 v0, 0x1
                    return v0
                    :native_touch
                    invoke-super {p0, p1}, $nativeSuper->onTouchEvent(Landroid/view/MotionEvent;)Z
                    move-result v0
                    return v0
                """.trimIndent())
            })
        }
        enableSettings("autoTranslate")
    }
}
