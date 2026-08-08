/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.xlite.misc.customfont

import app.crimera.patches.xlite.settings.Categories
import app.crimera.patches.xlite.settings.action
import app.crimera.patches.xlite.settings.group
import app.crimera.patches.xlite.settings.settingStrings
import app.crimera.patches.xlite.settings.toggle
import app.crimera.patches.xlite.settings.xLiteSettings
import app.crimera.patches.xlite.utils.Constants.COMPATIBILITY_X_LITE
import app.crimera.patches.xlite.utils.Constants.FONT_CLASS
import app.crimera.patches.xlite.utils.Constants.FONT_UPDATE_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c

/**
 * Matches the Compose paragraph intrinsics constructor (AndroidParagraphIntrinsics, obfuscated
 * to `androidx/compose/ui/text/platform/d` in 12.14.0). The constructor resolves the
 * paragraph's [android.graphics.Typeface] from the text style and applies it to the
 * paragraph TextPaint. X-Lite renders Compose-first, so this single constructor carries every
 * text surface (timeline, buttons, settings). Legacy View text has its own dedicated patch
 * set; it is intentionally not hooked here.
 */
private val composeParagraphTypefaceFingerprint by lazy {
    Fingerprint(
        definingClass = "androidx/compose/ui/text/platform",
        filters =
            listOf(
                methodCall(
                    smali = "Landroid/graphics/Paint;->setTypeface(Landroid/graphics/Typeface;)Landroid/graphics/Typeface;",
                ),
            ),
    )
}

/**
 * Matches Compose's MetricAffectingSpan typeface updates. These run after paragraph
 * initialization and otherwise overwrite the custom typeface for styled text ranges.
 */
private object ComposeSpanTypefaceFingerprint : Fingerprint(
    definingClass = "androidx/compose/ui/text/android/style",
    filters =
        listOf(
            methodCall(
                smali = "Landroid/graphics/Paint;->setTypeface(Landroid/graphics/Typeface;)Landroid/graphics/Typeface;",
            ),
        ),
)

/**
 * Matches EmojiCompat's public ReplacementSpan draw contract. EmojiSpan replaces the paragraph
 * typeface immediately before drawing, so the custom emoji font must be applied at this final
 * renderer boundary instead of at Compose paragraph initialization.
 */
private object EmojiSpanDrawFingerprint : Fingerprint(
    definingClass = "androidx/emoji2/text",
    name = "draw",
    returnType = "V",
    parameters =
        listOf(
            "Landroid/graphics/Canvas;",
            "Ljava/lang/CharSequence;",
            "I",
            "I",
            "F",
            "I",
            "I",
            "I",
            "Landroid/graphics/Paint;",
        ),
    filters =
        listOf(
            methodCall(
                smali = "Landroid/graphics/Paint;->setTypeface(Landroid/graphics/Typeface;)Landroid/graphics/Typeface;",
            ),
            methodCall(
                smali = "Landroid/graphics/Canvas;->drawText([CIIFFLandroid/graphics/Paint;)V",
            ),
            methodCall(
                smali = "Landroid/graphics/Paint;->setTypeface(Landroid/graphics/Typeface;)Landroid/graphics/Typeface;",
            ),
        ),
)

@Suppress("unused")
val customFontPatch =
    bytecodePatch(
        name = "X-Lite: Custom font",
        description = "Customise font style",
    ) {
        compatibleWith(COMPATIBILITY_X_LITE)

        xLiteSettings {
            category(Categories.CONTENT) {
                group(
                    id = "xlite.content.custom_font",
                    strings = settingStrings("piko_xlite_font"),
                    order = 200,
                ) {
                    toggle(
                        id = "xlite.content.custom_font.enabled",
                        strings = settingStrings("piko_xlite_custom_font"),
                        order = 100,
                        defaultValue = false,
                        rebootApp = true,
                    )
                    action(
                        id = "xlite.content.custom_font.add",
                        strings = settingStrings("piko_xlite_add_font"),
                        order = 110,
                        handlerClassDescriptor = "$FONT_CLASS\$AddFontAction;",
                    )
                    action(
                        id = "xlite.content.custom_font.delete",
                        strings = settingStrings("piko_xlite_delete_font", summary = false),
                        order = 120,
                        handlerClassDescriptor = "$FONT_CLASS\$DeleteFontAction;",
                    )
                    toggle(
                        id = "xlite.content.custom_emoji_font.enabled",
                        strings = settingStrings("piko_xlite_custom_emoji_font"),
                        order = 200,
                        defaultValue = false,
                        rebootApp = true,
                    )
                    action(
                        id = "xlite.content.custom_emoji_font.add",
                        strings = settingStrings("piko_xlite_add_emoji_font"),
                        order = 210,
                        handlerClassDescriptor = "$FONT_CLASS\$AddEmojiFontAction;",
                    )
                    action(
                        id = "xlite.content.custom_emoji_font.delete",
                        strings = settingStrings("piko_xlite_delete_emoji_font", summary = false),
                        order = 220,
                        handlerClassDescriptor = "$FONT_CLASS\$DeleteEmojiFontAction;",
                    )
                }
            }
        }

        execute {
            val paragraphMethod = composeParagraphTypefaceFingerprint.method
            if (paragraphMethod.name != "<init>" || paragraphMethod.parameterTypes.size != 6) {
                throw PatchException(
                    "X-Lite compose paragraph typeface anchor is not the expected intrinsics " +
                        "constructor: ${paragraphMethod}",
                )
            }
            val setTypefaceIndex = composeParagraphTypefaceFingerprint.instructionMatches.last().index
            val setTypeface = paragraphMethod.instructions.getOrNull(setTypefaceIndex)
            if (setTypeface?.opcode != Opcode.INVOKE_VIRTUAL) {
                throw PatchException("X-Lite compose paragraph typeface anchor is not invoke-virtual")
            }
            val register = setTypeface as Instruction35c
            paragraphMethod.replaceInstruction(
                setTypefaceIndex,
                """
                invoke-static {v${register.registerC}, v${register.registerD}}, $FONT_UPDATE_DESCRIPTOR->applyTypeface(Landroid/graphics/Paint;Landroid/graphics/Typeface;)V
                """.trimIndent(),
            )

            val spanMatches = ComposeSpanTypefaceFingerprint.matchAllOrNull().orEmpty()
            val spanMethodNames = spanMatches.map { it.method.name }.toSet()
            if (spanMatches.size != 2 || spanMethodNames != setOf("updateDrawState", "updateMeasureState")) {
                throw PatchException(
                    "Expected Compose typeface span draw/measure anchors, found " +
                        spanMatches.joinToString { it.method.toString() },
                )
            }
            spanMatches.forEach { match ->
                val spanMethod = match.method
                val spanTypefaceIndex = match.instructionMatches.single().index
                val spanTypeface = spanMethod.instructions.getOrNull(spanTypefaceIndex)
                if (spanTypeface?.opcode != Opcode.INVOKE_VIRTUAL) {
                    throw PatchException("Compose typeface span anchor is not invoke-virtual")
                }
                val spanRegister = spanTypeface as Instruction35c
                spanMethod.replaceInstruction(
                    spanTypefaceIndex,
                    """
                    invoke-static {v${spanRegister.registerC}, v${spanRegister.registerD}}, $FONT_UPDATE_DESCRIPTOR->applyTypeface(Landroid/graphics/Paint;Landroid/graphics/Typeface;)V
                    """.trimIndent(),
                )
            }

            val emojiSpanMatches = EmojiSpanDrawFingerprint.matchAllOrNull().orEmpty()
            if (emojiSpanMatches.size != 1) {
                throw PatchException(
                    "Expected one EmojiSpan draw renderer, found " +
                        emojiSpanMatches.joinToString { it.method.toString() },
                )
            }
            val emojiSpanMatch = emojiSpanMatches.single()
            if (emojiSpanMatch.instructionMatches.size != 3) {
                throw PatchException(
                    "Expected three EmojiSpan typeface/draw anchors, found " +
                        emojiSpanMatch.instructionMatches.size,
                )
            }
            val emojiTypefaceIndex = emojiSpanMatch.instructionMatches.first().index
            val emojiTypeface = emojiSpanMatch.method.instructions.getOrNull(emojiTypefaceIndex)
            if (emojiTypeface?.opcode != Opcode.INVOKE_VIRTUAL) {
                throw PatchException("EmojiSpan typeface anchor is not invoke-virtual")
            }
            val emojiRegister = emojiTypeface as Instruction35c
            emojiSpanMatch.method.replaceInstruction(
                emojiTypefaceIndex,
                """
                invoke-static {v${emojiRegister.registerC}, v${emojiRegister.registerD}}, $FONT_UPDATE_DESCRIPTOR->applyEmojiTypeface(Landroid/graphics/Paint;Landroid/graphics/Typeface;)V
                """.trimIndent(),
            )
        }
    }