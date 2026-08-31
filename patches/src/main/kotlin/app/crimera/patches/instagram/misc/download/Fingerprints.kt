/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.utils.Constants.DOWNLOAD_DESCRIPTOR
import app.morphe.patcher.Fingerprint

internal object FeedButtonOnClickFingerprint : Fingerprint(
    parameters = listOf("Lcom/instagram/feed/media/mediaoption/MediaOption\$Option;"),
    strings = listOf("MediaOptionsOverflowHelper"),
    returnType = "V",
)

internal object AddReelButtonFingerprint : Fingerprint(
    strings = listOf("ClipsOrganicMediaItemViewMoreOptionsController", "reels"),
)

internal object GetDirectThreadMediaSaverModuleNameFingerprint : Fingerprint(
    strings = listOf("DirectThreadMediaSaver"),
    name = "getModuleName",
    returnType = "Ljava/lang/String;",
)

internal object MediaOptionsOverflowMenuCreatorConstructorFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("MediaOptionsOverflowMenuCreator"),
)
