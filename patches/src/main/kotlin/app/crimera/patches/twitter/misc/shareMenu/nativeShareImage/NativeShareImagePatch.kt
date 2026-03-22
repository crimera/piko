/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.misc.shareMenu.nativeShareImage

import app.crimera.patches.twitter.entity.entityGenerator
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.shareMenu.hooks.shareMenuButtonInjection
import app.crimera.patches.twitter.shared.Constants.COMPATIBILITY_X
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val nativeShareImagePatch =
    bytecodePatch(
        name = "Share Tweet as Image",
        description = "Share tweets as rendered image. Requires X 11.0.0-release.0 or higher.",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, entityGenerator)

        execute {
            val actionName = "ShareImage"
            val prefFunctionName = "enableShareImage"
            val stringId = "piko_share_image_title"
            val iconId = "ic_vector_share"
            val functionReference = "/shareImage/ShareImageHandler;->shareAsImage"
            val statusFunctionName = "shareImage"

            shareMenuButtonInjection(
                actionName,
                prefFunctionName,
                stringId,
                iconId,
                functionReference,
                statusFunctionName
            )
        }
    }
