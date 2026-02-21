package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideTimelineItemsPatch =
    bytecodePatch(
        name = "Hide timeline items",
        description = "Adds options to hide various items such as \"Who to follow\" and \"Today's news\" in timeline, search, and replies.",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(timelineEntryHookPatch, settingsPatch)
        execute {
            // Pinned posts by followers.
            SettingsStatusLoadFingerprint.enableSettings("hideRevistPinnedPost")

            // Communities to join.
            SettingsStatusLoadFingerprint.enableSettings("hideCommToJoin")

            // Creators to subscribe.
            SettingsStatusLoadFingerprint.enableSettings("hideCreatorsToSub")

            // Detailed posts.
            SettingsStatusLoadFingerprint.enableSettings("hideDetailedPost")

            // Premium prompt.
            SettingsStatusLoadFingerprint.enableSettings("hidePremiumPrompt")

            // Revisit bookmarks.
            SettingsStatusLoadFingerprint.enableSettings("hideRevistBookmark")

            // Today's news.
            SettingsStatusLoadFingerprint.enableSettings("hideTodaysNews")

            // Top people in search.
            SettingsStatusLoadFingerprint.enableSettings("hideTopPeopleSearch")

            // Who to follow.
            SettingsStatusLoadFingerprint.enableSettings("hideWhoToFollow")
        }
    }
