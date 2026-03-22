/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * This file is part of piko.
 *
 * Any modifications, derivatives, or substantial rewrites of this file
 * must retain this copyright notice and the piko attribution 
 * in the source code and version control history.
 */

package app.crimera.patches.twitter.ads.timelineEntryHook

import app.crimera.patches.twitter.misc.settings.settingsPatch
import app.crimera.patches.twitter.misc.settings.SettingsStatusLoadFingerprint
import app.crimera.patches.twitter.shared.Constants.COMPATIBILITY_X
import app.crimera.utils.enableSettings
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val hideTimelinePostByCategory =
    bytecodePatch(
        name = "Hide timeline posts by category",
        description = "Hides different post category like who to follow, news today etc from timeline.",
    ) {
        compatibleWith(COMPATIBILITY_X)
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
