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
import app.morphe.patcher.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val SUGGESTED_PROFILES = "$PATCHES_DESCRIPTOR/SuggestedProfiles;"

context(_: BytecodePatchContext)
private fun Fingerprint.filterSuggestionFlags() {
    method.apply {
        instructionMatches.filter { it.instruction.opcode == Opcode.MOVE_RESULT }.asReversed().forEach {
            val register = getInstruction<OneRegisterInstruction>(it.index).registerA
            addInstructions(
                it.index + 1,
                """
                invoke-static/range {v$register .. v$register}, $SUGGESTED_PROFILES->showSuggestedUsers(Z)Z
                move-result v$register
                """,
            )
        }
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

            MutualFollowersResponseFingerprint.let {
                val keyIndex = it.instructionMatches[1].index
                val register = it.instructionMatches[1].getInstruction<OneRegisterInstruction>().registerA
                it.method.filterSuggestedSection(keyIndex + 1, register)
            }

            FollowListSuggestionsFingerprint.filterSuggestionFlags()
            FollowRequestSuggestionsFingerprint.filterSuggestionFlags()

            FriendingCenterResponseParserFingerprint.let {
                val index = it.instructionMatches.first().index
                val register = it.instructionMatches.first().instruction.registersUsed.first()
                it.method.filterSuggestedSection(index, register)
            }

            enableSettings("disableDiscoverPeople")
        }
    }
