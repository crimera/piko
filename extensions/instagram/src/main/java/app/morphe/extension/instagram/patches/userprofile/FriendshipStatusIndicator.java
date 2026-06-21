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

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.utils.Utils;
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

    public static void indicators(Object profileInfoObject, Object badgeObject){
        if(Pref.followBackIndicator() && SettingsStatus.followBackIndicator) {
            try {
                ProfileInfo profileInfo = new ProfileInfo(profileInfoObject);
                Boolean isSelfProfile = profileInfo.isSelfProfile();

                // If the logged in profile, then no need to display the badge.
                if (isSelfProfile) return;

                UserData viewingUserData = profileInfo.getUserData();
                UserFriendshipStatus userFriendshipStatus = viewingUserData.getUserFriendshipStatus();
                Boolean followed_by = userFriendshipStatus.getFollowBackStatus();
                String indicatorText = followed_by ? str("piko_fbi_follows_you") : str("piko_fbi_doesnt_follows_you");

                Entity entity = new Entity(badgeObject);
                TextView badgeView = (TextView) entity.getMethod("getView");
                badgeView.setVisibility(View.VISIBLE);
                badgeView.setText(indicatorText);

                badgeView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        friendshipStatusDialogBox(v.getContext(), userFriendshipStatus);
                    }
                });

            } catch (Exception ex) {
                Logger.printException(() -> "Failed follow back indicator", ex);
            }
        }
    }
}

