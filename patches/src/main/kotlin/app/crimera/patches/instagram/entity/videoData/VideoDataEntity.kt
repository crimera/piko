/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.videoData

import app.crimera.patches.instagram.utils.methodReference
import app.crimera.utils.changeFirstString
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.matchSingle

val videoDataEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of Video data",
    ) {
        execute {
            VideoVersionMapperFingerprint.matchSingle().instructionMatches.let {
                VideoHeightExtensionFingerprint.changeFirstString(it[1].methodReference.name)
                VideoCodecExtensionFingerprint.changeFirstString(it[3].methodReference.name)
                VideoUrlExtensionFingerprint.changeFirstString(it[5].methodReference.name)
                VideoWidthExtensionFingerprint.changeFirstString(it[7].methodReference.name)
            }
        }
    }
