/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution
 * in the source code and version control history.
 */

package app.crimera.patches.instagram.entity.trackDataIntf

import app.crimera.patches.instagram.utils.Constants
import app.morphe.patcher.Fingerprint

internal const val EXTENSION_CLASS_DESCRIPTOR = "${Constants.ENTITY_CLASS}/TrackDataIntf;"
internal const val IMMUTABLE_PANDO_AUDIO_FILTER_INFO_CLASS_DESCRIPTOR = "Lcom/instagram/api/schemas/ImmutablePandoAudioFilterInfo;"

internal object GetTrackDataExtension : Fingerprint(
    name = "getTrackData",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object GetMappingsExtension : Fingerprint(
    name = "getMappings",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object TrackDataFromMusicInfoMethodFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/api/schemas/MusicInfo",
    returnType = "Lcom/instagram/api/schemas/TrackData;",
)
