/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links

import app.crimera.patches.instagram.links.shareLinks.ShareLinkKind.AUDIO
import app.crimera.patches.instagram.links.shareLinks.ShareLinkKind.HIGHLIGHT
import app.crimera.patches.instagram.links.shareLinks.ShareLinkKind.PROFILE
import app.crimera.patches.instagram.links.shareLinks.hookShareLinks
import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val customSharingDomainPatch =
    bytecodePatch(
        name = "Custom sharing domain",
        description = "Allows for using custom domains when sharing posts, reels and stories.",
    ) {

        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            hookShareLinks("changeDomain", skip = setOf(PROFILE, AUDIO, HIGHLIGHT))

            enableSettings("customSharingDomain")
        }
    }
