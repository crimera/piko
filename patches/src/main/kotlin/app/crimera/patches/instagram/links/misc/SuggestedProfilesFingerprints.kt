/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.links.misc

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

internal const val FOLLOW_LIST_SUGGESTIONS = "FollowListFragment.ShouldFetchSuggestedUsers"
internal const val FOLLOW_REQUEST_SUGGESTIONS = "ARG_SHOW_SUGGESTED_USERS"

internal object MutualFollowersResponseFingerprint : Fingerprint(
    name = "unsafeParseFromJson",
    returnType = "Ljava/lang/Object;",
    strings = listOf("mutual_followers", "suggested_users", "show_see_all_followers_button"),
)

internal object FollowListSuggestionsFingerprint : Fingerprint(
    name = "onCreate",
    returnType = "V",
    strings = listOf(FOLLOW_LIST_SUGGESTIONS),
)

internal object FollowRequestSuggestionsFingerprint : Fingerprint(
    name = "invoke",
    returnType = "Ljava/lang/Object;",
    strings = listOf(FOLLOW_REQUEST_SUGGESTIONS, "ARG_HIDE_APPROVE_BUTTON"),
)

internal object FriendingCenterResponseFingerprint : Fingerprint(
    returnType = "Ljava/lang/Object;",
    strings = listOf("IGFriendingCenterQuery", "isTopCategoryFullyExpanded", "downranked_ids"),
)

internal object FriendingCenterCategoryFingerprint : Fingerprint(
    returnType = "Ljava/lang/Integer;",
    parameters = listOf("Ljava/lang/String;"),
    strings = listOf("suggested_users", "follow_requests", "recent_follows", "follow_back"),
)

internal object ActivityFeedSectionsFingerprint : Fingerprint(
    strings = listOf("new_stories", "FOLLOW_REQUEST", "SUGGESTED_USERS", "friend_request_pinned_row"),
    custom = { method, _ ->
        AccessFlags.STATIC.isSet(method.accessFlags) &&
            method.parameterTypes.size == 16 &&
            method.parameterTypes[0] == "Landroid/content/Context;" &&
            method.parameterTypes.subList(8, 12).all { it == "Ljava/lang/String;" } &&
            method.parameterTypes.subList(12, 15).all { it == "Ljava/util/List;" } &&
            method.parameterTypes[15] == "Z"
    },
)

internal object ReelsHiddenItemsFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    parameters = listOf("Ljava/util/List;"),
    strings = listOf("android_purge_26_q2_HiddenClipsFilter_transform"),
)

internal object ReelsSuggestedUsersExitFingerprint : Fingerprint(
    returnType = "V",
    strings = listOf("android_purge_26_q3_ClipsSuggestedUsersItemDelegateImpl_onExitClipsViewer"),
)
