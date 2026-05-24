/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.shareMenu.browseObject

import app.crimera.patches.twitter.entity.entityGenerator
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.shareMenu.hooks.shareMenuButtonInjection
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.versionCheckPatch
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val browseObjectPatch =
    bytecodePatch(
        name = "Browse tweet object",
        description = "Adds an option to browse the tweet object in the share menu.",
        default = false,
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, entityGenerator, versionCheckPatch)

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
