/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.font;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;

import java.util.Locale;

@SuppressWarnings("unused")
public final class ForceSystemFontPatch {

    private ForceSystemFontPatch() {
    }

    public static Typeface getSystemTypeface(
            Context context,
            int resourceId,
            int style
    ) {
        if (context == null) {
            return null;
        }

        String resourceName;

        try {
            resourceName = context
                    .getResources()
                    .getResourceEntryName(resourceId)
                    .toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return null;
        }

        if (resourceName.equals("iguibetav9_regular")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return Typeface.create(Typeface.DEFAULT, 400, false);
            }
            return Typeface.create(Typeface.DEFAULT, Typeface.NORMAL);
        }

        if (resourceName.equals("iguibetav9_semibold")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return Typeface.create(Typeface.DEFAULT, 600, false);
            }
            return Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
        }

        if (resourceName.equals("iguibetav9_bold")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return Typeface.create(Typeface.DEFAULT, 700, false);
            }
            return Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
        }

        if (resourceName.equals("prism_sans")) {
            return Typeface.create(Typeface.DEFAULT, style);
        }

        return null;
    }

    public static Typeface getSystemTypefaceForWeight(int weight) {
        int normalizedWeight = Math.max(1, Math.min(1000, weight));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(
                    Typeface.DEFAULT,
                    normalizedWeight,
                    false
            );
        }

        return Typeface.create(
                Typeface.DEFAULT,
                normalizedWeight >= 600
                        ? Typeface.BOLD
                        : Typeface.NORMAL
        );
    }
}