/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.shareMenu.nativeShareMenu

import app.crimera.patches.twitter.entity.entityGenerator
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.shareMenu.hooks.shareMenuButtonOnClickHook
import app.crimera.patches.twitter.utils.Constants.COMPATIBILITY_X
import app.crimera.patches.twitter.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val nativeShareMenuPatch =
    bytecodePatch(
        name = "Custom share menu",
        description = "Overrides existing share menu and adds a new one",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_X)
        dependsOn(settingsPatch, entityGenerator, shareMenuButtonOnClickHook)

        execute {

            enableSettings("enableNativeShareMenu")
        }
    }
