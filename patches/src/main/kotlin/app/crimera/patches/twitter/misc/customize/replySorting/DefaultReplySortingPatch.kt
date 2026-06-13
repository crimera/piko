/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.customize.replySorting

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.PatchException
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private object ReplySortingInvokeClassFinderFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/tweetview/focal/ui/replysorting/ReplySortingViewDelegateBinder;",
)

private object replySortingLastSelectedFinderFingerprint : Fingerprint(
    strings =
        listOf(
            "controller_data",
            "reply_sorting_enabled",
            "reply_sorting",
        ),
)

@Suppress("unused")
val defaultReplySortingPatch =
    bytecodePatch(
        name = "Customize default reply sorting",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val replySortingInvokeClass =
                ReplySortingInvokeClassFinderFingerprint.classDef.fields
                    .firstOrNull()?.type
                    ?: throw PatchException("Failed to find any fields in ${ReplySortingInvokeClassFinderFingerprint.definingClass}")

            val method = mutableClassDefBy(replySortingInvokeClass).methods.firstOrNull()
                ?: throw PatchException("Failed to find any methods in ${replySortingInvokeClass}")

            val instructions = method.instructions
            val sgetInstruction = instructions.firstOrNull { it.opcode == Opcode.SGET_OBJECT }
                ?: throw PatchException("Failed to find SGET_OBJECT in ${replySortingInvokeClass}")

            val loc = sgetInstruction.location.index
            val rClass = (method.getInstruction<ReferenceInstruction>(loc).reference as FieldReference).definingClass
            val r0 = method.getInstruction<OneRegisterInstruction>(loc).registerA
            method.addInstructions(
                loc + 1,
                """
                invoke-static {}, $PREF_DESCRIPTOR;->defaultReplySortFilter()Ljava/lang/String;
                move-result-object v$r0
                invoke-static{v0}, $rClass->valueOf(Ljava/lang/String;)$rClass
                move-result-object v$r0
                """.trimIndent(),
            )

            val method2 = replySortingLastSelectedFinderFingerprint.method
            val inst = method2.instructions

            val stringRefMatch = inst
                .filter { it.opcode == Opcode.CONST_STRING }
                .firstOrNull { it.getReference<StringReference>()?.string == "reply_sorting" }
                ?: throw PatchException("Failed to find 'reply_sorting' string in ${replySortingLastSelectedFinderFingerprint.definingClass}")

            stringRefMatch.apply {
                val loc2 = location.index
                val r = method2.getInstruction<OneRegisterInstruction>(loc2 - 1).registerA
                method2.addInstructions(
                    loc2,
                    """
                    invoke-static {v$r},$PREF_DESCRIPTOR;->setReplySortFilter(Ljava/lang/String;)V
                    """.trimIndent(),
                )
            }
            enableSettings("defaultReplySortFilter")
        }
    }
