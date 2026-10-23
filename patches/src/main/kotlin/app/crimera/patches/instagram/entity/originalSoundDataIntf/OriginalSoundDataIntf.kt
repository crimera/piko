/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.originalSoundDataIntf

import app.crimera.patches.instagram.utils.methodReference
import app.crimera.utils.changeFirstString
import app.morphe.patcher.patch.bytecodePatch

val originalSoundDataIntfEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of Original sound data interface",
    ) {
        execute {
            OriginalSoundMapperFingerprint.instructionMatches.let {
                GetAudioIdExtension.changeFirstString(it[1].methodReference.name)
                GetUserDataExtension.changeFirstString(it[3].methodReference.name)
                GetAudioNameExtension.changeFirstString(it[6].methodReference.name)
                GetAudioUrlExtension.changeFirstString(it[8].methodReference.name)
            }
        }
    }
