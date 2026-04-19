/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.utils.Constants.DOWNLOAD_DESCRIPTOR
import app.morphe.patcher.Fingerprint
import app.morphe.shared.misc.mapping.ResourceType
import app.morphe.shared.misc.mapping.resourceLiteral

val FEED_BUTTON_DESCRIPTOR = "$DOWNLOAD_DESCRIPTOR/FeedButton;"
val REEL_BUTTON_DESCRIPTOR = "$DOWNLOAD_DESCRIPTOR/ReelButton;"

internal object AddFeedButtonFingerprint : Fingerprint(
    strings = listOf("TEXT_POST_APP_INACTIVE"),
)

internal object FeedButtonOnClickFingerprint : Fingerprint(
    parameters = listOf("Lcom/instagram/feed/media/mediaoption/MediaOption\$Option;"),
    strings = listOf("MediaOptionsOverflowHelper"),
    returnType = "V",
)

internal object EditMediaInfoFragmentFingerprint : Fingerprint(
    definingClass = "Linstagram/features/creation/fragment/EditMediaInfoFragment;",
    returnType = "V",
    parameters = listOf(),
    strings = listOf("Required value was null."),
)

internal object AddReelButtonFingerprint : Fingerprint(
    strings = listOf("ClipsOrganicMediaItemViewMoreOptionsController"),
)

internal object EnumButtonClassFingerprint : Fingerprint(
    strings = listOf("UNSET_OR_UNRECOGNIZED_ENUM_VALUE", "ACTION", "DESTRUCTIVE"),
)

internal object GetDirectThreadMediaSaverModuleNameFingerprint : Fingerprint(
    strings = listOf("DirectThreadMediaSaver"),
    name = "getModuleName",
    returnType = "Ljava/lang/String;",
)

internal object DMLongPressButtonAdderFingerprint : Fingerprint(
    returnType = "V",
    filters =
        listOf(
            resourceLiteral(ResourceType.DRAWABLE, "instagram_sticker_outline_24"),
            resourceLiteral(ResourceType.DRAWABLE, "instagram_reply_outline_24"),
            resourceLiteral(ResourceType.DRAWABLE, "instagram_meta_gen_ai_outline_24"),
        ),
)

internal object GetEnumButtonClassExtensionFingerprint : Fingerprint(
    definingClass = FEED_BUTTON_DESCRIPTOR,
    name = "getEnumButtonClass",
)

internal object MediaOptionsOverflowMenuCreatorConstructorFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("MediaOptionsOverflowMenuCreator"),
)

internal object AddFeedButtonExtensionFingerprint : Fingerprint(
    definingClass = FEED_BUTTON_DESCRIPTOR,
    name = "addButton",
)

internal object FeedReplaceAudioDialogHelperFingerprint : Fingerprint(
    strings = listOf("FeedReplaceAudioDialogHelper"),
)

internal object AddReelButtonExtensionFingerprint : Fingerprint(
    definingClass = REEL_BUTTON_DESCRIPTOR,
    name = "addReelButton",
)
