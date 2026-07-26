/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.onboarding

import app.crimera.patches.instagram.misc.settings.settingsPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.addFlags
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val disableOnboardingPermissionPromptsPatch =
    bytecodePatch(
        name = "Disable onboarding permission prompts",
        description = "Prevents contacts and location permission onboarding prompts from appearing on launch.",
        default = true,
    ) {
        dependsOn(settingsPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {
            addFlags("onboardingPermissionPromptFlags")
        }
    }
