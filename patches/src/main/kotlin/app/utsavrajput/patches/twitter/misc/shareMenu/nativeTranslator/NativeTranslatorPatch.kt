/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.utsavrajput.patches.twitter.misc.shareMenu.nativeTranslator

import app.utsavrajput.patches.twitter.entity.entityGenerator
import app.utsavrajput.patches.twitter.misc.settings.settingsPatch
import app.utsavrajput.patches.twitter.misc.shareMenu.hooks.*
import app.utsavrajput.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.utsavrajput.patches.twitter.utils.versionCheckPatch
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val nativeTranslatorModePatch =
    bytecodePatch(
        name = "Native translator",
        description = "Requires X 11.0.0-release.0 or higher.",
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, entityGenerator, versionCheckPatch)

        execute {
            val actionName = "Translate"
            val prefFunctionName = "enableNativeTranslator"
            val stringId = "translate_tweet_show"
            val iconId = "ic_vector_sparkle"
            val functionReference = "/translator/NativeTranslator;->translate"
            val statusFunctionName = "nativeTranslator"
            shareMenuButtonInjection(actionName, prefFunctionName, stringId, iconId, functionReference, statusFunctionName)
        }
    }
