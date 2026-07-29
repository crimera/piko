package app.morphe.extension.xlite.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.util.TypedValue;

/**
 * Material design system color tokens and metrics for X-Lite UI components.
 */
public final class Theme {

    private Theme() {
    }

    public static boolean isDark(Context context) {
        if (context == null) return true;
        int nightMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int dpToPx(Context context, float dp) {
        if (context == null) return Math.round(dp);
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                dp,
                context.getResources().getDisplayMetrics()
        ));
    }

    public static int spToPx(Context context, float sp) {
        if (context == null) return Math.round(sp);
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                sp,
                context.getResources().getDisplayMetrics()
        ));
    }

    public static int surface(Context context) {
        return isDark(context) ? Color.rgb(20, 18, 24) : Color.rgb(254, 247, 255);
    }

    public static int surfaceContainer(Context context) {
        return isDark(context) ? Color.BLACK : Color.WHITE;
    }

    public static int surfaceContainerHigh(Context context) {
        return isDark(context) ? Color.rgb(40, 42, 48) : Color.rgb(243, 237, 247);
    }

    public static int surfaceVariant(Context context) {
        return isDark(context) ? Color.rgb(54, 56, 64) : Color.rgb(231, 224, 236);
    }

    public static int primaryText(Context context) {
        return isDark(context) ? Color.rgb(230, 225, 229) : Color.rgb(29, 27, 32);
    }

    public static int secondaryText(Context context) {
        return isDark(context) ? Color.rgb(202, 196, 208) : Color.rgb(73, 69, 79);
    }

    public static int primaryAccent(Context context) {
        return isDark(context) ? Color.rgb(29, 155, 240) : Color.rgb(29, 155, 240);
    }

    public static int onPrimaryAccent(Context context) {
        return Color.WHITE;
    }

    public static int primaryContainer(Context context) {
        return isDark(context) ? Color.rgb(26, 75, 110) : Color.rgb(218, 238, 255);
    }

    public static int onPrimaryContainer(Context context) {
        return isDark(context) ? Color.rgb(205, 232, 255) : Color.rgb(0, 45, 80);
    }

    public static int dividerColor(Context context) {
        return isDark(context) ? Color.argb(38, 255, 255, 255) : Color.argb(31, 0, 0, 0);
    }

    public static int rippleColor(Context context) {
        return isDark(context) ? Color.argb(40, 255, 255, 255) : Color.argb(32, 0, 0, 0);
    }

    public static int blend(int color1, int color2, float ratio) {
        float inverseRatio = 1f - ratio;
        float r = Color.red(color1) * inverseRatio + Color.red(color2) * ratio;
        float g = Color.green(color1) * inverseRatio + Color.green(color2) * ratio;
        float b = Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio;
        float a = Color.alpha(color1) * inverseRatio + Color.alpha(color2) * ratio;
        return Color.argb((int) a, (int) r, (int) g, (int) b);
    }
}
