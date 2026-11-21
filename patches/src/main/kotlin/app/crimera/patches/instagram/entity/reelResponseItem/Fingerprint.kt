/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.reelResponseItem

import app.crimera.patches.instagram.utils.Constants
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.methodCall
import app.morphe.patcher.literal

internal const val EXTENSION_CLASS_DESCRIPTOR = "${Constants.ENTITY_CLASS}/ReelResponseItem;"

internal object ReelMediaCountFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/model/reels/ImmutablePandoReelResponseItem;",
    returnType = "Ljava/lang/Integer;",
    parameters = emptyList(),
    filters = listOf(
        literal("media_count".hashCode().toLong()),
        methodCall(name = "getOptionalIntValueByHashCode", location = MatchAfterImmediately()),
    ),
)

internal object ReelTypeEnumInitFingerprint : Fingerprint(
    name = "<clinit>",
    strings =
        listOf("UNSET_OR_UNRECOGNIZED_ENUM_VALUE", "ads_reel", "ar_effect_preview", "archive_day_media_reel"),
)

internal object GetReelTypeExtensionFingerprint : Fingerprint(
    name = "getReelType",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetUserDataExtensionFingerprint : Fingerprint(
    name = "getUserData",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetMediaCountExtensionFingerprint : Fingerprint(
    name = "getMediaCount",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)
