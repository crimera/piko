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
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val SUGGESTED_PROFILES = "$PATCHES_DESCRIPTOR/SuggestedProfiles;"

private fun MutableMethod.filterSuggestionFlag(key: String, expectedReads: Int) {
    val reads = instructions.mapIndexedNotNull { index, instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference as? StringReference
        if (reference?.string == key) index else null
    }
    if (reads.size != expectedReads) {
        throw PatchException("Expected $expectedReads reads of $key, found ${reads.size}")
    }
    for (index in reads.asReversed()) {
        val call = instructions.getOrNull(index + 1)
        val reference = (call as? ReferenceInstruction)?.reference as? MethodReference
        val result = instructions.getOrNull(index + 2)
        val keyRegister = instructions[index].registersUsed.singleOrNull()
        if (reference?.definingClass != "Landroid/os/BaseBundle;" ||
            reference.name != "getBoolean" || reference.returnType != "Z" ||
            reference.parameterTypes !in listOf(listOf("Ljava/lang/String;"), listOf("Ljava/lang/String;", "Z")) ||
            call.registersUsed.getOrNull(1) != keyRegister || result?.opcode != Opcode.MOVE_RESULT
        ) {
            throw PatchException("Unexpected suggestion flag read for $key")
        }
        val register = result.registersUsed.singleOrNull()
            ?: throw PatchException("Missing suggestion flag result register")
        addInstructions(index + 3, """
            invoke-static/range {v$register .. v$register}, $SUGGESTED_PROFILES->showSuggestedUsers(Z)Z
            move-result v$register
        """.trimIndent())
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

            MutualFollowersResponseFingerprint.matchAll(1..1).single().method.apply {
                val index = instructions.mapIndexedNotNull { index, instruction ->
                    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    if (reference?.definingClass == "Ljava/lang/String;" && reference.name == "hashCode" &&
                        reference.parameterTypes.isEmpty() && reference.returnType == "I"
                    ) index else null
                }.singleOrNull() ?: throw PatchException("Expected one mutual followers response key hash")
                if (index < 1 || instructions[index - 1].opcode != Opcode.MOVE_RESULT_OBJECT) {
                    throw PatchException("Could not locate the mutual followers response key")
                }
                val register = instructions[index].registersUsed.singleOrNull()
                    ?: throw PatchException("Missing mutual followers key register")
                if (instructions[index - 1].registersUsed.singleOrNull() != register) {
                    throw PatchException("Unexpected mutual followers key register")
                }
                filterSuggestedSection(index, register)
            }

            FollowListSuggestionsFingerprint.matchAll(1..1).single().method
                .filterSuggestionFlag(FOLLOW_LIST_SUGGESTIONS, 1)
            FollowRequestSuggestionsFingerprint.matchAll(1..1).single().method
                .filterSuggestionFlag(FOLLOW_REQUEST_SUGGESTIONS, 2)

            val categoryParser = FriendingCenterCategoryFingerprint.matchAll(1..1).single().method
            FriendingCenterResponseFingerprint.matchAll(1..1).single().method.apply {
                val index = instructions.mapIndexedNotNull { index, instruction ->
                    val reference = (instruction as? ReferenceInstruction)?.reference as? MethodReference
                    if (reference?.toString() == categoryParser.toString()) index else null
                }.singleOrNull() ?: throw PatchException("Expected one friending center category parser call")
                val register = instructions[index].registersUsed.singleOrNull()
                    ?: throw PatchException("Missing friending center category register")
                val result = instructions.getOrNull(index + 1)
                val skip = instructions.getOrNull(index + 2)
                if (result?.opcode != Opcode.MOVE_RESULT_OBJECT || skip?.opcode != Opcode.IF_EQZ ||
                    result.registersUsed.singleOrNull() != skip.registersUsed.singleOrNull()
                ) {
                    throw PatchException("Friending center no longer skips unknown categories")
                }
                filterSuggestedSection(index, register)
            }

            enableSettings("disableDiscoverPeople")
        }
    }
