/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.misc.customize.font

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.shared.Constants.COMPATIBILITY_X
import app.crimera.utils.Constants
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c

private object CustomFontHookFingerprint : Fingerprint(
    definingClass = "emoji2/text",
    filters = listOf(
        string("end should be < than charSequence length")
    )
)

@Suppress("unused")
val customFontHook =
    bytecodePatch(
        description = "Hook to customise font",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            CustomFontHookFingerprint.method.apply {

                val charSeqReg = (instructions.first { it.opcode == Opcode.INVOKE_INTERFACE } as Instruction35c).registerC
                addInstructions(
                    0,
                    """
                    invoke-static {v$charSeqReg}, ${Constants.PATCHES_DESCRIPTOR}/customise/font/UpdateFont;->process(Ljava/lang/CharSequence;)Landroid/text/Spannable;
                    move-result-object v$charSeqReg
                    """.trimIndent(),
                )
            }
        }
    }
