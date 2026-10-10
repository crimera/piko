/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.userProfile

import app.crimera.patches.instagram.entity.decoder.USER_MODEL_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.methodCall
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val BOXED_BOOLEAN = "Ljava/lang/Boolean;"
private const val BOOLEAN_VALUE = "$BOXED_BOOLEAN->booleanValue()Z"

private fun MutableMethod.followedByCallIndex(updater: MethodReference): Int =
    instructions.withIndex().singleOrNull { (_, instruction) ->
        instruction.opcode == Opcode.INVOKE_STATIC &&
            instruction.getReference<MethodReference>()?.toString() == updater.toString()
    }?.index ?: throw PatchException("Expected one followed-by update in $this")

private fun MutableMethod.preserveNullableFollowedBy(updater: MethodReference) {
    val callIndex = followedByCallIndex(updater)
    val code = instructions
    if (callIndex < 4 || callIndex + 1 >= code.size) {
        throw PatchException("Missing nullable followed-by update sequence in $this")
    }
    val source = code[callIndex - 4]
    val guard = code[callIndex - 3] as? BuilderOffsetInstruction
        ?: throw PatchException("Missing nullable followed-by guard in $this")
    val valueRegister = source.registersUsed.firstOrNull()
    val callRegisters = code[callIndex].registersUsed
    val validSource = when (source.opcode) {
        Opcode.IGET_OBJECT -> source.getReference<FieldReference>()?.type == BOXED_BOOLEAN
        Opcode.MOVE_RESULT_OBJECT -> code.getOrNull(callIndex - 5)?.getReference<MethodReference>()?.let {
            it.returnType == BOXED_BOOLEAN && it.parameterTypes.isEmpty()
        } == true
        else -> false
    }
    if (!validSource || valueRegister == null || callRegisters.size != 2 ||
        guard.opcode != Opcode.IF_EQZ || guard.registersUsed != listOf(valueRegister) ||
        code[callIndex - 2].getReference<MethodReference>()?.toString() != BOOLEAN_VALUE ||
        code[callIndex - 2].registersUsed != listOf(valueRegister) ||
        code[callIndex - 1].opcode != Opcode.MOVE_RESULT ||
        code[callIndex - 1].registersUsed != listOf(callRegisters[1])
    ) throw PatchException("Unexpected nullable followed-by registers in $this")

    val nullTarget = guard.target.location.index
    if (nullTarget != callIndex) {
        val zero = code.getOrNull(nullTarget)
        val jump = code.getOrNull(nullTarget + 1) as? BuilderOffsetInstruction
        if (zero?.opcode != Opcode.CONST_4 ||
            (zero as? NarrowLiteralInstruction)?.narrowLiteral != 0 ||
            zero.registersUsed != listOf(callRegisters[1]) ||
            jump?.opcode != Opcode.GOTO || jump.target.location.index != callIndex
        ) throw PatchException("Expected null followed-by to default to false in $this")
    }

    // Missing suggestion metadata must not overwrite an existing relationship with false.
    implementation!!.replaceInstruction(
        callIndex - 3,
        BuilderInstruction21t(Opcode.IF_EQZ, valueRegister, implementation!!.newLabelForIndex(callIndex + 1)),
    )
}

val preserveUnknownFriendshipStatusPatch = bytecodePatch(
    description = "Preserves friendship status when suggested-user metadata omits followed_by.",
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    dependsOn(decoderEntity)

    execute {
        val currentUserSetter = Fingerprint(
            definingClass = USER_MODEL_CLASS_NAME,
            parameters = listOf(BOXED_BOOLEAN),
            returnType = "V",
            filters = listOf(string("is_following_current_user")),
        ).matchAll(1..1).single().method
        val updater = Fingerprint(
            definingClass = "Lcom/instagram/user/model/UserExtKt;",
            parameters = listOf(USER_MODEL_CLASS_NAME, "Z"),
            returnType = "V",
            filters = listOf(methodCall(
                definingClass = USER_MODEL_CLASS_NAME,
                name = currentUserSetter.name,
                parameters = listOf(BOXED_BOOLEAN),
                returnType = "V",
            )),
        ).matchAll(1..1).single().method

        fun updaterCall() = methodCall(
            definingClass = updater.definingClass,
            name = updater.name,
            parameters = listOf(USER_MODEL_CLASS_NAME, "Z"),
            returnType = "V",
            opcode = Opcode.INVOKE_STATIC,
        )

        val suggestedUserUpdate = Fingerprint(
            parameters = emptyList(),
            returnType = "V",
            filters = listOf(methodCall(
                definingClass = BOXED_BOOLEAN,
                name = "booleanValue",
                parameters = emptyList(),
                returnType = "Z",
            ), updaterCall()),
        ).matchAll(1..1).single().method
        val inboxSuggestionsUpdate = Fingerprint(
            parameters = listOf("Ljava/util/List;", "Ljava/lang/String;"),
            returnType = "V",
            filters = listOf(updaterCall()),
        ).matchAll(1..1).single().method
        val suggestedUserConversion = Fingerprint(
            returnType = USER_MODEL_CLASS_NAME,
            custom = { method, _ -> method.parameterTypes.size == 1 },
            filters = listOf(updaterCall()),
        ).matchAll(1..1).single().method
        val followedByGetter = Fingerprint(
            definingClass = USER_MODEL_CLASS_NAME,
            parameters = emptyList(),
            returnType = BOXED_BOOLEAN,
            filters = listOf(string("followed_by")),
        ).matchAll(1..1).single().method

        suggestedUserConversion.apply {
            val callIndex = followedByCallIndex(updater)
            val code = instructions
            if (callIndex < 4 || callIndex + 1 >= code.size) {
                throw PatchException("Missing suggested-user followed-by conversion")
            }
            val nullableResult = code[callIndex - 3]
            val converter = code[callIndex - 2].getReference<MethodReference>()
            val sourceRegister = nullableResult.registersUsed.singleOrNull()
            val callRegisters = code[callIndex].registersUsed
            if (code[callIndex - 4].getReference<MethodReference>()?.toString() != followedByGetter.toString() ||
                nullableResult.opcode != Opcode.MOVE_RESULT_OBJECT || sourceRegister == null ||
                code[callIndex - 2].opcode != Opcode.INVOKE_STATIC ||
                converter?.parameterTypes != listOf("Ljava/lang/Object;") || converter.returnType != "Z" ||
                code[callIndex - 2].registersUsed != listOf(sourceRegister) ||
                callRegisters.size != 2 || code[callIndex - 1].opcode != Opcode.MOVE_RESULT ||
                code[callIndex - 1].registersUsed != listOf(callRegisters[1])
            ) throw PatchException("Unexpected suggested-user followed-by conversion")

            addInstructionsWithLabels(
                callIndex - 2,
                "if-eqz v$sourceRegister, :preserve_friendship",
                ExternalLabel("preserve_friendship", getInstruction(callIndex + 1)),
            )
        }
        suggestedUserUpdate.preserveNullableFollowedBy(updater)
        inboxSuggestionsUpdate.preserveNullableFollowedBy(updater)
    }
}
