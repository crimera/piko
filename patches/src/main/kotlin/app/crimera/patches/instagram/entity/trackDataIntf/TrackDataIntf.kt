/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.trackDataIntf

import app.crimera.patches.instagram.utils.methodReference
import app.crimera.utils.changeFirstString
import app.morphe.patcher.patch.bytecodePatch

val trackDataIntfEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of track data interface",
    ) {
        execute {
            GetTrackDataExtension.changeFirstString(TrackDataFromMusicInfoMethodFingerprint.originalMethod.name)
            GetMappingsExtension.changeFirstString(
                AudioFilterInfoTreeUpdaterFingerprint.instructionMatches.first().methodReference.name,
            )
        }
    }
