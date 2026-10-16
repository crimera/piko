/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.entity.mediadata.GetVideoDashManifestExtensionFingerprint
import app.crimera.patches.instagram.entity.mediadata.HasAudioGetterFingerprint
import app.crimera.patches.instagram.entity.mediadata.HasAudioTrackExtensionFingerprint
import app.crimera.patches.instagram.entity.mediadata.VideoDashManifestGetterFingerprint
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.crimera.utils.changeFirstString
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val bestQualityDownloadPatch =
    bytecodePatch(
        name = "Best quality downloads",
        description = "Downloads videos as the sharpest rendition Instagram lists, up to 1080p AV1",
    ) {
        dependsOn(downloadMediaPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            GetVideoDashManifestExtensionFingerprint.changeFirstString(VideoDashManifestGetterFingerprint.originalMethod.name)
            HasAudioTrackExtensionFingerprint.changeFirstString(HasAudioGetterFingerprint.originalMethod.name)
            enableSettings("bestQualityDownload")
        }
    }
