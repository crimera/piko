/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.newx.misc.customfont

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.action
import app.crimera.patches.newx.settings.group
import app.crimera.patches.newx.settings.settingStrings
import app.crimera.patches.newx.settings.toggle
import app.crimera.patches.newx.settings.newXSettings
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.Constants.FONT_CLASS
import app.crimera.patches.newx.utils.Constants.FONT_UPDATE_DESCRIPTOR
import app.crimera.patches.utils.scopedMatchAll
import app.crimera.patches.utils.scopedMatchAllOrNull
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Matches the Compose paragraph intrinsics constructor (AndroidParagraphIntrinsics, obfuscated
 * to `androidx/compose/ui/text/platform/d` in 12.14.0). The constructor resolves the
 * paragraph's [android.graphics.Typeface] from the text style and applies it to the
 * paragraph TextPaint. NewX renders Compose-first, so this single constructor carries every
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

private const val CHAR_SEQUENCE_DESCRIPTOR = "Ljava/lang/CharSequence;"

private fun isComposeEmojiProcessingCall(instruction: Instruction): Boolean {
    if (instruction.opcode != Opcode.INVOKE_VIRTUAL) return false
    val reference =
        (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    return reference.definingClass.startsWith("Landroidx/emoji2/text/") &&
        reference.parameterTypes == listOf("I", "I", "I", CHAR_SEQUENCE_DESCRIPTOR) &&
        reference.returnType == CHAR_SEQUENCE_DESCRIPTOR
}

private fun isListEmptyCall(instruction: Instruction): Boolean {
    if (instruction.opcode != Opcode.INVOKE_INTERFACE) return false
    val reference =
        (instruction as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    return reference.definingClass == "Ljava/util/List;" &&
        reference.name == "isEmpty" &&
        reference.parameterTypes.isEmpty() &&
        reference.returnType == "Z"
}

@Suppress("unused")
val customFontPatch =
    bytecodePatch(
        name = "NewX: Custom font",
        description = "Customise font style",
    ) {
        compatibleWith(COMPATIBILITY_NEW_X)

        newXSettings {
            category(Categories.APPEARANCE) {
                group(
                    id = "newx.appearance.fonts",
                    strings = settingStrings("piko_newx_font"),
                    iconResourceName = "ic_vector_text_size",
                    order = 200,
                ) {
                    toggle(
                        id = "newx.content.system_font.enabled",
                        strings = settingStrings("piko_newx_system_font"),
                        order = 50,
                        defaultValue = true,
                        rebootApp = true,
                    )
                    toggle(
                        id = "newx.content.custom_font.enabled",
                        strings = settingStrings("piko_newx_custom_font"),
                        order = 100,
                        defaultValue = false,
                        rebootApp = true,
                    )
                    action(
                        id = "newx.content.custom_font.add",
                        strings = settingStrings("piko_newx_add_font"),
                        order = 110,
                        handlerClassDescriptor = "$FONT_CLASS\$AddFontAction;",
                    )
                    action(
                        id = "newx.content.custom_font.delete",
                        strings = settingStrings("piko_newx_delete_font", summary = false),
                        order = 120,
                        handlerClassDescriptor = "$FONT_CLASS\$DeleteFontAction;",
                    )
                    toggle(
                        id = "newx.content.custom_emoji_font.enabled",
                        strings = settingStrings("piko_newx_custom_emoji_font"),
                        order = 200,
                        defaultValue = false,
                        rebootApp = true,
                    )
                    action(
                        id = "newx.content.custom_emoji_font.add",
                        strings = settingStrings("piko_newx_add_emoji_font"),
                        order = 210,
                        handlerClassDescriptor = "$FONT_CLASS\$AddEmojiFontAction;",
                    )
                    action(
                        id = "newx.content.custom_emoji_font.delete",
                        strings = settingStrings("piko_newx_delete_emoji_font", summary = false),
                        order = 220,
                        handlerClassDescriptor = "$FONT_CLASS\$DeleteEmojiFontAction;",
                    )
                }
            }
        }

        execute {
            val paragraphMatches = composeParagraphTypefaceFingerprint.scopedMatchAll()
            if (paragraphMatches.size != 1) {
                throw PatchException(
                    "Expected one NewX Compose paragraph typeface anchor, found " +
                        paragraphMatches.joinToString { it.originalMethod.toString() },
                )
            }
            val paragraphMatch = paragraphMatches.single()
            val paragraphMethod = paragraphMatch.method
            if (paragraphMethod.name != "<init>" || paragraphMethod.parameterTypes.size != 6) {
                throw PatchException(
                    "NewX compose paragraph typeface anchor is not the expected intrinsics " +
                        "constructor: ${paragraphMethod}",
                )
            }
            val setTypefaceIndex = paragraphMatch.instructionMatches.last().index
            val setTypeface = paragraphMethod.instructions.getOrNull(setTypefaceIndex)
            if (setTypeface?.opcode != Opcode.INVOKE_VIRTUAL) {
                throw PatchException("NewX compose paragraph typeface anchor is not invoke-virtual")
            }
            val register = setTypeface as Instruction35c
            paragraphMethod.replaceInstruction(
                setTypefaceIndex,
                """
                invoke-static {v${register.registerC}, v${register.registerD}}, $FONT_UPDATE_DESCRIPTOR->applyTypeface(Landroid/graphics/Paint;Landroid/graphics/Typeface;)V
                """.trimIndent(),
            )

            val spanMatches = ComposeSpanTypefaceFingerprint.scopedMatchAllOrNull().orEmpty()
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

            val emojiCalls =
                paragraphMethod.instructions.withIndex().filter { (_, instruction) ->
                    isComposeEmojiProcessingCall(instruction)
                }
            if (emojiCalls.size != 1) {
                throw PatchException(
                    "Expected one Compose EmojiCompat processing call, found " +
                        emojiCalls.joinToString { "${it.index}:${it.value}" },
                )
            }
            val (emojiIndex, emojiInstruction) = emojiCalls.single()
            val emojiResultInstruction = paragraphMethod.instructions.getOrNull(emojiIndex + 1)
            if (emojiResultInstruction?.opcode != Opcode.MOVE_RESULT_OBJECT) {
                throw PatchException("Compose EmojiCompat call is not followed by move-result-object")
            }
            val emojiResultRegister =
                (emojiResultInstruction as? OneRegisterInstruction)?.registerA
                    ?: throw PatchException("Compose emoji result register is unavailable")
            val mergeCandidates =
                paragraphMethod.instructions.withIndex().filter { (index, instruction) ->
                    index in (emojiIndex + 1)..(emojiIndex + 20) && isListEmptyCall(instruction)
                }
            if (mergeCandidates.size != 2) {
                throw PatchException(
                    "Expected two list checks after the Compose emoji merge, found " +
                        mergeCandidates.joinToString { "${it.index}:${it.value}" },
                )
            }
            val mergeIndex = mergeCandidates.first().index
            val bypassCandidates =
                paragraphMethod.instructions.withIndex().filter { (index, instruction) ->
                    if (index !in (emojiIndex + 2) until mergeIndex) return@filter false
                    if (instruction.opcode != Opcode.MOVE_OBJECT) return@filter false
                    (instruction as? TwoRegisterInstruction)?.registerA == emojiResultRegister
                }
            if (bypassCandidates.size != 1) {
                throw PatchException(
                    "Expected one raw-text Compose emoji bypass, found " +
                        bypassCandidates.joinToString { "${it.index}:${it.value}" },
                )
            }
            val bypassIndex = bypassCandidates.single().index
            val processEmojiInstructions =
                """
                invoke-static {v$emojiResultRegister}, $FONT_UPDATE_DESCRIPTOR->processComposeEmoji(Ljava/lang/CharSequence;)Ljava/lang/CharSequence;
                move-result-object v$emojiResultRegister
                """.trimIndent()
            paragraphMethod.addInstructions(bypassIndex + 1, processEmojiInstructions)
            paragraphMethod.addInstructions(emojiIndex + 2, processEmojiInstructions)
        }
    }
