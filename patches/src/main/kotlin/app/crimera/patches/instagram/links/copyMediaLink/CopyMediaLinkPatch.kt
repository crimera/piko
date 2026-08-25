/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.copyMediaLink

import app.crimera.patches.instagram.misc.overflowMenuButton.posts.addOverflowMenuButtonAttributes
import app.crimera.patches.instagram.misc.overflowMenuButton.posts.debugOverflowButton.debugOverflowMenuButtonPatch
import app.crimera.patches.instagram.misc.overflowMenuButton.posts.hookOverflowMenuButton
import app.crimera.patches.instagram.misc.overflowMenuButton.reels.hookReelOverflowMenuButton
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val copyMediaLinkPatch =
    bytecodePatch(
        name = "Copy media link",
        description = "Adds a button to copy the direct media link (CDN URL) of posts and reels to the clipboard",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch, hookOverflowMenuButton, debugOverflowMenuButtonPatch, hookReelOverflowMenuButton)
        execute {

            addOverflowMenuButtonAttributes("PIKO_COPY_MEDIA_LINK", "copyMediaLinkOverflowButton")

            enableSettings("copyMediaLink")
        }
    }
