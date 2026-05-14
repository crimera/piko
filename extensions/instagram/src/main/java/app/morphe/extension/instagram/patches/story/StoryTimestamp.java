/*
    * Copyright (C) 2026 piko <https://github.com/crimera/piko>
    *
    * This file is part of piko.
    *
    * Any modifications, derivatives, or substantial rewrites of this file
    * must retain this copyright notice and the piko attribution
    * in the source code and version control history.
*/


package app.morphe.extension.instagram.patches.story;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.instagram.utils.Utils;

public class StoryTimestamp {
    private static String CUSTOMISE_STORY_TIMESTAMP;
    static{
        CUSTOMISE_STORY_TIMESTAMP = Pref.customiseStoryTimestamp();
    }

    public static String customiseStoryTimestamp(long postedTS){
        String formatedTS = null;

        try {
            postedTS = postedTS * 1000;
            if(CUSTOMISE_STORY_TIMESTAMP.equals("detailed")){
                Date date = new Date(postedTS);
                SimpleDateFormat format =
                        new SimpleDateFormat("EEEE, MMMM dd HH:mm:ss", Locale.getDefault());
                formatedTS = format.format(date);

            } else if(CUSTOMISE_STORY_TIMESTAMP.equals("timeleft")) {
                long now = System.currentTimeMillis();
                long expireTS = postedTS + 86400000L; // +24 hours
                long diff = expireTS - now;
                
                if (diff <= 0) {
                    formatedTS = "0s";
                } else {
                    long totalSeconds = diff / 1000;

                    long hours = totalSeconds / 3600;
                    long minutes = (totalSeconds % 3600) / 60;
                    long seconds = totalSeconds % 60;

                    formatedTS = hours + "h " + minutes + "m " + seconds + "s left";
                }
            }

        } catch (Exception e) {
            Logger.printException(() -> "customiseStoryTimestamp failure", e);
        }
        return formatedTS;
    }

}