/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.overflowMenuButton.posts

import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.misc.download.FeedButtonOnClickFingerprint
import app.crimera.patches.instagram.misc.download.MediaOptionsOverflowMenuCreatorConstructorFingerprint
import app.crimera.patches.instagram.utils.Constants.ADD_REEL_BTN_OVERFLOW_MENU_BUTTON_CLASS
import app.crimera.patches.instagram.utils.Constants.FEED_OVERFLOW_MENU_BUTTON_CLASS
import app.crimera.patches.instagram.utils.Constants.MEDIA_OPTIONS_CLASS
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.checkCast
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.newInstance
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

internal object OverflowMenuButtonEnumInitialiser : Fingerprint(
    definingClass = MEDIA_OPTIONS_CLASS,
    name = "<clinit>",
    filters = listOf(
        fieldAccess(definingClass = "this", name = "\$VALUES", type = "[$MEDIA_OPTIONS_CLASS", opcode = Opcode.SPUT_OBJECT),
    ),
)

internal object EnumButtonClassFingerprint : Fingerprint(
    strings = listOf("UNSET_OR_UNRECOGNIZED_ENUM_VALUE", "ACTION", "DESTRUCTIVE"),
)

internal fun feedMenuListFingerprint(creatorType: String, menuModelType: String) = Fingerprint(
    strings = listOf("TEXT_POST_APP_INACTIVE"),
    filters = listOf(
        fieldAccess(type = creatorType, opcode = Opcode.IGET_OBJECT),
        newInstance(menuModelType),
        methodCall(name = "<init>", location = MatchAfterImmediately()),
        methodCall(returnType = "Ljava/util/ArrayList;", parameters = emptyList(), opcode = Opcode.INVOKE_STATIC, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
    ),
)

/**
 * Adds a native option to the feed overflow menu.
 */
internal object AddFeedOverflowOptionFingerprint : Fingerprint(
    classFingerprint = MediaOptionsOverflowMenuCreatorConstructorFingerprint,
    parameters = listOf("L", MEDIA_OPTIONS_CLASS, "L", "L", "Ljava/lang/CharSequence;", "Ljava/util/ArrayList;", "Z"),
)

/**
 * Media of the feed overflow menu.
 */
internal object FeedButtonGetMediaFingerprint : Fingerprint(
    classFingerprint = FeedButtonOnClickFingerprint,
    parameters = listOf("L"),
    returnType = MEDIA_CLASS_NAME,
    filters = listOf(
        fieldAccess(definingClass = "this", opcode = Opcode.IGET_OBJECT),
        methodCall(name = "getValue", parameters = emptyList(), returnType = "Ljava/lang/Object;", opcode = Opcode.INVOKE_INTERFACE, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately()),
        checkCast(MEDIA_CLASS_NAME, location = MatchAfterImmediately()),
        opcode(Opcode.RETURN_OBJECT, location = MatchAfterImmediately()),
    ),
)

internal object GetEnumButtonClassExtensionFingerprint : Fingerprint(
    definingClass = FEED_OVERFLOW_MENU_BUTTON_CLASS,
    name = "getEnumButtonClass",
)

internal object AddFeedButtonExtensionFingerprint : Fingerprint(
    definingClass = FEED_OVERFLOW_MENU_BUTTON_CLASS,
    name = "addButton",
)

internal object FeedReplaceAudioDialogHelperFingerprint : Fingerprint(
    filters = listOf(
        string("FeedReplaceAudioDialogHelper"),
        // Replace audio option listener.
        opcode(Opcode.INVOKE_DIRECT_RANGE),
        // Adds the replace audio option.
        methodCall(
            parameters = listOf("Landroid/content/Context;", "Landroid/view/View\$OnClickListener;", "Ljava/lang/String;", "I"),
            returnType = "V",
            location = MatchAfterImmediately(),
        ),
    ),
)

internal object AddReelButtonExtensionFingerprint : Fingerprint(
    definingClass = ADD_REEL_BTN_OVERFLOW_MENU_BUTTON_CLASS,
    name = "addReelButton",
)
