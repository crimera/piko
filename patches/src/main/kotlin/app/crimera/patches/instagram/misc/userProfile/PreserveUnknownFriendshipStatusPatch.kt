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
import app.morphe.patcher.InstructionFilter
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.patcher.util.matchSingle
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val BOXED_BOOLEAN = "Ljava/lang/Boolean;"
private const val BOOLEAN_VALUE = "$BOXED_BOOLEAN->booleanValue()Z"

/**
 * Followed-by update with a nullable value, where null updates the relationship to false.
 */
private fun nullableFollowedByUpdateFilters(updaterCall: InstructionFilter) = listOf(
    opcode(Opcode.IF_EQZ),
    methodCall(smali = BOOLEAN_VALUE, location = MatchAfterImmediately()),
    opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
    updaterCall,
)

/**
 * Missing suggestion metadata must not overwrite an existing relationship with false.
 */
context(_: BytecodePatchContext)
private fun Fingerprint.preserveNullableFollowedBy() {
    val match = matchSingle()
    val guardIndex = match.instructionMatches.first().index
    val callIndex = match.instructionMatches.last().index
    val implementation = match.method.implementation!!
    implementation.replaceInstruction(
        guardIndex,
        BuilderInstruction21t(
            Opcode.IF_EQZ,
            match.instructionMatches.first().getInstruction<OneRegisterInstruction>().registerA,
            implementation.newLabelForIndex(callIndex + 1),
        ),
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
        ).matchSingle().originalMethod
        val updater = Fingerprint(
            definingClass = "Lcom/instagram/user/model/UserExtKt;",
            parameters = listOf(USER_MODEL_CLASS_NAME, "Z"),
            returnType = "V",
            filters = listOf(methodCall(reference = currentUserSetter)),
        ).matchSingle().originalMethod
        val followedByGetter = Fingerprint(
            definingClass = USER_MODEL_CLASS_NAME,
            parameters = emptyList(),
            returnType = BOXED_BOOLEAN,
            filters = listOf(string("followed_by")),
        ).matchSingle().originalMethod

        fun updaterCall() = methodCall(reference = updater, location = MatchAfterImmediately())

        // A null followed-by keeps the existing relationship.
        Fingerprint(
            filters = listOf(
                methodCall(reference = followedByGetter),
                opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
                // Converts the nullable value to false.
                methodCall(parameters = listOf("Ljava/lang/Object;"), returnType = "Z", location = MatchAfterWithin(4)),
                opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
                updaterCall(),
            ),
        ).matchSingle().let {
            val converterIndex = it.instructionMatches[2].index
            val sourceRegister = it.instructionMatches[1].getInstruction<OneRegisterInstruction>().registerA
            it.method.addInstructionsWithLabels(
                converterIndex,
                "if-eqz v$sourceRegister, :preserve_friendship",
                ExternalLabel("preserve_friendship", it.method.getInstruction(it.instructionMatches.last().index + 1)),
            )
        }

        // Suggested user update.
        val legacySuggestedUserUpdate = Fingerprint(
            parameters = emptyList(),
            returnType = "V",
            filters = nullableFollowedByUpdateFilters(updaterCall()),
        )
        val suggestedUserUpdate = if (legacySuggestedUserUpdate.matchOrNull() != null) {
            legacySuggestedUserUpdate
        } else {
            Fingerprint(
                parameters = listOf("L"),
                returnType = "Ljava/lang/Object;",
                filters = nullableFollowedByUpdateFilters(updaterCall()),
            )
        }
        suggestedUserUpdate.preserveNullableFollowedBy()

        // Inbox suggestions update.
        Fingerprint(
            parameters = listOf("Ljava/util/List;", "Ljava/lang/String;"),
            returnType = "V",
            filters = nullableFollowedByUpdateFilters(updaterCall()),
        ).preserveNullableFollowedBy()
    }
}
