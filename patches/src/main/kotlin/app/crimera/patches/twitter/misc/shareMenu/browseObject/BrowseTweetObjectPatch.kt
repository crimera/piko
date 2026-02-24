package app.crimera.patches.twitter.misc.shareMenu.browseObject

import app.crimera.patches.twitter.entity.entityGenerator
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.shareMenu.hooks.shareMenuButtonInjection
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val browseObjectPatch =
    bytecodePatch(
        name = "Browse tweet object",
        description = "Adds an option to browse the tweet object in the share menu.",
        use = false,
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch, entityGenerator)

        execute {
            val actionName = "BrowseObject"
            val prefFunctionName = "browseObject"
            val stringId = "piko_browse_object_title"
            val iconId = "ic_vector_flask_stroke"
            val functionReference = "/browse/BrowseTweetObjectPatch;->browse"
            val statusFunctionName = "browseObject"
            shareMenuButtonInjection(actionName, prefFunctionName, stringId, iconId, functionReference, statusFunctionName)
        }
    }
