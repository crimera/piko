package app.crimera.patches.newx.misc.customsharingdomain

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.settings.settingStrings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val newXPermanentShareLinksPatch =
    bytecodePatch(
        name = "NewX: Permanent share links",
        description = "Uses stable account and post IDs in shared links when enabled.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)

        newXToggle(
            id = "newx.links.permanent_share_links",
            category = Categories.LINKS,
            strings = settingStrings("piko_newx_permanent_share_links"),
            order = 510,
            defaultValue = false,
        )

        dependsOn(newXSharedUrlRewritePatch)
    }
