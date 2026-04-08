/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.links.distractionFree

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.OpcodesFilter
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.getReference
import app.morphe.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private object InitializeNavigationButtonsListFingerprint : Fingerprint (
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    parameters = listOf("Lcom/instagram/common/session/UserSession;", "Z"),
    returnType = "Ljava/util/List;",
    filters = OpcodesFilter.opcodesToFilters(
        Opcode.IF_EQZ,
        Opcode.INVOKE_STATIC,
        Opcode.MOVE_RESULT_OBJECT,
        Opcode.RETURN_OBJECT
    )
)

private object NavigationButtonsEnumInitFingerprint : Fingerprint (
    name = "<init>",
    classFingerprint = Fingerprint(
        strings = listOf("FEED", "fragment_feed", "SEARCH", "fragment_search"),
        accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.CONSTRUCTOR)
    )
)

private const val EXTENSION_CLASS_DESCRIPTOR =
    "$PATCHES_DESCRIPTOR/hide/navigation/HideNavigationButtonsPatch;"

@Suppress("unused")
val hideNavigationButtonsPatch = bytecodePatch(
    name = "Hide navigation buttons",
    description = "Hides navigation bar buttons, such as the Reels and Create button."
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    dependsOn(settingsPatch)

    execute {
        val enumNameField: String

        // Get the field name which contains the name of the enum for the navigation button ("fragment_share", "fragment_search", ...)
        with(NavigationButtonsEnumInitFingerprint.method) {
            enumNameField = indexOfFirstInstructionOrThrow {
                opcode == Opcode.IPUT_OBJECT &&
                        (this as TwoRegisterInstruction).registerA == 2 // The p2 register
            }.let {
                getInstruction(it).getReference<FieldReference>()!!.name
            }
        }

        InitializeNavigationButtonsListFingerprint.method.apply {
            val returnIndex = indexOfFirstInstructionOrThrow(Opcode.RETURN_OBJECT)
            val buttonsListRegister = getInstruction<OneRegisterInstruction>(returnIndex).registerA
            val freeRegister = findFreeRegister(returnIndex, buttonsListRegister)

            addInstructionsAtControlFlowLabel(
                returnIndex,
                """
                        const-string v$freeRegister, "$enumNameField"
                        invoke-static { v$buttonsListRegister, v$freeRegister }, $EXTENSION_CLASS_DESCRIPTOR->filterNavigationButtons(Ljava/util/List;Ljava/lang/String;)Ljava/util/List;
                        move-result-object v$buttonsListRegister
                    """
            )
        }

        enableSettings("hideNavigationButtons")
    }
}
