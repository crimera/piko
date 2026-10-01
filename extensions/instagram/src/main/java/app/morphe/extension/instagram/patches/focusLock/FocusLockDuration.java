/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.focusLock;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.ResourceUtils;

/**
 * How long a lock lasts, as a fixed ladder of steps.
 *
 * The steps are stored in minutes and grow quickly, so one slider covers a minute up to a few
 * months without the short end being unreachable. The labels come from resources so they stay
 * translatable, and the two arrays are read in step, so they have to stay the same length.
 */
public final class FocusLockDuration {

    private static final int[] FALLBACK_MINUTES = {
            1, 5, 10, 30, 60, 120, 360, 720, 1440, 4320, 10080, 20160, 43200, 129600
    };
    private static final int DEFAULT_MINUTES = 10080;

    private FocusLockDuration() {
    }

    private static int[] steps() {
        try {
            CharSequence[] values = ResourceUtils.getStringArray("piko_array_focus_lock_duration_val");
            if (values != null && values.length > 0) {
                int[] steps = new int[values.length];
                for (int i = 0; i < values.length; i++) {
                    steps[i] = Integer.parseInt(values[i].toString().trim());
                }
                return steps;
            }
        } catch (Exception ignored) {
            // Fall through to the built in ladder.
        }
        return FALLBACK_MINUTES;
    }

    public static int stepCount() {
        return steps().length;
    }

    public static int minutes() {
        int stored = parse(Pref.focusLockDurationMinutes());
        return stored > 0 ? stored : DEFAULT_MINUTES;
    }

    private static int parse(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** The slider notch for the stored value, or the closest one below it. */
    public static int currentStep() {
        int[] steps = steps();
        int minutes = minutes();
        int best = 0;
        for (int i = 0; i < steps.length; i++) {
            if (steps[i] <= minutes) best = i;
        }
        return best;
    }

    public static boolean setStep(int step) {
        int[] steps = steps();
        if (step < 0 || step >= steps.length) return false;
        return Pref.setFocusLockDurationMinutes(String.valueOf(steps[step]));
    }

    public static String labelForStep(int step) {
        try {
            CharSequence[] labels = ResourceUtils.getStringArray("piko_array_focus_lock_duration");
            if (labels != null && step >= 0 && step < labels.length) return labels[step].toString();
        } catch (Exception ignored) {
            // Fall through to the raw minutes.
        }
        int[] steps = steps();
        int minutes = step >= 0 && step < steps.length ? steps[step] : minutes();
        return minutes + "m";
    }

    /** The label for what is stored, for use outside the slider. */
    public static String summary() {
        return labelForStep(currentStep());
    }

    public static long millis() {
        return minutes() * 60L * 1000L;
    }
}
