package app.morphe.extension.newx.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;

import app.morphe.extension.crimera.theme.SettingsColor;
import app.morphe.extension.crimera.theme.SettingsFont;
import app.morphe.extension.crimera.theme.SettingsTheme;
import app.morphe.extension.newx.misc.UpdateFont;
import app.morphe.extension.newx.theme.TwitterTheme;

/**
 * NewX's look for the shared settings widgets: the palette the X app and the NewX theme chooser
 * resolve, the Chirp fonts with the user's custom font on top, and the host's own resource styles.
 */
public final class NewXSettingsTheme implements SettingsTheme {
    @Override
    public boolean isDark(Context context) {
        return Theme.isDark(context);
    }

    @Override
    public int color(Context context, SettingsColor role) {
        return switch (role) {
            case SURFACE -> Theme.surface(context);
            case SURFACE_CONTAINER -> Theme.surfaceContainer(context);
            case SURFACE_CONTAINER_HIGH -> Theme.surfaceContainerHigh(context);
            case SURFACE_VARIANT -> Theme.surfaceVariant(context);
            case ON_SURFACE -> Theme.primaryText(context);
            case ON_SURFACE_VARIANT -> Theme.secondaryText(context);
            case ACCENT -> Theme.primaryAccent(context);
            case ON_ACCENT -> Theme.onPrimaryAccent(context);
            case ACCENT_CONTAINER -> Theme.primaryContainer(context);
            case ON_ACCENT_CONTAINER -> Theme.onPrimaryContainer(context);
            case OUTLINE -> Theme.dividerColor(context);
            case CHECKBOX_CHECKED -> Theme.checkboxChecked(context);
        };
    }

    @Override
    public Typeface typeface(Context context, SettingsFont font, Typeface fallback) {
        return switch (font) {
            case ROW_TITLE -> UpdateFont.customTypefaceOr(hostFont(context, "chirp_medium_500", fallback));
            case ROW_SUMMARY -> UpdateFont.customTypefaceOr(hostFont(context, "chirp_regular_400", fallback));
            case GENERIC -> UpdateFont.customTypefaceOr(fallback);
        };
    }

    @Override
    public void applyHostTheme(Activity activity) {
        int baseStyle = style(activity, "Twitter");
        if (baseStyle == 0) baseStyle = style(activity, "Theme.AppCompat.DayNight.NoActionBar");
        if (baseStyle != 0) activity.getTheme().applyStyle(baseStyle, true);

        int paletteStyle = style(activity, TwitterTheme.fromContext(activity).styleResourceName());
        if (paletteStyle != 0) activity.getTheme().applyStyle(paletteStyle, true);
    }

    private static int style(Activity activity, String resourceName) {
        return activity.getResources().getIdentifier(resourceName, "style", activity.getPackageName());
    }

    private static Typeface hostFont(Context context, String resourceName, Typeface fallback) {
        int resourceId = context.getResources().getIdentifier(
                resourceName,
                "font",
                context.getPackageName()
        );
        if (resourceId == 0) return fallback;
        try {
            return context.getResources().getFont(resourceId);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
