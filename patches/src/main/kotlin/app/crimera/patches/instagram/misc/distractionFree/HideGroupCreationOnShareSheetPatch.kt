/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.distractionFree

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.findFreeRegister
import app.morphe.util.indexOfFirstInstruction
import com.android.tools.smali.dexlib2.Opcode

internal object ShareSheetItemsBinderFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("Ljava/util/List;", "Z", "Lcom/instagram/common/session/UserSession;"),
    definingClass = "Lcom/instagram/direct/fragment/sharesheet/view/DirectShareSheetFragmentMessageComposerViewBinder;",
)

@Suppress("unused")
val hideGroupCreationOnShareSheetPatch =
    bytecodePatch(
        name = "Hide group creation button on sharesheet",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)
        execute {
            ShareSheetItemsBinderFingerprint.method.apply {

                val firstGoto16Index = indexOfFirstInstruction(Opcode.GOTO_16)
                val firstInvokeStaticAfterGoto = indexOfFirstInstruction(firstGoto16Index, Opcode.INVOKE_STATIC)

                val conditionIndex = firstGoto16Index + 2
                val freeRegister = findFreeRegister(conditionIndex + 1)

                addInstructionsWithLabels(
                    conditionIndex,
                    """
                    ${PREF_CALL_DESCRIPTOR}->hideGroupCreationOnSharesheet()Z
                     move-result v$freeRegister
                     if-eqz v$freeRegister, :piko
                     return-void
                    """.trimIndent(),
                    ExternalLabel("piko", getInstruction(firstInvokeStaticAfterGoto)),
                )

                enableSettings("hideGroupCreationOnSharesheet")
            }
        }
    }
