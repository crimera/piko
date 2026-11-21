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
import app.morphe.patcher.patch.patch
import app.morphe.patcher.util.registersUsed

@Suppress("unused")
val hookOverflowMenuButton =
    patch(
        description = "This patch hooks array values initialisation in overflow menu button constructor.",
    ) {
        dependsOn(includeButtonsInOverflowMenuArrayPatch, hookOverflowMenuButtonOnClickPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        execute {
            val enumBtnClass = classNameToExtension(EnumButtonClassFingerprint.originalClassDef.type)
            GetEnumButtonClassExtensionFingerprint.changeFirstString(enumBtnClass)

            val addOption = AddFeedOverflowOptionFingerprint.originalMethod
            AddFeedButtonExtensionFingerprint.changeFirstString(addOption.name)

            feedMenuListFingerprint(addOption.definingClass, addOption.parameterTypes[3].toString()).match().let {
                val creatorRegister = it.instructionMatches.first().instruction.registersUsed.first()
                val modelRegister = it.instructionMatches[1].instruction.registersUsed.single()
                val list = it.instructionMatches.last()
                val listRegister = list.instruction.registersUsed.single()

                it.method.addInstructions(
                    list.index + 1,
                    """
                    invoke-static {v$creatorRegister,v$modelRegister,v$listRegister},$FEED_OVERFLOW_MENU_BUTTON_CLASS->addFeedOverflowButton(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/ArrayList;)V
                    """.trimIndent(),
                )
            }
        }
    }
