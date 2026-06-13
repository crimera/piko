/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.overflowMenuButton.posts

import app.crimera.patches.instagram.utils.Constants.ADD_REEL_BTN_OVERFLOW_MENU_BUTTON_CLASS
import app.crimera.patches.instagram.utils.Constants.FEED_OVERFLOW_MENU_BUTTON_CLASS
import app.crimera.patches.instagram.utils.Constants.MEDIA_OPTIONS_CLASS
import app.morphe.patcher.Fingerprint

internal object OverflowMenuButtonEnumInitialiser : Fingerprint(
    definingClass = MEDIA_OPTIONS_CLASS,
    name = "<clinit>",
)

internal object EnumButtonClassFingerprint : Fingerprint(
    strings = listOf("UNSET_OR_UNRECOGNIZED_ENUM_VALUE", "ACTION", "DESTRUCTIVE"),
)

internal object AddFeedButtonFingerprint : Fingerprint(
    strings = listOf("TEXT_POST_APP_INACTIVE"),
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
    strings = listOf("FeedReplaceAudioDialogHelper"),
)

internal object AddReelButtonExtensionFingerprint : Fingerprint(
    definingClass = ADD_REEL_BTN_OVERFLOW_MENU_BUTTON_CLASS,
    name = "addReelButton",
)
