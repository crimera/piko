/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.stories

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PREF_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

private object StoryFollowButtonComponentFingerprint : Fingerprint(
    returnType = "Ljava/lang/String;",
    parameters = emptyList(),
    filters = listOf(string("StoryFollowButtonViewComponent")),
)

@Suppress("unused")
val hideStoryFollowButtonPatch = bytecodePatch(
    name = "Hide story follow button",
    description = "Hides the follow button at the top of stories.",
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    dependsOn(settingsPatch)

    execute {
        val componentClass = StoryFollowButtonComponentFingerprint.originalMethod.definingClass
        val binding = Fingerprint(
            definingClass = componentClass,
            returnType = "V",
            parameters = listOf("Ljava/lang/Object;"),
            filters = listOf(
                methodCall(smali = "Ljava/lang/Number;->intValue()I"),
                opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
                literal(8, location = MatchAfterImmediately()),
                opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
            ),
        )
        val state = binding.instructionMatches[1]
        val register = state.instruction.registersUsed.singleOrNull()
            ?: throw PatchException("Expected one story follow button state register")
        if (binding.instructionMatches[3].instruction.registersUsed != listOf(register)) {
            throw PatchException("Unexpected story follow button visibility branch")
        }

        // State zero uses the native path that hides both story follow button variants.
        binding.method.addInstructions(
            state.index + 1,
            """
            invoke-static/range {v$register .. v$register}, $PREF_DESCRIPTOR->storyFollowButtonState(I)I
            move-result v$register
            """.trimIndent(),
        )
        enableSettings("hideStoryFollowButton")
    }
}
