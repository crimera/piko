/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.videoData

import app.crimera.utils.changeFirstString
import app.morphe.patcher.patch.bytecodePatch

val videoDataEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of Video data",
    ) {
        execute {

            ImmutablePandoVideoVersionMapExtensionFingerprint.changeFirstString(ImmutablePandoVideoVersionMapperFingerprint.method.name)

            VideoVersionMapExtensionFingerprint.changeFirstString(VideoVersionMapperFingerprint.method.name)
        }
    }
