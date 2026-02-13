package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.settingsStatusLoadFingerprint
import app.crimera.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideTimelinePostByCategory =
    bytecodePatch(
        name = "Hide timeline posts by category",
        description = "Hides different post category like who to follow, news today etc from timeline.",
    ) {
        compatibleWith("com.twitter.android")
        dependsOn(timelineEntryHookPatch, settingsPatch)
        execute {
            // Pinned posts by followers.
            settingsStatusLoadFingerprint.enableSettings("hideRevistPinnedPost")

            // Communities to join.
            settingsStatusLoadFingerprint.enableSettings("hideCommToJoin")

            // Creators to subscribe.
            settingsStatusLoadFingerprint.enableSettings("hideCreatorsToSub")

            // Detailed posts.
            settingsStatusLoadFingerprint.enableSettings("hideDetailedPost")

            // Premium prompt.
            settingsStatusLoadFingerprint.enableSettings("hidePremiumPrompt")

            // Revisit bookmarks.
            settingsStatusLoadFingerprint.enableSettings("hideRevistBookmark")

            // Today's news.
            settingsStatusLoadFingerprint.enableSettings("hideTodaysNews")

            // Top people in search.
            settingsStatusLoadFingerprint.enableSettings("hideTopPeopleSearch")

            // Who to follow.
            settingsStatusLoadFingerprint.enableSettings("hideWhoToFollow")
        }
    }
