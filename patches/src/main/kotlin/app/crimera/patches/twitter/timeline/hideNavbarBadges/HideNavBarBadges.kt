package app.crimera.patches.twitter.timeline.hideNavbarBadges

import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.utils.Constants
import app.crimera.utils.enableSettings
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

private object setBadgeNumberFingerprint : Fingerprint(
    definingClass = "/BadgeableTabView;",
    name = "setBadgeNumber"
)

@Suppress("unused")
val hideNavBarBadgesPatch =
    bytecodePatch(
        name = "Hide badges from navigation bar icons",
        description = "Hides notification nudges & counts from navigation bar icons",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(settingsPatch)

        execute {
            setBadgeNumberFingerprint.method.apply {
                addInstructions(
                    0,
                    """
                    invoke-static {p1}, ${Constants.PREF_DESCRIPTOR};->navbarBadgeCount(I)I
                    move-result p1
                    """.trimIndent(),
                )
                SettingsStatusLoadFingerprint.enableSettings("hideNavbarBadge")
            }
        }
    }
