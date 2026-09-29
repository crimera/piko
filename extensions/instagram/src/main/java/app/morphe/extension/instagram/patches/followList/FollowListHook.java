/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.followList;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import static app.morphe.extension.instagram.utils.IgStr.str;

import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.entity.UserFriendshipStatus;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.patches.userprofile.FriendshipStatusIndicator;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceUtils;

import com.instagram.common.session.UserSession;

/** Runtime hook for "Show non-followers in Following list". Field names resolved at patch time. */
public class FollowListHook {

    private static final String BADGE_TAG = "piko_follow_list_badge";

    // Parameter order matches bindView(int, View, Object, Object) exactly, since it's called with
    // all of bindView's own parameters passed straight through.
    public static void onRowBound(Object binderObj, int position, View row, Object userObj) {
        try {
            ImageView badge = row.findViewWithTag(BADGE_TAG);
            // A recycled row may no longer belong to an enabled, eligible list.
            if (badge != null) badge.setVisibility(View.GONE);
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

            UserData userData = new UserData(userObj);
            UserFriendshipStatus friendshipStatus = userData.getUserFriendshipStatus();
            boolean doesntFollowBack = Boolean.FALSE.equals(friendshipStatus.getFollowBackStatus());
            if (!doesntFollowBack) return;

            FrameLayout avatar = findAvatar(row);
            if (avatar == null) return;
            if (badge != null && badge.getParent() != avatar) {
                ((ViewGroup) badge.getParent()).removeView(badge);
                badge = null;
            }
            if (badge == null) {
                badge = createBadge(avatar.getContext());
                avatar.addView(badge, new FrameLayout.LayoutParams(
                        dp(avatar.getContext(), 24), dp(avatar.getContext(), 24),
                        Gravity.BOTTOM | Gravity.RIGHT));
            }

            Context context = avatar.getContext();
            int iconColor = Pref.followBackColorIndicator()
                    ? Color.rgb(235, 73, 65)
                    : themedColor(context, "igds_color_primary_text");
            // Tint alone left this icon black in dark mode; use the profile indicator's filter.
            badge.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_ATOP));
            ((GradientDrawable) badge.getBackground()).setColor(
                    themedColor(context, "igds_color_primary_background"));
            badge.setContentDescription(str("piko_fbi_doesnt_follows_you"));
            FriendshipStatusIndicator.setStatusClickListener(badge, friendshipStatus);
            badge.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            Logger.printException(() -> "Failed to bind follow list badge", e);
        }
    }

    private static ImageView createBadge(Context context) {
        ImageView badge = new ImageView(context);
        badge.setTag(BADGE_TAG);
        Drawable icon = ResourceUtils.getDrawable("fb_ic_friend_remove_outline_20").mutate();
        badge.setImageDrawable(icon);
        badge.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int padding = dp(context, 4);
        badge.setPadding(padding, padding, padding, padding);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        badge.setBackground(background);
        badge.setFocusable(true);
        badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        return badge;
    }

    private static int themedColor(Context context, String attrName) {
        TypedValue value = new TypedValue();
        int attrId = ResourceUtils.getAttrIdentifier(attrName);
        if (attrId != 0 && context.getTheme().resolveAttribute(attrId, value, true)) {
            if (value.resourceId != 0) return context.getColor(value.resourceId);
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= TypedValue.TYPE_LAST_COLOR_INT) return value.data;
        }
        return UI.getThemedColour(attrName);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static FrameLayout findAvatar(View row) {
        int id = row.getContext().getResources()
                .getIdentifier("follow_list_user_imageview", "id", row.getContext().getPackageName());
        View avatar = id != 0 ? row.findViewById(id) : null;
        // GradientSpinnerAvatarView is a FrameLayout; leave unknown layouts untouched.
        return avatar instanceof FrameLayout ? (FrameLayout) avatar : null;
    }
}
