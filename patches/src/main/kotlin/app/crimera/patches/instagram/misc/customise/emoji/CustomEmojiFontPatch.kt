/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.customise.emoji

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.classDefForEach
import app.morphe.patcher.patch.mutableClassDefBy
import app.morphe.patcher.patch.patch
import app.morphe.patcher.string
import app.morphe.patcher.util.getReference
import app.morphe.patcher.util.proxy.mutableTypes.MutableClass
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.util.findInstructionIndicesReversed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter

private const val EMOJI_FONT = "$PATCHES_DESCRIPTOR/customise/emoji/EmojiFont;"
private const val STATIC_LAYOUT_BUILDER = "Landroid/text/StaticLayout\$Builder;"
private const val OBTAIN_SIGNATURE =
    "(Ljava/lang/CharSequence;IILandroid/text/TextPaint;I)$STATIC_LAYOUT_BUILDER"

/** androidx EmojiCompat.process(CharSequence, int, int, int, int): the one every emoji2 caller ends in. */
private object EmojiCompatProcessFingerprint : Fingerprint(
    returnType = "Ljava/lang/CharSequence;",
    filters = listOf(string("end should be < than charSequence length")),
    custom = { method, _ ->
        !AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.parameterTypes.firstOrNull()?.toString() == "Ljava/lang/CharSequence;"
    },
)

/**
 * The emoji image infra (X.09bz in 452): A01(url, prefix) builds the disk cache key of every emoji
 * bitmap ("emoji_system_<hash>"), single emoji and sprite sheets alike.
 */
private object EmojiImageCacheKeyFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
    filters = listOf(string("fbnotopart_")),
)

/** Every app method that lays text out itself through StaticLayout.Builder.obtain. */
private object StaticLayoutObtainFingerprint : Fingerprint(
    filters = listOf(
        methodCall(definingClass = STATIC_LAYOUT_BUILDER, name = "obtain"),
    ),
    custom = { _, classDef -> !classDef.type.startsWith("Lapp/morphe/") },
)

private fun Instruction.isObtainCall() =
    (opcode == Opcode.INVOKE_STATIC || opcode == Opcode.INVOKE_STATIC_RANGE) &&
        getReference<MethodReference>()?.let {
            it.definingClass == STATIC_LAYOUT_BUILDER && it.name == "obtain"
        } == true

private val TEXT_VIEWS = setOf(
    "Landroid/widget/TextView;",
    "Landroid/widget/Button;",
    "Landroid/widget/CheckedTextView;",
)
private val EDIT_TEXTS = setOf(
    "Landroid/widget/EditText;",
    "Landroid/widget/AutoCompleteTextView;",
    "Landroid/widget/MultiAutoCompleteTextView;",
)
private const val CHAR_SEQUENCE = "Ljava/lang/CharSequence;"
private const val TYPEFACE = "Landroid/graphics/Typeface;"
private const val BUFFER_TYPE = "Landroid/widget/TextView\$BufferType;"

private fun MutableClass.findMethod(name: String, parameters: List<String>) =
    methods.firstOrNull { method ->
        method.name == name && method.returnType == "V" &&
            method.parameterTypes.map(CharSequence::toString) == parameters
    }

private fun MutableClass.addMethod(name: String, parameters: List<String>, accessFlags: Int, registers: Int,
                                   body: MethodImplementationBuilder.() -> Unit) {
    val implementation = MethodImplementationBuilder(registers).apply(body).methodImplementation
    methods.add(
        MutableMethod(
            ImmutableMethod(
                type, name, parameters.map { ImmutableMethodParameter(it, null, null) },
                "V", accessFlags, emptySet(), emptySet(), implementation,
            ),
        ),
    )
}

/**
 * Instagram's own text views (IgTextView, IgEditText...) extend the platform classes, not AppCompat,
 * so EmojiCompat never sees their text. Every text they are given passes through setText.
 */
private fun MutableClass.hookSetText() {
    val parameters = listOf(CHAR_SEQUENCE, BUFFER_TYPE)
    val existing = findMethod("setText", parameters)
    if (existing != null) {
        existing.implementation ?: return
        existing.addInstructions(
            0,
            """
            invoke-static/range {p1 .. p1}, $EMOJI_FONT->process($CHAR_SEQUENCE)$CHAR_SEQUENCE
            move-result-object p1
            """.trimIndent(),
        )
        return
    }
    // Registers: v0 this, v1 text, v2 buffer type.
    addMethod("setText", parameters, AccessFlags.PUBLIC.value, 3) {
        addInstruction("invoke-static {v1}, $EMOJI_FONT->process($CHAR_SEQUENCE)$CHAR_SEQUENCE".toInstruction())
        addInstruction("move-result-object v1".toInstruction())
        addInstruction("invoke-super {v0, v1, v2}, $superclass->setText($CHAR_SEQUENCE$BUFFER_TYPE)V".toInstruction())
        addInstruction("return-void".toInstruction())
    }
}

/** Typing never calls setText, so text fields also re-span their text on every change. */
private fun MutableClass.hookTextChanges() {
    val parameters = listOf(CHAR_SEQUENCE, "I", "I", "I")
    val existing = findMethod("onTextChanged", parameters)
    if (existing != null) {
        existing.implementation ?: return
        existing.addInstructions(0, "invoke-static/range {p1 .. p1}, $EMOJI_FONT->onTextChanged($CHAR_SEQUENCE)V")
        return
    }
    // Registers: v0 this, v1 text, v2-v4 the ints.
    addMethod("onTextChanged", parameters, AccessFlags.PROTECTED.value, 5) {
        addInstruction("invoke-static {v1}, $EMOJI_FONT->onTextChanged($CHAR_SEQUENCE)V".toInstruction())
        addInstruction("invoke-super {v0, v1, v2, v3, v4}, $superclass->onTextChanged(${CHAR_SEQUENCE}III)V".toInstruction())
        addInstruction("return-void".toInstruction())
    }
}

@Suppress("unused")
val customEmojiFontPatch =
    patch(
        name = "Custom emoji font",
        description = "Adds an option to download an emoji font (Noto, Twemoji, Fluent) and draw emoji in it.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)

        execute {
            // Text views, text fields and Compose text: EmojiCompat processes them all. The emoji
            // runs get a span with the downloaded font before EmojiCompat looks at the text.
            EmojiCompatProcessFingerprint.method.addInstructions(
                0,
                """
                invoke-static/range {p1 .. p1}, $EMOJI_FONT->process(Ljava/lang/CharSequence;)Ljava/lang/CharSequence;
                move-result-object p1
                """.trimIndent(),
            )

            // Custom text views (captions, comments) lay their text out with StaticLayout. Each
            // obtain call is pointed at a wrapper with the same signature, so no register changes.
            val methods = StaticLayoutObtainFingerprint.matchAll()
            if (methods.isEmpty()) throw PatchException("No StaticLayout.Builder.obtain calls found")
            methods.forEach { match ->
                match.method.apply {
                    findInstructionIndicesReversed { isObtainCall() }.forEach { index ->
                        val registers = instructions.elementAt(index).registersUsed
                        val call = if (instructions.elementAt(index).opcode == Opcode.INVOKE_STATIC_RANGE) {
                            "invoke-static/range {v${registers.first()} .. v${registers.last()}}"
                        } else {
                            "invoke-static {${registers.joinToString { "v$it" }}}"
                        }
                        replaceInstruction(index, "$call, $EMOJI_FONT->obtain$OBTAIN_SIGNATURE")
                    }
                }
            }

            // Emoji drawn as images (reactions, the reaction picker, the emoji keyboard) go through
            // the emoji infra: its key builder names the cached bitmaps, so the font goes into the
            // key, and both bitmap renderers ask its provider for a typeface (null for the system
            // font). The providers are the classes its factory method instantiates.
            EmojiImageCacheKeyFingerprint.method.apply {
                findInstructionIndicesReversed { opcode == Opcode.RETURN_OBJECT }.forEach { index ->
                    val register = (instructions.elementAt(index) as OneRegisterInstruction).registerA
                    addInstructions(
                        index,
                        """
                        invoke-static/range {v$register .. v$register}, $EMOJI_FONT->emojiImageCacheKey(Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v$register
                        """.trimIndent(),
                    )
                }
            }
            val infra = EmojiImageCacheKeyFingerprint.classDef
            val providerTypes = infra.methods
                .filter { AccessFlags.STATIC.isSet(it.accessFlags) && it.parameterTypes.isEmpty() && it.returnType.startsWith("L") }
                .flatMap { method ->
                    method.implementation?.instructions.orEmpty()
                        .filter { it.opcode == Opcode.NEW_INSTANCE }
                        .mapNotNull { (it as ReferenceInstruction).reference.toString() }
                }
                .distinct()
            var providers = 0
            providerTypes.forEach { type ->
                val provider = mutableClassDefBy(type)
                provider.methods
                    .filter { it.returnType == TYPEFACE && it.parameterTypes.map(CharSequence::toString) == listOf("Ljava/lang/String;") }
                    .forEach { method ->
                        method.findInstructionIndicesReversed { opcode == Opcode.RETURN_OBJECT }.forEach { index ->
                            val register = (method.instructions.elementAt(index) as OneRegisterInstruction).registerA
                            method.addInstructions(
                                index,
                                """
                                invoke-static/range {v$register .. v$register}, $EMOJI_FONT->emojiImageTypeface($TYPEFACE)$TYPEFACE
                                move-result-object v$register
                                """.trimIndent(),
                            )
                        }
                        providers++
                    }
            }
            if (providers == 0) throw PatchException("No emoji typeface providers found in ${infra.type}")

            // The app's own TextView and EditText subclasses.
            val textViews = mutableListOf<ClassDef>()
            classDefForEach { classDef ->
                if ((classDef.superclass in TEXT_VIEWS || classDef.superclass in EDIT_TEXTS) &&
                    !classDef.type.startsWith("Lapp/morphe/")
                ) {
                    textViews += classDef
                }
            }
            if (textViews.isEmpty()) throw PatchException("No TextView subclasses found")
            textViews.forEach { classDef ->
                mutableClassDefBy(classDef).apply {
                    hookSetText()
                    if (superclass in EDIT_TEXTS) hookTextChanges()
                }
            }

            enableSettings("customEmojiFont")
        }
    }
