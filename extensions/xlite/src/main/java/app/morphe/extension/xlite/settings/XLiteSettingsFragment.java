package app.morphe.extension.xlite.settings;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.View;
import android.widget.ListView;

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

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        View root = getView();
        if (root == null) return;

        View list = root.findViewById(android.R.id.list);
        if (!(list instanceof ListView listView)) return;

        int verticalPadding = Math.round(8 * getResources().getDisplayMetrics().density);
        int backgroundColor = XLitePreferenceStyle.backgroundColor(root.getContext());
        root.setBackgroundColor(backgroundColor);
        listView.setBackgroundColor(backgroundColor);
        listView.setPadding(0, verticalPadding, 0, verticalPadding);
        listView.setClipToPadding(false);
        listView.setDivider(null);
        listView.setDividerHeight(0);
    }
}
