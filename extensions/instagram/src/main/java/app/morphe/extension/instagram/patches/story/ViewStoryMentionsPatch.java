/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/

package app.morphe.extension.instagram.patches.story;

import static app.morphe.extension.instagram.utils.IgStr.str;

import java.util.HashSet;
import java.util.ArrayList;
import android.app.Dialog;
import android.content.Context;
import android.view.View;

import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.entity.MediaData;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.crimera.PikoUtils;

import com.instagram.igds.components.peoplecell.IgdsPeopleCell;
import com.instagram.common.typedurl.ImageUrl;

public class ViewStoryMentionsPatch {

    public static void viewMentions(Context context, Object mediaObject){
        try {
            HashSet<UserData> mentionSet = new MediaData(mediaObject).getMentionSet();

            ArrayList<IgdsPeopleCell> peopleCells = new ArrayList<>();
            if(mentionSet!=null) {
                mentionSet.forEach(userData -> {
                    try {
                        String fullName = userData.getFullName();
                        String username = userData.getUsername();
                        ImageUrl lowResDP = userData.getLowResProfilePicture();
                        boolean isVerified = userData.isVerified();

                        IgdsPeopleCell cell = new IgdsPeopleCell(context);

                        cell.A0A(fullName, isVerified);
                        cell.A08(username);
                        cell.A06(lowResDP, null);

                        cell.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                PikoUtils.openUrl("instagram://user?username=" + username, true);
                            }
                        });

                        peopleCells.add(cell);
                    } catch (Exception ex){
                            Logger.printException(() -> "Failed story mention user extraction", ex);
                            PikoUtils.logger(ex);
                    }
                });
            }
            PeopleCellDialogBox.showPeopleDialog(context, peopleCells);

        } catch (Exception ex){
            Logger.printException(() -> "Failed viewMentions", ex);
            PikoUtils.logger(ex);
        }
    }

}