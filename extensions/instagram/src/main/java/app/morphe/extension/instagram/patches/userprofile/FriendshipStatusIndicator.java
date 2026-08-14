/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches.userprofile;

import static app.morphe.extension.instagram.utils.IgStr.str;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Arrays;
import android.widget.TextView;
import android.view.View;
import android.content.Context;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.ViewParent;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.entity.UserFriendshipStatus;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.entity.ProfileInfo;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.constants.UI;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.ui.Dim;

import com.instagram.common.session.UserSession;

public class FriendshipStatusIndicator {

    private static void friendshipStatusDialogBox(Context context, UserFriendshipStatus userFriendshipStatus) {
        InstagramDialogBox dialog = new InstagramDialogBox(context);

        dialog.setPositiveButton(str("piko_ok"), null);
        dialog.setTitle(str("piko_friendship_status"));
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        Map<String, Boolean> friendshipMap = userFriendshipStatus.getMappings();

        StringBuilder content = new StringBuilder();
        content.append("\n\n");
        for (Map.Entry<String, Boolean> entry : friendshipMap.entrySet()) {
            String tag = entry.getKey();
            Object sts = entry.getValue();
            if(sts!=null){
                tag = tag.replace("_"," ");
                content.append(tag+": "+sts.toString().toUpperCase()+"\n\n");
            }

        }
        // Removes last \n
        if(content.length()>2)  content.deleteCharAt(content.length() - 2);
        dialog.setMessage(content.toString());



        Dialog dlg = dialog.getDialog();
        dlg.show();
    }

    private static void addFriendshipTextView(
            View internalBadgeTextView,
            UserFriendshipStatus userFriendshipStatus,
            String text,
            String indicatorColorHex,
            String indicatorIconDrawable
    ) throws Exception {
        if (internalBadgeTextView == null) return;

        ViewParent parent = internalBadgeTextView.getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) parent;

            String tag = "piko_friendship_status_textview";
            if (viewGroup.findViewWithTag(tag) != null) {
                return;
            }

            Context context = internalBadgeTextView.getContext();
            TextView friendshipStatusTextView = new TextView(context);
            friendshipStatusTextView.setTag(tag);
            friendshipStatusTextView.setText(text);

            float indicatorTextSizeSp = 12;
            int indicatorTextSizePx = Math.round(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP,
                    indicatorTextSizeSp,
                    context.getResources().getDisplayMetrics()
            ));
            int primaryTextColor = UI.getThemedColour("igds_color_primary_text");
            friendshipStatusTextView.setTextColor(primaryTextColor);

            int indicatorColor = indicatorColorHex == null
                    ? primaryTextColor
                    : Color.parseColor(indicatorColorHex);
            Drawable statusIcon = ResourceUtils
                    .getDrawable(indicatorIconDrawable)
                    .mutate();
            statusIcon.setColorFilter(
                    new PorterDuffColorFilter(indicatorColor, PorterDuff.Mode.SRC_ATOP)
            );
            statusIcon.setBounds(0, 0, indicatorTextSizePx, indicatorTextSizePx);

            friendshipStatusTextView.setCompoundDrawablePadding(Dim.dp4);
            friendshipStatusTextView.setCompoundDrawablesRelative(
                    statusIcon,
                    null,
                    null,
                    null
            );
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.TRANSPARENT);
            background.setStroke(
                    Dim.dp2 / 2,
                    Color.argb(
                            26,
                            Color.red(primaryTextColor),
                            Color.green(primaryTextColor),
                            Color.blue(primaryTextColor)
                    )
            );
            background.setCornerRadius(Dim.dp20);
            friendshipStatusTextView.setBackground(background);
            friendshipStatusTextView.setGravity(Gravity.CENTER_VERTICAL);
            friendshipStatusTextView.setIncludeFontPadding(false);
            friendshipStatusTextView.setSingleLine(true);
            friendshipStatusTextView.setPadding(Dim.dp8, Dim.dp6, Dim.dp8, Dim.dp6);
            friendshipStatusTextView.setTextSize(
                    TypedValue.COMPLEX_UNIT_SP,
                    indicatorTextSizeSp
            );
            friendshipStatusTextView.setTypeface(Typeface.create(
                    "sans-serif-medium",
                    Typeface.NORMAL
            ));

            friendshipStatusTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    friendshipStatusDialogBox(v.getContext(), userFriendshipStatus);
                }
            });

            int targetIndex = viewGroup.indexOfChild(internalBadgeTextView);
            ViewGroup.MarginLayoutParams layoutParams = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            layoutParams.setMargins(0, Dim.dp4, 0, Dim.dp6);
            viewGroup.addView(friendshipStatusTextView, targetIndex + 1, layoutParams);
        }
    }

    public static void addFriendshipIndicator(Object profileInfoObject, Object badgeObject){
        if(Pref.followBackIndicator() && SettingsStatus.followBackIndicator) {
            try {
                ProfileInfo profileInfo = new ProfileInfo(profileInfoObject);
                Boolean isSelfProfile = profileInfo.isSelfProfile();

                // If the logged in profile, then no need to display the badge.
                if (isSelfProfile) return;

                UserData viewingUserData = profileInfo.getUserData();
                UserFriendshipStatus userFriendshipStatus = viewingUserData.getUserFriendshipStatus();
                Boolean followed_by = userFriendshipStatus.getFollowBackStatus();
                Boolean following = userFriendshipStatus.getFollowingStatus();

                Entity entity = new Entity(badgeObject);
                TextView badgeView = (TextView) entity.getMethod("getView");

                String indicatorText;
                String indicatorIconDrawable;
                String indicatorColorHex;
                boolean useStatusColor = Pref.followBackColorIndicator();
                if (followed_by && following) {
                    indicatorText = str("piko_fbi_following_each_other");
                    indicatorIconDrawable = "fb_ic_friend_confirm_outline_20";
                    indicatorColorHex = useStatusColor ? "#3389DF" : null;
                } else if (followed_by) {
                    indicatorText = str("piko_fbi_follows_you");
                    indicatorIconDrawable = "fb_ic_friend_add_outline_20";
                    indicatorColorHex = useStatusColor ? "#3CC176" : null;
                } else {
                    indicatorText = str("piko_fbi_doesnt_follows_you");
                    indicatorIconDrawable = "fb_ic_friend_remove_outline_20";
                    indicatorColorHex = useStatusColor ? "#EB4941" : null;
                }

                addFriendshipTextView(
                        badgeView,
                        userFriendshipStatus,
                        indicatorText,
                        indicatorColorHex,
                        indicatorIconDrawable
                );

            } catch (Exception ex) {
                Logger.printException(() -> "Failed follow back indicator", ex);
            }
        }
    }
}

