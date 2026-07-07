/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.utsavrajput.patches.twitter.misc.shareMenu.nativeDownloader

import app.utsavrajput.patches.twitter.entity.entityGenerator
import app.utsavrajput.patches.twitter.misc.settings.settingsPatch
import app.utsavrajput.patches.twitter.misc.shareMenu.hooks.shareMenuButtonInjection
import app.utsavrajput.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.utsavrajput.patches.twitter.utils.versionCheckPatch
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val nativeDownloaderPatch =
    bytecodePatch(
        name = "Native downloader",
        description = "Requires X 11.0.0-release.0 or higher.",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, entityGenerator, inlineDownloadButtonPatch, versionCheckPatch)

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
