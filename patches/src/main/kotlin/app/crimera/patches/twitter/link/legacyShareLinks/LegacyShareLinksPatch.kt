/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.link.legacyShareLinks

import app.crimera.patches.twitter.link.handlemodernsharesheetlinks.handleModernShareSheetLinks
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val legacyShareLinksPatch =
    bytecodePatch(
        name = "Legacy share links",
        description = "Brings back username on post share links. Works post 11.4x.xx",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, handleModernShareSheetLinks)
        execute {

            enableSettings("legacyShareLink")
        }
    }
