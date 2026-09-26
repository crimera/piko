/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.misc

import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

context(_: BytecodePatchContext)
internal fun hideReelsSuggestions() {
    val method = ReelsHiddenItemsFingerprint.matchAll(1..1).single().method
    val code = method.instructions
    val castIndex = code.indices.singleOrNull { code[it].opcode == Opcode.CHECK_CAST }
        ?: throw PatchException("Expected one reels item cast")
    val itemType = code[castIndex].getReference<TypeReference>()?.type
        ?: throw PatchException("Missing reels item type")
    val itemRegister = code[castIndex].registersUsed.singleOrNull()
        ?: throw PatchException("Missing reels item register")

    val loopIndex = code.indices.singleOrNull {
        val reference = code[it].getReference<MethodReference>()
        reference?.definingClass == "Ljava/util/Iterator;" && reference.name == "hasNext" &&
            reference.parameterTypes.isEmpty() && reference.returnType == "Z"
    } ?: throw PatchException("Expected one reels filter iterator")
    val result = code.getOrNull(loopIndex + 1)
    val loopExit = code.getOrNull(loopIndex + 2)
    val scratch = result?.registersUsed?.singleOrNull()
        ?: throw PatchException("Missing reels filter scratch register")
    if (result.opcode != Opcode.MOVE_RESULT || loopExit?.opcode != Opcode.IF_EQZ ||
        loopExit.registersUsed.singleOrNull() != scratch || scratch == itemRegister ||
        castIndex != loopIndex + 5 ||
        code[castIndex - 2].getReference<MethodReference>()?.toString() !=
        "Ljava/util/Iterator;->next()Ljava/lang/Object;" ||
        code[castIndex - 1].opcode != Opcode.MOVE_RESULT_OBJECT ||
        code[castIndex - 1].registersUsed.singleOrNull() != itemRegister
    ) {
        throw PatchException("Unexpected reels filter iteration")
    }

    val lifecycle = ReelsSuggestedUsersExitFingerprint.matchAll(1..1).single()
    val classifier = lifecycle.method.instructions.mapNotNull { instruction ->
        val reference = instruction.getReference<MethodReference>()
        reference?.takeIf {
            instruction.opcode in listOf(Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE) &&
                it.definingClass == lifecycle.classDef.type && it.returnType == "Z" &&
                it.parameterTypes == listOf(itemType)
        }
    }.distinctBy { it.toString() }.singleOrNull()
        ?: throw PatchException("Expected one native reels suggested-users classifier")
    val classifierMethod = lifecycle.classDef.methods.singleOrNull { it.toString() == classifier.toString() }
        ?: throw PatchException("Missing native reels suggested-users classifier")
    if (!AccessFlags.PUBLIC.isSet(classifierMethod.accessFlags) ||
        !AccessFlags.STATIC.isSet(classifierMethod.accessFlags)
    ) {
        throw PatchException("Reels suggested-users classifier is not accessible")
    }

    // Native classification covers both blending and netego cards; skip the item before it gets a page.
    method.addInstructionsWithLabels(castIndex + 1, """
        invoke-static/range {v$itemRegister .. v$itemRegister}, $classifier
        move-result v$scratch
        invoke-static/range {v$scratch .. v$scratch}, $PATCHES_DESCRIPTOR/SuggestedProfiles;->hideReelsSuggestedUsers(Z)Z
        move-result v$scratch
        if-nez v$scratch, :next_item
    """.trimIndent(), ExternalLabel("next_item", code[loopIndex]))
}
