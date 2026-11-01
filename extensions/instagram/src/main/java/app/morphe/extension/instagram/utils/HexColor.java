/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.utils;

import android.graphics.Color;

import java.util.Locale;
import java.util.regex.Pattern;

/** Colors written as hexadecimal digits, RRGGBB or AARRGGBB. */
public final class HexColor {
    private static final Pattern HEX = Pattern.compile("[0-9A-F]{6}|[0-9A-F]{8}");

    private HexColor() { }

    /** The color as RRGGBB or AARRGGBB in capitals, or null when it is not one. A leading # is dropped. */
    public static String normalize(String color) {
        if (color == null) return null;
        String hex = color.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        hex = hex.toUpperCase(Locale.ROOT);
        return HEX.matcher(hex).matches() ? hex : null;
    }

    public static int toArgb(String color) {
        long value = Long.parseLong(color, 16);
        return color.length() == 6 ? (int) (0xFF000000L | value) : (int) value;
    }

    /** Whether the character is one of 0-9, A-F and a-f, and not a digit of another script. */
    public static boolean isDigit(char character) {
        return (character >= '0' && character <= '9')
                || (character >= 'A' && character <= 'F')
                || (character >= 'a' && character <= 'f');
    }

    public static double brightness(int argb) {
        return (299 * Color.red(argb) + 587 * Color.green(argb) + 114 * Color.blue(argb)) / 1000.0;
    }

    public static String readableText(double brightness) {
        return brightness >= 150 ? "000000" : "FFFFFF";
    }

    /** For text over several colors, such as the ends of a gradient; white when both are as bad. */
    public static String readableTextOnAll(int... backgroundsArgb) {
        double white = Double.MAX_VALUE;
        double black = Double.MAX_VALUE;
        for (int background : backgroundsArgb) {
            white = Math.min(white, contrast(Color.WHITE, background));
            black = Math.min(black, contrast(Color.BLACK, background));
        }
        return black > white ? "000000" : "FFFFFF";
    }

    private static double contrast(int foregroundArgb, int backgroundArgb) {
        double foreground = Color.luminance(foregroundArgb) + 0.05;
        double background = Color.luminance(backgroundArgb) + 0.05;
        return Math.max(foreground, background) / Math.min(foreground, background);
    }
}
