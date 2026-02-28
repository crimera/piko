package app.morphe.extension.instagram.patches.userprofile;

import java.util.List;
import java.util.Arrays;
import android.widget.TextView;
import android.view.View;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.utils.Utils;
import app.morphe.extension.instagram.constants.Strings;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.entity.UserFriendshipStatus;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.constants.Strings;

import com.instagram.common.session.UserSession;

public class FriendshipStatusIndicator {

    private static Object getViewingProfileUserObject(Object classObject)throws Exception{
        Entity entity = new Entity(classObject);
        return entity.getField("fieldName");
    }

    /**
     * Given user object, this function returns user's id.
     *
     * @param userObject The viewing profile user object.
     * @return The user ID as string.
     * @throws Exception If an exception occurs during the method invocation.
     **/
    private static String getViewingProfileUserId(Object userObject) throws Exception {
        Entity entity = new Entity(userObject);
        return (String) entity.getMethod("getId");
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
        if(Pref.followBackIndicator()) {
            try {
                String loggedInUserId = userSession.getUserId();
                Object viewingProfileUserObject = getViewingProfileUserObject(profileInfoObject);
                String viewingProfileUserId = getViewingProfileUserId(viewingProfileUserObject);

                // If the logged in user id is same as viewing profile, then no need to display the badge.
                if (loggedInUserId.equals(viewingProfileUserId)) return;

                UserFriendshipStatus userFriendshipStatus = new UserFriendshipStatus(viewingProfileUserObject);
                Boolean followed_by = userFriendshipStatus.getFollowBackStatus();
                String indicatorText = followed_by ? Strings.FBI_FOLLOWS_YOU : Strings.FBI_DOESNT_FOLLOWS_YOU;
                setInternalBadgeText(badgeObject, indicatorText);

                } catch (Exception ex) {
                    Logger.printException(() -> "Failed follow back indicator", ex);
            }
        }
    }
}

