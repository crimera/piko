package app.morphe.extension.xlite.settings;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.ContextThemeWrapper;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

@SuppressWarnings("deprecation")
public final class XLiteSettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyHostTheme();
        super.onCreate(savedInstanceState);
        Utils.setActivity(this);
        setTitle(StringRef.str("piko_xlite_settings_title"));
        if (savedInstanceState != null) return;
        getFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new XLiteSettingsFragment())
                .commit();
    }

    private void applyHostTheme() {
        getTheme().applyStyle(style("Twitter"), true);
        getTheme().applyStyle(style(selectedPalette(this)), true);
    }

    static Context createPreferenceContext(Context context) {
        int theme = "Twitter.Standard".equals(selectedPalette(context))
                ? android.R.style.Theme_Material_Light_NoActionBar
                : android.R.style.Theme_Material_NoActionBar;
        return new ContextThemeWrapper(context, theme);
    }

    private static String selectedPalette(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(
                context.getPackageName() + "_preferences",
                Context.MODE_PRIVATE
        );
        if ("0".equals(preferences.getString("three_state_night_mode", "light"))) {
            return "Twitter.Standard";
        }
        return switch (preferences.getString("dark_mode_appearance", "lights_out")) {
            case "dim" -> "Twitter.Dim";
            case "lights_out" -> "Twitter.LightsOut";
            default -> "Twitter.Standard";
        };
    }

    private int style(String resourceName) {
        return ResourceUtils.getIdentifierOrThrow(this, ResourceType.STYLE, resourceName);
    }
}
