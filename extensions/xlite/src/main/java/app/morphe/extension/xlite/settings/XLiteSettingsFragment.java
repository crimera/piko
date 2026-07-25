package app.morphe.extension.xlite.settings;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

@SuppressWarnings("deprecation")
public final class XLiteSettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Activity activity = getActivity();
        if (activity == null) throw new IllegalStateException("X-Lite settings activity is missing");

        Context preferenceContext = XLiteSettingsActivity.createPreferenceContext(activity);
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(preferenceContext);
        setPreferenceScreen(screen);
        SettingsRenderer.render(activity, screen);
    }
}
