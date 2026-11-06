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
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
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
private const val TWEET = "Lcom/twitter/model/core/e;"
private const val QUOTE = "Lcom/twitter/tweetview/core/QuoteView;"
private const val BODY = "Lcom/twitter/ui/widget/TextContentView;"
private const val TEXT = "Ljava/lang/CharSequence;"
private const val TEXT_VIEW = "Landroid/widget/TextView;"
private const val BUFFER = "Landroid/widget/TextView\$BufferType;"

private fun MutableMethod.assertNotHooked() {
    if (instructions.any { ((it as? ReferenceInstruction)?.reference as? MethodReference)?.definingClass == HELPER }) {
        throw PatchException("$definingClass->$name already contains automatic translation hooks")
    }
}

private fun registers(vararg values: Int): String {
    if (values.any { it !in 0..15 }) throw PatchException("hook registers no longer fit invoke-static/35c")
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
    val index = matches.singleOrNull() ?: throw PatchException("missing or ambiguous final setter in $definingClass->${this.name}")
    val instruction = instructions[index] as Instruction35c
    val args = if (parameters.size == 1) registers(instruction.registerC, instruction.registerD)
        else registers(instruction.registerC, instruction.registerD, instruction.registerE)
    replaceInstruction(index, "invoke-static {$args}, $HELPER->$helperName($owner${parameters.joinToString("")})V")
}

/** Internal rendering hooks included by the Native translator patch. */
val googleAutoTranslatePatch = bytecodePatch {
    compatibleWith(COMPATIBILITY_X)
    dependsOn(settingsPatch)

    execute {
        val capture = CapturePostFingerprint.method
        val body = TimelineTextFingerprint.method
        val focal = FocalTextFingerprint.method
        val attached = FocalAttachmentFingerprint.method
        val quote = QuotedPostFingerprint.method
        listOf(capture, body, focal, attached, quote).forEach { it.assertNotHooked() }

        capture.apply {
            val index = instructions.indices.singleOrNull { index ->
                val instruction = instructions[index]
                val field = (instruction as? ReferenceInstruction)?.reference as? FieldReference
                instruction.opcode == Opcode.IGET_OBJECT && field?.definingClass == definingClass && field.type == "Landroid/view/View;"
            } ?: throw PatchException("missing or ambiguous formatter view read")
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
            } ?: throw PatchException("missing or ambiguous empty focal visibility path")
            val view = (instructions[index] as Instruction35c).registerC
            addInstructions(index, "invoke-static {${registers(view)}}, $HELPER->clearFocal(Landroid/view/View;)V")
        }

        quote.apply {
            val index = instructions.indices.singleOrNull { index ->
                val field = (instructions[index] as? ReferenceInstruction)?.reference as? FieldReference
                instructions[index].opcode == Opcode.IPUT_BOOLEAN && field?.definingClass == QUOTE && field.name == "Q" && field.type == "Z"
            } ?: throw PatchException("missing or ambiguous completed quote layout flag")
            val view = (instructions[index] as TwoRegisterInstruction).registerB
            addInstructions(index + 1, "invoke-static {${registers(view)}}, $HELPER->bindQuote($QUOTE)V")
        }

        mutableClassDefBy(QUOTE).apply {
            if (methods.any { it.name == "onTouchEvent" && it.parameterTypes.map(CharSequence::toString) == listOf("Landroid/view/MotionEvent;") }) {
                throw PatchException("QuoteView already handles onTouchEvent; refusing to overwrite it")
            }
            val nativeSuper = superclass ?: throw PatchException("QuoteView has no superclass")
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
    }
}
