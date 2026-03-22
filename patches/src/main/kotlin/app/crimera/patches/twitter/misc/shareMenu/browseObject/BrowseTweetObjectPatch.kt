/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.misc.shareMenu.browseObject

import app.crimera.patches.twitter.entity.entityGenerator
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.shareMenu.hooks.shareMenuButtonInjection
import app.crimera.patches.twitter.shared.Constants.COMPATIBILITY_X
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val browseObjectPatch =
    bytecodePatch(
        name = "Browse tweet object",
        description = "Adds an option to browse the tweet object in the share menu.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_X)
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
