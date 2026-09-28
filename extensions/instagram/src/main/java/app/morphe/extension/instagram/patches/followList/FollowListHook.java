/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.followList;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import static app.morphe.extension.instagram.utils.IgStr.str;

import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.entity.UserFriendshipStatus;
import app.morphe.extension.instagram.patches.userprofile.FriendshipStatusIndicator;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;

import com.instagram.common.session.UserSession;

/** Runtime hook for "Show non-followers in Following list". Field names resolved at patch time. */
public class FollowListHook {

    private static final String BADGE_TAG = "piko_follow_list_badge";

    // Parameter order matches bindView(int, View, Object, Object) exactly, since it's called with
    // all of bindView's own parameters passed straight through.
    public static void onRowBound(Object binderObj, int position, View row, Object userObj) {
        try {
            if (!Pref.followListNonFollowerBadge()) return;

            Entity binderEntity = new Entity(binderObj);
            Object listTypeObj = binderEntity.getField("fieldName");
            String listType = (String) new Entity(listTypeObj).getField("fieldName2");
            if (!"following".equals(listType)) return;

            Object listConfigObj = binderEntity.getField("fieldName3");
            Object followListDataObj = new Entity(listConfigObj).getField("fieldName4");
            String targetUserId = (String) new Entity(followListDataObj).getField("fieldName5");
            UserSession session = (UserSession) binderEntity.getField("fieldName6");
            if (!targetUserId.equals(session.getUserId())) return;

            if (!(row instanceof ViewGroup)) return;

            ViewGroup badgeContainer = findBadgeContainer((ViewGroup) row);
            View existingBadge = badgeContainer.findViewWithTag(BADGE_TAG);

            UserData userData = new UserData(userObj);
            UserFriendshipStatus friendshipStatus = userData.getUserFriendshipStatus();
            boolean doesntFollowBack = Boolean.FALSE.equals(friendshipStatus.getFollowBackStatus());

            if (!doesntFollowBack) {
                if (existingBadge != null) badgeContainer.removeView(existingBadge);
                return;
            }
            if (existingBadge != null) return;

            // Same badge style as the profile-page friendship status indicator, for consistency.
            TextView badge = FriendshipStatusIndicator.createStatusTextView(
                    badgeContainer.getContext(), friendshipStatus,
                    str("piko_fbi_doesnt_follows_you"), "#EB4941", "fb_ic_friend_remove_outline_20");
            badge.setTag(BADGE_TAG);

            float density = badgeContainer.getContext().getResources().getDisplayMetrics().density;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = Math.round(4 * density);
            badgeContainer.addView(badge, params);
        } catch (Exception e) {
            Logger.printException(() -> "Failed to bind follow list badge", e);
        }
    }

    // Resource names survive minification, so the username/subtitle column is looked up by name
    // instead of guessing which child view to use. Falls back to the row itself if not found.
    private static ViewGroup findBadgeContainer(ViewGroup row) {
        int id = row.getContext().getResources()
                .getIdentifier("follow_list_content_container", "id", row.getContext().getPackageName());
        View content = id != 0 ? row.findViewById(id) : null;
        return content instanceof ViewGroup ? (ViewGroup) content : row;
    }
}
