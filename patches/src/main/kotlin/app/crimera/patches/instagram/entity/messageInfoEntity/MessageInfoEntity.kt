/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.messageInfoEntity

import app.crimera.patches.instagram.entity.decoder.MEDIA_CLASS_NAME
import app.crimera.patches.instagram.utils.fieldReference
import app.crimera.utils.changeFirstString
import app.crimera.utils.changeStringAt
import app.morphe.patcher.patch.bytecodePatch

val messageInfoEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of message info",
    ) {
        execute {
            val voiceMediaField = StellaDirectMessagingServiceAudioRelatedFingerprint.instructionMatches.last().fieldReference
            GetAudioMediaExtension.changeFirstString(voiceMediaField.name)
            GetAudioMediaExtension.changeStringAt(
                1,
                classDefBy(voiceMediaField.type).fields.first { it.type == MEDIA_CLASS_NAME }.name,
            )
        }
    }
