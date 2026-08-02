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
import android.graphics.Typeface;
import android.util.TypedValue;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.view.ViewParent;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.entity.UserFriendshipStatus;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.entity.ProfileInfo;
import app.morphe.extension.instagram.entity.InstagramDialogBox;

import app.morphe.extension.shared.Logger;

import com.instagram.common.session.UserSession;

public class FriendshipStatusIndicator {

    private static void friendshipStatusDialogBox(Context context, UserFriendshipStatus userFriendshipStatus) {
        InstagramDialogBox dialog = new InstagramDialogBox(context);

        dialog.setNegativeButton(str("piko_cancel"),null);
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

    private static void addFriendshipTextView(View internalBadgeTextView, UserFriendshipStatus userFriendshipStatus, String text, String colorHex) throws Exception {
        if (internalBadgeTextView == null) return;

        ViewParent parent = internalBadgeTextView.getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) parent;

            // 1. Prevent adding duplicate views using a unique tag
            String tag = "piko_friendship_status_textview";
            if (viewGroup.findViewWithTag(tag) != null) {
                return; // Already added
            }

            Context context = internalBadgeTextView.getContext();
            TextView friendshipStatusTextView = new TextView(context);
            friendshipStatusTextView.setTag(tag);
            friendshipStatusTextView.setText(text);

            // 2. Set text color to Black
            friendshipStatusTextView.setTextColor(Color.BLACK);

            // Fetch display density for DP to PX conversion
            float density = context.getResources().getDisplayMetrics().density;

            // 3. Create rounded background with Grey color
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(8.0f * density); // 8dp rounded corners
            friendshipStatusTextView.setBackgroundColor(Color.parseColor(colorHex));

            // 4. Set left and right padding (16dp gap)
            int paddingPx = (int) (16.0f * density);
            friendshipStatusTextView.setPadding(paddingPx, 0, paddingPx, 0);

            // 5. Set font size to 12sp and typeface to bold.
            friendshipStatusTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            friendshipStatusTextView.setTypeface(null, Typeface.BOLD); // Bold.

            friendshipStatusTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    friendshipStatusDialogBox(v.getContext(), userFriendshipStatus);
                }
            });

            // 6. Place it directly underneath the target view
            int targetIndex = viewGroup.indexOfChild(internalBadgeTextView);
            viewGroup.addView(friendshipStatusTextView, targetIndex + 1);
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

                String indicatorText = followed_by ? str("piko_fbi_follows_you") : str("piko_fbi_doesnt_follows_you");
                indicatorText = followed_by && following ? str("piko_fbi_following_each_other") : indicatorText;

                Entity entity = new Entity(badgeObject);
                TextView badgeView = (TextView) entity.getMethod("getView");

                String colorHex = "#CCCCCC";
                if(Pref.followBackColorIndicator()){
                    colorHex = "#EB4941"; // Red Shade.

                    if(followed_by){
                        if(following){
                            colorHex = "#3389DF";
                        } else{
                            colorHex = "#3CC176";

                        }
                    }
                }

                addFriendshipTextView(badgeView, userFriendshipStatus, indicatorText, colorHex);

            } catch (Exception ex) {
                Logger.printException(() -> "Failed follow back indicator", ex);
            }
        }
    }
}

