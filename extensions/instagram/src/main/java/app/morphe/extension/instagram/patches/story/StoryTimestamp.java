/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.story;

import android.content.Context;
import android.icu.text.MeasureFormat;
import android.icu.util.Measure;
import android.icu.util.MeasureUnit;
import android.text.format.DateFormat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public class StoryTimestamp {
    private static String CUSTOMISE_STORY_TIMESTAMP;

    static {
        CUSTOMISE_STORY_TIMESTAMP = Pref.customiseStoryTimestamp();
    }

    public static String customiseStoryTimestamp(long postedTS) {
        String formatedTS = null;

        try {
            if (!CUSTOMISE_STORY_TIMESTAMP.equals("detailed")
                    && !CUSTOMISE_STORY_TIMESTAMP.equals("posttime")
                    && !CUSTOMISE_STORY_TIMESTAMP.equals("timeleft")) {
                return null;
            }

            Context context = Utils.getContext();
            if (context == null) return null;
            Locale locale = context.getResources().getConfiguration().getLocales().get(0);
            postedTS = postedTS * 1000;

            if (CUSTOMISE_STORY_TIMESTAMP.equals("detailed")
                    || CUSTOMISE_STORY_TIMESTAMP.equals("posttime")) {
                String skeleton = DateFormat.is24HourFormat(context) ? "Hms" : "hms";
                if (CUSTOMISE_STORY_TIMESTAMP.equals("detailed")) {
                    skeleton = "EEEEMMMMd" + skeleton;
                }
                Date date = new Date(postedTS);
                SimpleDateFormat format = new SimpleDateFormat(
                        DateFormat.getBestDateTimePattern(locale, skeleton), locale);
                formatedTS = format.format(date);

            } else if (CUSTOMISE_STORY_TIMESTAMP.equals("timeleft")) {
                long now = System.currentTimeMillis();
                long expireTS = postedTS + 86400000L; // +24 hours
                long diff = expireTS - now;
                MeasureFormat format = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT);

                if (diff <= 0) {
                    formatedTS = format.formatMeasures(new Measure(0, MeasureUnit.SECOND));
                } else {
                    long totalSeconds = diff / 1000;

                    long hours = totalSeconds / 3600;
                    long minutes = (totalSeconds % 3600) / 60;
                    long seconds = totalSeconds % 60;

                    formatedTS = format.formatMeasures(
                            new Measure(hours, MeasureUnit.HOUR),
                            new Measure(minutes, MeasureUnit.MINUTE),
                            new Measure(seconds, MeasureUnit.SECOND));
                }
            }

        } catch (Exception e) {
            Logger.printException(() -> "customiseStoryTimestamp failure", e);
        }

        return formatedTS;
    }
}
