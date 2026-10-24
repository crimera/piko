/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.reels

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

/**
 * Instagram gates the auto-scroll state behind `per_session_autoscroll_enabled`, which is true
 * by default and makes the app forget the user's choice on every restart. Turning that flag off
 * keeps the state the user left, without turning auto-scroll on for anyone who never enabled it.
 */
@Suppress("unused")
val rememberAutoScrollStatePatch =
    bytecodePatch(
        name = "Remember auto-scroll state",
        description = "Keeps Reels auto-scroll in the state you left it, instead of turning it off after Instagram restarts. Does not turn auto-scroll on by itself.",
        default = true,
    ) {
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(settingsPatch)

        execute {
            enableSettings("rememberAutoScrollState")
            addFlags("rememberAutoScrollStateFlags")
        }
    }
