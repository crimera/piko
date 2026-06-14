/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.videoData

import app.crimera.patches.instagram.utils.Constants.ENTITY_CLASS
import app.morphe.patcher.Fingerprint

internal const val EXTENSION_CLASS_DESCRIPTOR = "$ENTITY_CLASS/VideoData;"

internal object ImmutablePandoVideoVersionMapExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "immutablePandoVideoVersionMap",
)

internal object VideoVersionMapExtensionFingerprint : Fingerprint(
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
    name = "videoVersionMap",
)

internal object ImmutablePandoVideoVersionMapperFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/model/mediasize/ImmutablePandoVideoVersion;",
    returnType = "Ljava/util/Map;",
)

internal object VideoVersionMapperFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/model/mediasize/VideoVersion;",
    returnType = "Ljava/util/Map;",
)
