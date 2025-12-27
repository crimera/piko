package app.crimera.patches.twitter.misc.shareMenu.nativeDownloader

import app.crimera.patches.twitter.entity.entityGenerator
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.shareMenu.hooks.shareMenuButtonInjection
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val nativeDownloaderPatch =
    bytecodePatch(
        name = "Native downloader",
        description = "Requires X 11.0.0-release.0 or higher.",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch, entityGenerator)

        execute {
            val actionName = "Download"
            val prefFunctionName = "enableNativeDownloader"
            val stringId = "piko_pref_native_downloader_alert_title"
            val iconId = "ic_vector_incoming"
            val functionReference = "/downloader/NativeDownloader;->downloader"
            val statusFunctionName = "nativeDownloader"
            shareMenuButtonInjection(actionName, prefFunctionName, stringId, iconId, functionReference, statusFunctionName)
        }
    }
