/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.misc

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_CALL_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.patches.instagram.utils.hideFeedUfiStates
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.findFreeRegister
import app.morphe.patcher.util.registersUsed
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode

private const val HIDE_SHARE_BUTTON = "$PREF_CALL_DESCRIPTOR->hideShareButton()Z"

@Suppress("unused")
val hideShareButtonPatch = bytecodePatch(
    name = "Hide share button",
    description = "Hides the share button on posts, reels and stories",
) {
    dependsOn(settingsPatch)
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute {
        hideFeedUfiStates(
            HIDE_SHARE_BUTTON,
            ", isShareEnabled=",
            ", shouldShowShareCountInUfi=",
        )

        val reels = Fingerprint(
            returnType = "Z",
            parameters = listOf("Lcom/instagram/clips/intf/ClipsViewerConfig;", "L", "L", "L"),
            filters = listOf(string("android_purge_26_q3_UfiUseCase_shouldShowShareButton")),
        ).method
        val scratch = reels.findFreeRegister(0)
        // Instagram's false result selects the hidden state for both the button and count.
        reels.addInstructionsWithLabels(
            0,
            """
            $HIDE_SHARE_BUTTON
            move-result v$scratch
            if-eqz v$scratch, :original
            const/4 v$scratch, 0x0
            return v$scratch
            """.trimIndent(),
            ExternalLabel("original", reels.getInstruction(0)),
        )
        val storyComponent = Fingerprint(
            returnType = "Ljava/lang/String;",
            parameters = emptyList(),
            filters = listOf(string("StoryReshareButtonViewComponent")),
        ).originalMethod.definingClass
        // The component has separate button and label visibility gates, shared by
        // both layouts. Leave its independent layout-selection boolean untouched.
        val storyStates = listOf(
            listOf(methodCall(smali = "Landroid/view/View;->getContentDescription()Ljava/lang/CharSequence;", location = MatchAfterImmediately())),
            listOf(
                fieldAccess(opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
                methodCall(name = "getValue", parameters = emptyList(), returnType = "Ljava/lang/Object;", location = MatchAfterImmediately()),
            ),
        ).map { tail ->
            Fingerprint(
                definingClass = storyComponent,
                returnType = "V",
                parameters = listOf("Ljava/lang/Object;"),
                filters = listOf(
                    fieldAccess(type = "Z", opcode = Opcode.IGET_BOOLEAN),
                    opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
                ) + tail,
            )
        }
        storyStates.sortedByDescending { it.instructionMatches.first().index }.forEach { fingerprint ->
            val read = fingerprint.instructionMatches.first()
            val registers = read.instruction.registersUsed
            if (registers.size != 2 || fingerprint.instructionMatches[1].instruction.registersUsed != listOf(registers[0])) {
                throw PatchException("Unexpected story share visibility branch")
            }
            val visible = registers[0]
            fingerprint.method.addInstructionsWithLabels(
                read.index + 1,
                """
                if-eqz v$visible, :original
                $HIDE_SHARE_BUTTON
                move-result v$visible
                xor-int/lit8 v$visible, v$visible, 0x1
                """.trimIndent(),
                ExternalLabel("original", fingerprint.method.getInstruction(read.index + 1)),
            )
        }
        enableSettings("hideShareButton")
    }
}
