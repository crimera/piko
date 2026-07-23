/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.premium.unlockdownloads

import app.morphe.patcher.Fingerprint

internal object XLiteDownloadEventHandlerFingerprint : Fingerprint(
    strings =
        listOf(
            "download_video_to_offline",
            "load_highest_quality",
            "video_download",
        ),
)

internal object SubscriptionsFeaturesHasAnyPremiumFingerprint : Fingerprint(
    strings =
        listOf(
            "feature/twitter_blue",
            "feature/premium_basic",
            "feature/twitter_blue_verified",
            "feature/premium_plus",
        ),
)

internal object MediaContentVideoIsDownloadableFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/MediaContent\$MediaContentVideo;",
    name = "isDownloadable",
    returnType = "Z",
)

internal object MediaContentGifIsDownloadableFingerprint : Fingerprint(
    definingClass = "Lcom/x/models/MediaContent\$MediaContentGif;",
    name = "isDownloadable",
    returnType = "Z",
)
