/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.font;

import android.content.Context;
import android.graphics.Typeface;

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
            return Typeface.create(Typeface.DEFAULT, 400, false);
        }

        if (resourceName.equals("iguibetav9_semibold")) {
            return Typeface.create(Typeface.DEFAULT, 600, false);
        }

        if (resourceName.equals("iguibetav9_bold")) {
            return Typeface.create(Typeface.DEFAULT, 700, false);
        }

        if (resourceName.equals("prism_sans")) {
            return Typeface.create(Typeface.DEFAULT, style);
        }

        return null;
    }

    public static Typeface getSystemTypefaceForWeight(int weight) {
        int normalizedWeight = Math.max(1, Math.min(1000, weight));

        return Typeface.create(
                Typeface.DEFAULT,
                normalizedWeight,
                false
        );
    }
}