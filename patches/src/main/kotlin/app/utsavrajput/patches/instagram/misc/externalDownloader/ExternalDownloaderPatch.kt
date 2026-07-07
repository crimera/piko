/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.utsavrajput.patches.instagram.misc.externalDownloader

import app.utsavrajput.patches.instagram.misc.overflowMenuButton.posts.addOverflowMenuButtonAttributes
import app.utsavrajput.patches.instagram.misc.overflowMenuButton.posts.debugOverflowButton.debugOverflowMenuButtonPatch
import app.utsavrajput.patches.instagram.misc.overflowMenuButton.posts.hookOverflowMenuButton
import app.utsavrajput.patches.instagram.misc.overflowMenuButton.reels.hookReelOverflowMenuButton
import app.utsavrajput.patches.instagram.misc.settings.settingsPatch
import app.utsavrajput.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.utsavrajput.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val externalDownloaderPatch =
    bytecodePatch(
        name = "External downloader",
        description = "Adds support to share post links directly to external downloader",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, hookOverflowMenuButton, debugOverflowMenuButtonPatch, hookReelOverflowMenuButton)
        execute {

            addOverflowMenuButtonAttributes("PIKO_EXTERNAL_DOWNLOADER", "externalDownloaderOverflowButton")

            enableSettings("downloadWithExternalDownloader")
        }
    }
