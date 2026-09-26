/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.notes

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.literal
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference


private object NotesCreationBubbleViewClassFingerprint : Fingerprint(
    strings = listOf("noteContentText", "songTitleText"),
)

private object NotesCreationBubbleViewApplyColorsFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("LX/0Rd3;", "Ljava/lang/Integer;", "Ljava/lang/Integer;", "Ljava/lang/Integer;"),
    returnType = "V",
    classFingerprint = NotesCreationBubbleViewClassFingerprint,
)

private object ApplyNoteThemeFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    returnType = "V",
    strings = listOf("has_seen_all_followers_share_dialog", "notes_last_created_timestamp_ms"),
)

private const val NOTE_CUSTOM_THEME_CLASS =
    "Lcom/instagram/direct/inbox/notes/models/domain/NoteCustomTheme;"

private const val NOTE_BUBBLE_VIEW_CLASS =
    "Lcom/instagram/direct/inbox/notes/ui/NoteBubbleView;"

private object NoteBubbleViewSpotifyContentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/CharSequence;", "Ljava/lang/String;", "Ljava/lang/String;", "Z", "Z", "Z"),
    returnType = "V",
    custom = { method, classDef ->
        classDef.type == NOTE_BUBBLE_VIEW_CLASS && method.name == "A0M"
    },
)

private object NoteBubbleViewGameContentFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Ljava/lang/String;", "Ljava/lang/CharSequence;"),
    returnType = "V",
    custom = { method, classDef ->
        classDef.type == NOTE_BUBBLE_VIEW_CLASS && method.name == "setGameContent"
    },
)

// Hook C: long-press on the theme/palette button. Anchored on its layout resource id literal (0x7f0b40b0 / themes_edit_button_view) — more stable across IG version bumps than an opcode-sequence match on a large onCreateView().

private object NotesCreationThemeButtonFingerprint : Fingerprint(
    returnType = "Landroid/view/View;",
    filters = listOf(literal(0x7f0b40b0L)),
)

private const val EXTENSION_CLASS_DESCRIPTOR =
    "$PATCHES_DESCRIPTOR/notes/NotesCustomColorPatch;"


private fun invokeStaticOneArg(register: Int, methodDescriptor: String): String =
    if (register < 16) {
        "invoke-static {v$register}, $methodDescriptor"
    } else {
        "invoke-static/range {v$register .. v$register}, $methodDescriptor"
    }

@Suppress("unused")
val notesCustomTextColorPatch =
    bytecodePatch(
        name = "Custom note text color",
        description = "Choose a custom color for the text in the notes.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        dependsOn(settingsPatch)

        execute {

            NotesCreationBubbleViewApplyColorsFingerprint.method.addInstructionsAtControlFlowLabel(
                0,
                """
                    invoke-static {p3}, $EXTENSION_CLASS_DESCRIPTOR->overrideTextColorArgb(Ljava/lang/Integer;)Ljava/lang/Integer;
                    move-result-object p3
                    invoke-static {p4}, $EXTENSION_CLASS_DESCRIPTOR->overrideTextColorArgb(Ljava/lang/Integer;)Ljava/lang/Integer;
                    move-result-object p4
                """,
            )

            ApplyNoteThemeFingerprint.method.apply {
                val constructorIndex =
                    indexOfFirstInstructionOrThrow {
                        opcode == Opcode.INVOKE_DIRECT_RANGE &&
                            getReference<MethodReference>()?.definingClass == NOTE_CUSTOM_THEME_CLASS
                    }
                val range = getInstruction<RegisterRangeInstruction>(constructorIndex)
                val primaryColorRegister = range.startRegister + 10
                val secondaryColorRegister = range.startRegister + 8

                addInstructionsAtControlFlowLabel(
                    constructorIndex,
                    """
                        ${invokeStaticOneArg(primaryColorRegister, "$EXTENSION_CLASS_DESCRIPTOR->overrideTextColorHex(Ljava/lang/String;)Ljava/lang/String;")}
                        move-result-object v$primaryColorRegister
                        ${invokeStaticOneArg(secondaryColorRegister, "$EXTENSION_CLASS_DESCRIPTOR->overrideTextColorHex(Ljava/lang/String;)Ljava/lang/String;")}
                        move-result-object v$secondaryColorRegister
                    """,
                )
            }
            
            NoteBubbleViewSpotifyContentFingerprint.method.apply {
                val lengthCallIndex =
                    indexOfFirstInstructionOrThrow {
                        opcode == Opcode.INVOKE_INTERFACE &&
                            getReference<MethodReference>()?.let {
                                it.name == "length" && it.definingClass == "Ljava/lang/CharSequence;"
                            } == true
                    }
                addInstructionsAtControlFlowLabel(
                    lengthCallIndex,
                    """
                        iget-object v0, p0, $NOTE_BUBBLE_VIEW_CLASS->A0Y:Ljava/lang/Integer;
                        iget-object v1, p0, $NOTE_BUBBLE_VIEW_CLASS->A0N:Lcom/instagram/common/ui/base/IgTextView;
                        invoke-static {v0, v1}, $EXTENSION_CLASS_DESCRIPTOR->applyBubbleTextColor(Ljava/lang/Integer;Landroid/widget/TextView;)V
                        iget-object v0, p0, $NOTE_BUBBLE_VIEW_CLASS->A0Y:Ljava/lang/Integer;
                        iget-object v1, p0, $NOTE_BUBBLE_VIEW_CLASS->A0L:Lcom/instagram/common/ui/base/IgTextView;
                        invoke-static {v0, v1}, $EXTENSION_CLASS_DESCRIPTOR->applyBubbleTextColor(Ljava/lang/Integer;Landroid/widget/TextView;)V
                    """,
                )
            }
         
            NoteBubbleViewGameContentFingerprint.method.apply {
                val getContextIndex =
                    indexOfFirstInstructionOrThrow {
                        opcode == Opcode.INVOKE_VIRTUAL &&
                            getReference<MethodReference>()?.let {
                                it.name == "getContext" && it.definingClass == "Landroid/view/View;"
                            } == true
                    }
                addInstructionsAtControlFlowLabel(
                    getContextIndex,
                    """
                        iget-object v0, p0, $NOTE_BUBBLE_VIEW_CLASS->A0Y:Ljava/lang/Integer;
                        iget-object v1, p0, $NOTE_BUBBLE_VIEW_CLASS->A0I:Lcom/instagram/common/ui/base/IgTextView;
                        invoke-static {v0, v1}, $EXTENSION_CLASS_DESCRIPTOR->applyBubbleTextColor(Ljava/lang/Integer;Landroid/widget/TextView;)V
                        iget-object v0, p0, $NOTE_BUBBLE_VIEW_CLASS->A0Y:Ljava/lang/Integer;
                        iget-object v1, p0, $NOTE_BUBBLE_VIEW_CLASS->A0H:Lcom/instagram/common/ui/base/IgTextView;
                        invoke-static {v0, v1}, $EXTENSION_CLASS_DESCRIPTOR->applyBubbleTextColor(Ljava/lang/Integer;Landroid/widget/TextView;)V
                    """,
                )
            }


            NotesCreationThemeButtonFingerprint.let { fp ->
                fp.method.apply {
                    val literalIndex = fp.instructionMatches[0].index
                    val fieldStoreIndex =
                        indexOfFirstInstructionOrThrow(literalIndex) {
                            opcode == Opcode.IPUT_OBJECT
                        }
                    val buttonRegister =
                        (getInstruction(fieldStoreIndex) as TwoRegisterInstruction).registerA

                    addInstructionsAtControlFlowLabel(
                        fieldStoreIndex + 1,
                        invokeStaticOneArg(
                            buttonRegister,
                            "$EXTENSION_CLASS_DESCRIPTOR->attachThemeButtonLongClick(Landroid/view/View;)V",
                        ),
                    )
                }
            }

            enableSettings("notesCustomTextColor")
        }
    }