package app.crimera.patches.twitter.misc.shareMenu.nativeTranslator

import app.crimera.patches.twitter.entity.entityGenerator
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.shareMenu.hooks.*
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val nativeTranslatorModePatch =
    bytecodePatch(
        name = "Custom translator",
        description = "Requires X 11.0.0-release.0 or higher.",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch, entityGenerator)

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
