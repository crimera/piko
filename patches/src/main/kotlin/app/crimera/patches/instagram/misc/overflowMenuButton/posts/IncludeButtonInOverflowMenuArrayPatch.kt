/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.overflowMenuButton.posts

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.FEED_OVERFLOW_MENU_BUTTON_CLASS
import app.crimera.patches.instagram.utils.Constants.MEDIA_OPTIONS_CLASS
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

@Suppress("unused")
val includeButtonsInOverflowMenuArrayPatch =
    bytecodePatch(
        description = "This patch hooks array values initialisation in overflow menu button constructor.",
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            OverflowMenuButtonEnumInitialiser.method.apply {
                val lastInvokeStaticIndex = instructions.last { it.opcode == Opcode.INVOKE_STATIC }.location.index
                val arraySPutObjectIndex = lastInvokeStaticIndex - 1

                val arrayRegister = getInstruction(arraySPutObjectIndex).registersUsed[0]

                addInstructions(
                    lastInvokeStaticIndex - 1,
                    """
                    invoke-static {}, $FEED_OVERFLOW_MENU_BUTTON_CLASS->addToMenuOptionArray()[$MEDIA_OPTIONS_CLASS
                    move-result-object v$arrayRegister
                    """.trimIndent(),
                )
            }
        }
    }
