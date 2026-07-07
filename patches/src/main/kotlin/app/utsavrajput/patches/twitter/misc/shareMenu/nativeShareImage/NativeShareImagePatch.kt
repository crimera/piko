/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.utsavrajput.patches.twitter.misc.shareMenu.nativeShareImage

import app.utsavrajput.patches.twitter.entity.entityGenerator
import app.utsavrajput.patches.twitter.misc.settings.settingsPatch
import app.utsavrajput.patches.twitter.misc.shareMenu.hooks.shareMenuButtonInjection
import app.utsavrajput.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.utsavrajput.patches.twitter.utils.versionCheckPatch
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val nativeShareImagePatch =
    bytecodePatch(
        name = "Share Tweet as Image",
        description = "Share tweets as rendered image. Requires X 11.0.0-release.0 or higher.",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, entityGenerator, versionCheckPatch)

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
                statusFunctionName,
            )
        }
    }
