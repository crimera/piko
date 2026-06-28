/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.shareMenu.externalDownloader

import app.crimera.patches.twitter.entity.entityGenerator
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.shareMenu.hooks.shareMenuButtonInjection
import app.crimera.patches.twitter.misc.shareMenu.hooks.shareMenuButtonOnClickHook
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val externalDownloaderPatch =
    bytecodePatch(
        name = "Support external downloader",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, entityGenerator, shareMenuButtonOnClickHook)

        execute {
            val actionName = "ExternalDownload"
            val prefFunctionName = "enableExternalDownloader"
            val stringId = "piko_pref_external_downloader_text"
            val iconId = "ic_vector_incoming"
            val statusFunctionName = "externalDownloader"
            shareMenuButtonInjection(actionName, prefFunctionName, stringId, iconId, statusFunctionName)
        }
    }
