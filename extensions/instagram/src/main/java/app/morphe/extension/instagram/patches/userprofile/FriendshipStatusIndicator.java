/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


package app.morphe.extension.instagram.patches.userprofile;

import java.util.List;
import java.util.Arrays;
import android.widget.TextView;
import android.view.View;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.utils.Utils;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.entity.UserFriendshipStatus;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.constants.Strings;

import com.instagram.common.session.UserSession;

public class FriendshipStatusIndicator {

    private static Object getViewingProfileUserObject(Object classObject)throws Exception{
        Entity entity = new Entity(classObject);
        return entity.getField("fieldName");
    }

    /**
     * Given badge object and text, this function,
     * sets text to the badge and makes it visible.
     *
     * @param badgeObject The viewing profile user object.
     * @param text String text to set in badge label.
     * @throws Exception If an exception occurs during the method invocation.
     **/
    private static void setInternalBadgeText(Object badgeObject,String text) throws Exception {
        Entity entity = new Entity(badgeObject);
        TextView badgeView = (TextView) entity.getMethod("getView");
        badgeView.setVisibility(View.VISIBLE);
        badgeView.setText(text);
    }


    public static void indicators(UserSession userSession, Object profileInfoObject, Object badgeObject){
        if(Pref.followBackIndicator() && SettingsStatus.followBackIndicator) {
            try {
                String loggedInUserId = userSession.getUserId();
                Object viewingProfileUserObject = getViewingProfileUserObject(profileInfoObject);
                UserData viewingUserData = new UserData(viewingProfileUserObject);
                String viewingProfileUserId = viewingUserData.getUserId();

                // If the logged in user id is same as viewing profile, then no need to display the badge.
                if (loggedInUserId.equals(viewingProfileUserId)) return;

                UserFriendshipStatus userFriendshipStatus = viewingUserData.getUserFriendshipStatus();
                Boolean followed_by = userFriendshipStatus.getFollowBackStatus();
                String indicatorText = followed_by ? Strings.FBI_FOLLOWS_YOU : Strings.FBI_DOESNT_FOLLOWS_YOU;
                setInternalBadgeText(badgeObject, indicatorText);

                } catch (Exception ex) {
                    Logger.printException(() -> "Failed follow back indicator", ex);
            }
        }
    }
}

