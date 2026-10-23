/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.overflowMenuButton.posts

import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.FEED_OVERFLOW_MENU_BUTTON_CLASS
import app.crimera.utils.changeFirstString
import app.crimera.utils.classNameToExtension
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.matchSingle

@Suppress("unused")
val hookOverflowMenuButton =
    bytecodePatch(
        description = "This patch hooks array values initialisation in overflow menu button constructor.",
    ) {
        dependsOn(includeButtonsInOverflowMenuArrayPatch, hookOverflowMenuButtonOnClickPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            val enumBtnClass = classNameToExtension(EnumButtonClassFingerprint.originalClassDef.type)
            GetEnumButtonClassExtensionFingerprint.changeFirstString(enumBtnClass)

            AddFeedButtonExtensionFingerprint.changeFirstString(AddFeedOverflowOptionFingerprint.originalMethod.name)

            (AddFeedButtonFingerprint.matchOrNull() ?: AddFeedButtonFactoryFingerprint.matchSingle()).let {
                val arrayListRegister = it.instructionMatches[1].instruction.registersUsed.single()
                val checkCastIndex = it.instructionMatches.last().index
                val checkCastRegister = it.instructionMatches.last().instruction.registersUsed.single()

                it.method.addInstructions(
                    checkCastIndex + 1,
                    """
                    invoke-static {v$checkCastRegister,v$arrayListRegister},$FEED_OVERFLOW_MENU_BUTTON_CLASS->addFeedOverflowButton(Ljava/lang/Object;Ljava/util/ArrayList;)V
                    """.trimIndent(),
                )
            }
        }
    }
