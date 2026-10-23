/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.misc

import app.crimera.patches.instagram.links.interceptUriPatch
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.matchSingle
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val SUGGESTED_PROFILES = "$PATCHES_DESCRIPTOR/SuggestedProfiles;"

context(_: BytecodePatchContext)
private fun Fingerprint.filterSuggestionFlags() {
    val match = matchSingle()
    match.instructionMatches.filter { it.instruction.opcode == Opcode.MOVE_RESULT }.asReversed().forEach {
        val register = match.method.getInstruction<OneRegisterInstruction>(it.index).registerA
        match.method.addInstructions(
            it.index + 1,
            """
            invoke-static/range {v$register .. v$register}, $SUGGESTED_PROFILES->showSuggestedUsers(Z)Z
            move-result v$register
            """.trimIndent(),
        )
    }
}

private fun MutableMethod.filterSuggestedSection(index: Int, register: Int) {
    addInstructions(index, """
        invoke-static/range {v$register .. v$register}, $SUGGESTED_PROFILES->filterSuggestedSection(Ljava/lang/String;)Ljava/lang/String;
        move-result-object v$register
    """.trimIndent())
}

@Suppress("unused")
val disableDiscoverPeoplePatch =
    bytecodePatch(
        name = "Disable discover people",
        description = "Hides suggested accounts",
    ) {
        dependsOn(settingsPatch, interceptUriPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            hideActivityFeedSuggestions()
            hideReelsSuggestions()

            MutualFollowersResponseFingerprint.matchSingle().let { match ->
                val keyIndex = match.instructionMatches[1].index
                val register = match.instructionMatches[1].getInstruction<OneRegisterInstruction>().registerA
                match.method.filterSuggestedSection(keyIndex + 1, register)
            }

            FollowListSuggestionsFingerprint.filterSuggestionFlags()
            FollowRequestSuggestionsFingerprint.filterSuggestionFlags()

            FriendingCenterResponseParserFingerprint.matchSingle().let { match ->
                val index = match.instructionMatches.first().index
                val register = match.instructionMatches.first().instruction.registersUsed.first()
                match.method.filterSuggestedSection(index, register)
            }

            enableSettings("disableDiscoverPeople")
        }
    }
