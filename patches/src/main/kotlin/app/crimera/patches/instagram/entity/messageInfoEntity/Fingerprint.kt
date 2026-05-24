/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.messageInfoEntity

import app.crimera.patches.instagram.utils.Constants.ENTITY_CLASS
import app.morphe.patcher.Fingerprint

internal const val EXTENSION_CLASS_DESCRIPTOR = "$ENTITY_CLASS/MessageInfo;"

internal object GetAudioMediaExtension : Fingerprint(
    name = "getAudioMedia",
    definingClass = EXTENSION_CLASS_DESCRIPTOR,
)

internal object StellaDirectMessagingServiceAudioRelatedFingerprint : Fingerprint(
    strings = listOf("prepare audio source link"),
)
