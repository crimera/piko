/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.dm.categories

import app.crimera.patches.instagram.misc.extension.hooks.instagramInitHook
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val HOOK_CLASS = "$PATCHES_DESCRIPTOR/dm/DirectOrganizer;"
private const val BAR_CLASS = "$PATCHES_DESCRIPTOR/dm/InboxFolderBar;"

@Suppress("unused")
val categorizeMenuPatch =
    bytecodePatch(
        name = "Chat categories",
        description = "Adds a \"Categorize\" option to the chat long-press menu and folder chips above the inbox.",
        default = true,
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            instagramInitHook.fingerprint.method.apply {
                val superIndex = indexOfFirstInstruction(Opcode.INVOKE_SUPER)
                addInstruction(superIndex + 1, "invoke-static {}, $BAR_CLASS->init()V")
            }

            InboxAdapterUpdateFingerprint.method.addInstruction(
                0,
                "invoke-static {p1}, $BAR_CLASS->onAdapterUpdate(Ljava/lang/Object;)V",
            )

            ThreadLongPressMenuFingerprint.method.apply {
                // Inject once every native row is added, right before the sheet is created.
                val allInstructions = instructions.toList()
                val showInvokeIndex = allInstructions.indexOfFirst { instruction ->
                    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    instruction.opcode.name.startsWith("invoke-direct") &&
                        reference?.definingClass == "LX/0QcN;" &&
                        reference.name == "<init>"
                }
                if (showInvokeIndex <= 0) {
                    throw PatchException("Thread long-press menu has no ActionSheet show call")
                }

                val showInvoke = allInstructions[showInvokeIndex]
                val showRegisters = showInvoke.registersUsed
                if (showRegisters.size < 2) {
                    throw PatchException("Thread long-press menu show call has unexpected registers")
                }
                val configRegister = showRegisters[1]

                val paramBase = implementation!!.registerCount - 17
                val threadKeyRegister = paramBase + 8 // p8: the chat's DirectThreadKey
                val rowViewRegister = paramBase + 1 // p1: the row's View

                val freeRegisters = getFreeRegisterProvider(showInvokeIndex, 3)
                val configScratch = freeRegisters.getFreeRegister()
                val keyScratch = freeRegisters.getFreeRegister()
                val viewScratch = freeRegisters.getFreeRegister()

                addInstructions(
                    showInvokeIndex,
                    """
                    move-object/from16 v$viewScratch, v$rowViewRegister
                    move-object/from16 v$keyScratch, v$threadKeyRegister
                    move-object/from16 v$configScratch, v$configRegister
                    invoke-static {v$configScratch, v$keyScratch, v$viewScratch}, $HOOK_CLASS->onThreadMenuBuilt(Ljava/lang/Object; Ljava/lang/Object; Landroid/view/View;)V
                    """.trimIndent(),
                )
            }
        }
    }
