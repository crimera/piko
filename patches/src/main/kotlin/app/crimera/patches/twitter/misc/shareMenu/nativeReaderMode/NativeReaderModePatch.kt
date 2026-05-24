/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.shareMenu.nativeReaderMode

import app.crimera.patches.twitter.entity.entityGenerator
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.shareMenu.hooks.*
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.versionCheckPatch
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val nativeReaderModePatch =
    bytecodePatch(
        name = "Native reader mode",
        description = "Requires X 11.0.0-release.0 or higher.",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(nativeReaderModeResourcePatch, settingsPatch, entityGenerator, versionCheckPatch)

        execute {
            val actionName = "ReaderMode"
            val prefFunctionName = "enableNativeReaderMode"
            val stringId = "piko_title_native_reader_mode"
            val iconId = "ic_vector_book_stroke_on"
            val functionReference = "/readerMode/ReaderModeUtils;->launchReaderMode"
            val statusFunctionName = "nativeReaderMode"
            shareMenuButtonInjection(actionName, prefFunctionName, stringId, iconId, functionReference, statusFunctionName)
        }
    }
