/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.history

import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.INTEGRATIONS_PACKAGE
import app.crimera.patches.instagram.utils.Constants.USER_SESSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.findFreeRegister
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

// Reels viewer onPageSelected(newPosition, oldPosition). Its first call to a private helper of the
// listener resolves the clips item at the new position.
private object ClipsPageSelectedFingerprint : Fingerprint(
    returnType = "V",
    parameters = listOf("I", "I"),
    strings = listOf("ClipsViewerViewPagerListener.onPageSelected"),
)

private const val VIEW_HISTORY_HOOK_CLASS = "$INTEGRATIONS_PACKAGE/patches/history/ViewHistoryHook;"

@Suppress("unused")
val reelsViewHistoryPatch =
    bytecodePatch(
        name = "Log Reels to view history",
        description = "Records each Reel to Piko's view history as it is shown in the Reels viewer.",
        default = true,
    ) {
        dependsOn(viewHistorySettingsPatch, decoderEntity)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            fun fail(what: String): Nothing = throw PatchException("Reels view history: $what not found")

            val listenerClass = ClipsPageSelectedFingerprint.classDef
            ClipsPageSelectedFingerprint.method.apply {
                val itemLookup =
                    instructions.indexOfFirst {
                        it.opcode == Opcode.INVOKE_DIRECT &&
                            ((it as ReferenceInstruction).reference as MethodReference).definingClass == listenerClass.type
                    }.takeIf { it >= 0 } ?: fail("clips item lookup")
                val itemClass = ((instructions[itemLookup] as ReferenceInstruction).reference as MethodReference).returnType
                val itemResult = instructions[itemLookup + 1]
                if (itemResult.opcode != Opcode.MOVE_RESULT_OBJECT) fail("clips item result")
                val itemReg = (itemResult as OneRegisterInstruction).registerA

                val mediaField =
                    classDefBy(itemClass).fields.singleOrNull { it.type == MEDIA_CLASS_NAME } ?: fail("clips item media")
                val userSessionField =
                    listenerClass.fields.firstOrNull { it.type == USER_SESSION_CLASS } ?: fail("user session")

                val insertIndex = itemLookup + 2
                val used = mutableListOf(itemReg)
                fun freeRegister() = findFreeRegister(insertIndex, used).also { used += it }
                val sessionReg = freeRegister()
                val mediaReg = freeRegister()
                val indexReg = freeRegister()
                // iget and non-range invoke only encode v0-v15.
                if (used.any { it > 15 }) fail("low registers")

                addInstructionsWithLabels(
                    insertIndex,
                    """
                    if-eqz v$itemReg, :piko_skip
                    iget-object v$mediaReg, v$itemReg, $mediaField
                    move-object/from16 v$sessionReg, p0
                    iget-object v$sessionReg, v$sessionReg, $userSessionField
                    const/4 v$indexReg, 0x0
                    invoke-static {v$mediaReg, v$sessionReg, v$indexReg}, $VIEW_HISTORY_HOOK_CLASS->logMediaView(Ljava/lang/Object;Lcom/instagram/common/session/UserSession;I)V
                    :piko_skip
                    nop
                    """.trimIndent(),
                )
            }
        }
    }
