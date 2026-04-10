/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.timeline.hideHiddenReplies

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private object HideHiddenRepliesFingerprint : Fingerprint(
    definingClass = "Lcom/twitter/model/json/timeline/urt/JsonTimelineTweet;",
    returnType = "Ljava/lang/Object;",
)

@Suppress("unused")
val hideHiddenRepliesPatch =
    bytecodePatch(
        name = "Hide hidden replies",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch)

        execute {
            val method = HideHiddenRepliesFingerprint.method
            val instructions = method.instructions

            val get_bool = instructions.last { it.opcode == Opcode.IGET_BOOLEAN }.location.index
            val reg = method.getInstruction<TwoRegisterInstruction>(get_bool).registerA

            val M = "invoke-static {v$reg}, $PREF_DESCRIPTOR;->hideHiddenReplies(Z)Z"

            method.addInstructions(
                get_bool + 1,
                """
                $M
                move-result v$reg
                """.trimIndent(),
            )

            enableSettings("hideHiddenReplies")
        }
    }
