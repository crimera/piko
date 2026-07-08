/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.twitter.misc.moreProfileInfo

import app.morphe.patcher.patch.resourcePatch
import app.morphe.patches.all.misc.resources.resourceMappingPatch
import app.morphe.util.findElementByAttributeValueOrThrow

internal val fieldList = listOf("fast_follower_stat", "tweet_stat", "article_stat", "media_stat", "likes_stat")

internal val moreProfileInfoResourcePatch =
    resourcePatch {
        dependsOn(resourceMappingPatch)
        execute {
            document("res/layout/profile_details.xml").use { editor ->
                val statsContainer =
                    editor.childNodes.findElementByAttributeValueOrThrow(
                        "android:id",
                        "@id/stats_container",
                    )

                fieldList.forEach {
                    val stat = editor.createElement("com.twitter.ui.tweet.TweetStatView")
                    stat.setAttribute("android:id", "@+id/$it")
                    stat.setAttribute("android:visibility", "gone")
                    stat.setAttribute("android:layout_width", "wrap_content")
                    stat.setAttribute("android:layout_height", "fill_parent")
                    stat.setAttribute("style", "@style/ProfileStatView")

                    statsContainer.appendChild(stat)
                }
            }
        }
    }
