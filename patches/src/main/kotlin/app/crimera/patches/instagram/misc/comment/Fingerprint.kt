/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.comment

import app.crimera.patches.instagram.utils.Constants.COMMENT_BUTTON_EXTENSION_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionFilter
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.newInstance
import app.morphe.patcher.opcode
import app.morphe.patcher.resource.ResourceType
import app.morphe.patcher.resourceLiteral
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

internal const val HANDLE_COMMENT_BUTTON_EXTENSION_CLASS = "${COMMENT_BUTTON_EXTENSION_CLASS}/HandleCommentButton;"

private const val COMMENT_ACTIONS_STRING = "instagram_share_comment_to_story_entrypoint_impression"

/**
 * Builds the comment action buttons, starting with the comment action list.
 */
internal object AddCommentButtonFingerprint : Fingerprint(
    returnType = "Ljava/util/ArrayList;",
    strings = listOf(COMMENT_ACTIONS_STRING),
    filters = listOf(
        newInstance("Ljava/util/ArrayList;"),
        methodCall(smali = "Ljava/util/ArrayList;-><init>()V", location = MatchAfterImmediately()),
        // Comment author, read from the comment.
        fieldAccess(type = "Lcom/instagram/user/model/User;", opcode = Opcode.IGET_OBJECT, location = MatchAfterImmediately()),
    ),
)

/**
 * Comment action button using the eye off drawable, and the action button bundle it creates.
 * Each added button is inserted before this button, so the match must be cleared before every use.
 */
internal object CommentActionButtonFingerprint : Fingerprint(
    returnType = "Ljava/util/ArrayList;",
    strings = listOf(COMMENT_ACTIONS_STRING),
    filters = listOf(
        // 0: Action of the existing button.
        fieldAccess(opcode = Opcode.SGET_OBJECT),
        methodCall(name = "areEqual", opcode = Opcode.INVOKE_STATIC, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        resourceLiteral(ResourceType.DRAWABLE, "instagram_eye_off_outline_24", location = MatchAfterWithin(3)),
        // 5: Button style.
        fieldAccess(opcode = Opcode.SGET_OBJECT),
        opcode(Opcode.NEW_INSTANCE, location = MatchAfterImmediately()),
        // 7: Button bundle.
        methodCall(name = "<init>", location = MatchAfterImmediately()),
        opcode(Opcode.GOTO, location = MatchAfterImmediately()),
    ),
)

internal object CommentButtonClickCheckFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("select_comment_screen_delete_comments_tap", "comment_share_click"),
    filters = listOf(
        // Selected comment buttons.
        methodCall(returnType = "Ljava/util/ArrayList;"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        methodCall(smali = "Ljava/util/AbstractCollection;->isEmpty()Z", location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterWithin(1)),
    ),
)

/**
 * Constructor of a native chat button, which passes its drawable and string resources to the superclass.
 */
private fun chatButtonConstructorFilters(action: InstructionFilter) = listOf(
    action,
    // Drawable.
    opcode(Opcode.CONST, location = MatchAfterImmediately()),
    // String.
    opcode(Opcode.CONST, location = MatchAfterImmediately()),
    opcode(Opcode.CONST_4, location = MatchAfterImmediately()),
    methodCall(name = "<init>", parameters = listOf("L", "I", "I", "Z"), location = MatchAfterImmediately()),
)

internal fun chatButtonConstructorFingerprint(buttonClass: String) = Fingerprint(
    definingClass = buttonClass,
    name = "<init>",
    filters = chatButtonConstructorFilters(fieldAccess(opcode = Opcode.SGET_OBJECT)),
)

internal fun chatButtonConstructorFingerprint(action: FieldReference) = Fingerprint(
    name = "<init>",
    parameters = emptyList(),
    filters = chatButtonConstructorFilters(fieldAccess(reference = action, opcode = Opcode.SGET_OBJECT)),
)
