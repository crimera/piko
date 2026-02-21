package app.crimera.patches.twitter.misc.shareMenu.nativeShareImage

import app.crimera.patches.twitter.entity.entityGenerator
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.shareMenu.hooks.shareMenuButtonInjection
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val nativeShareImagePatch =
    bytecodePatch(
        name = "Share as Image",
        description = "Share tweets as rendered image. Requires X 11.0.0-release.0 or higher.",
    ) {
        compatibleWith("com.twitter.android")
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
