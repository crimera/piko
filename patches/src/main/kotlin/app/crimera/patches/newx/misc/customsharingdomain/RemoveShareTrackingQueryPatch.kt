package app.crimera.patches.newx.misc.customsharingdomain

import app.crimera.patches.newx.settings.Categories
import app.crimera.patches.newx.settings.newXToggle
import app.crimera.patches.newx.utils.Constants.COMPATIBILITY_NEW_X
import app.crimera.patches.newx.utils.unsupportedOnMount
import app.crimera.patches.settings.settingStrings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val newXRemoveShareTrackingQueryPatch =
    bytecodePatch(
        name = "NewX: Remove share tracking parameters",
        description = "Adds a Links setting to remove the a= and s= attribution parameters from shared X URLs.",
    ) {
        unsupportedOnMount()
        compatibleWith(COMPATIBILITY_NEW_X)

        newXToggle(
            id = "newx.links.remove_share_tracking_query",
            category = Categories.LINKS,
            strings = settingStrings("piko_newx_remove_share_tracking_query"),
            order = 520,
            defaultValue = true,
        )

        dependsOn(newXSharedUrlRewritePatch)
    }
