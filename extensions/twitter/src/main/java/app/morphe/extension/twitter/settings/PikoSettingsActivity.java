/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

import android.app.Activity;
import android.os.Bundle;

/** Activity host for Piko screens that is independent of X's active app family. */
@SuppressWarnings("deprecation")
public final class PikoSettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applyTwitterTheme();
        super.onCreate(savedInstanceState);
        if (ActivityHook.create(this)) return;

        finish();
    }

    private void applyTwitterTheme() {
        getTheme().applyStyle(getStyleIdentifier("Twitter"), true);

        String selectedTheme = app.morphe.extension.twitter.Utils.getTheme();
        String palette = switch (selectedTheme) {
            case "dark" -> "Twitter.LightsOut";
            case "dim" -> "Twitter.Dim";
            default -> "Twitter.Standard";
        };
        getTheme().applyStyle(getStyleIdentifier(palette), true);
    }

    private int getStyleIdentifier(String name) {
        int identifier = getResources().getIdentifier(name, "style", getPackageName());
        if (identifier != 0) return identifier;

        throw new IllegalStateException("Missing style: " + name);
    }

    @Override
    public void onBackPressed() {
        if (SettingsSearchUIController.handleBackPressed(this)) return;

        super.onBackPressed();
    }
}
