/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.originalSoundDataIntf

import app.crimera.patches.instagram.utils.Constants
import app.morphe.patcher.Fingerprint

internal const val EXTENSION_CLASS_DESCRIPTOR = "${Constants.ENTITY_CLASS}/OriginalSoundDataIntf;"

internal object GetUserDataExtension : Fingerprint(
    name = "getUserData",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetAudioIdExtension : Fingerprint(
    name = "getAudioId",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetAudioNameExtension : Fingerprint(
    name = "getAudioName",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetAudioUrlExtension : Fingerprint(
    name = "getAudioUrl",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object OriginalSoundMapperFingerprint : Fingerprint(
    returnType = "Ljava/util/Map;",
    strings = listOf("audio_asset_id", "hide_remixing", "original_audio_title", "progressive_download_url"),
)
