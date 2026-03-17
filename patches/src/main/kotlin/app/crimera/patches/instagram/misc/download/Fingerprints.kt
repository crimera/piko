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
import app.morphe.patcher.OpcodesFilter
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

val FEED_BUTTON_DESCRIPTOR = "$DOWNLOAD_DESCRIPTOR/FeedButton;"
val REEL_BUTTON_DESCRIPTOR = "$DOWNLOAD_DESCRIPTOR/ReelButton;"

internal object AddFeedButtonFingerprint : Fingerprint(
    strings = listOf("TEXT_POST_APP_INACTIVE"),
)

internal object FeedButtonOnClickFingerprint : Fingerprint(
    strings = listOf("click_media_option", "Required value was null."),
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

internal object AddingReelButtonMethodFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "V",
    parameters = listOf("Landroid/content/Context;", "Landroid/view/View\$OnClickListener;", "Ljava/lang/String;", "I"),
    filters =
        OpcodesFilter.opcodesToFilters(
            Opcode.CONST_4,
            Opcode.CONST_4,
            Opcode.CONST_HIGH16,
            Opcode.CONST_4,
        ),
)

internal object AddReelButtonExtensionFingerprint : Fingerprint(
    definingClass = REEL_BUTTON_DESCRIPTOR,
    name = "addReelButton",
)
