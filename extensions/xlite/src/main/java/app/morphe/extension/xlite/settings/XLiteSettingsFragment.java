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
    private static final String GROUP_ID_ARGUMENT = "group_id";
    private SettingsNode.Group group;

    static XLiteSettingsFragment forGroup(SettingsNode.Group group) {
        XLiteSettingsFragment fragment = new XLiteSettingsFragment();
        Bundle arguments = new Bundle();
        arguments.putString(GROUP_ID_ARGUMENT, group.id);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Activity activity = requireActivity();
        Bundle arguments = getArguments();
        if (arguments != null) {
            String groupId = arguments.getString(GROUP_ID_ARGUMENT);
            if (groupId != null) group = SettingsRegistry.getGroup(groupId);
        }

        Context preferenceContext = XLiteSettingsActivity.createPreferenceContext(activity);
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(preferenceContext);
        setPreferenceScreen(screen);
        if (group == null) {
            SettingsRenderer.render(activity, screen, this::openGroup);
            return;
        }
        SettingsRenderer.renderGroup(activity, screen, group, this::openGroup);
    }

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = requireActivity();
        if (!(activity instanceof XLiteSettingsActivity settingsActivity)) return;
        settingsActivity.setPageTitle(
                group == null
                        ? app.morphe.extension.shared.StringRef.str("piko_xlite_settings_title")
                        : group.title.toString()
        );
    }

    private void openGroup(SettingsNode.Group group) {
        Activity activity = requireActivity();
        int containerId = app.morphe.extension.shared.ResourceUtils.getIdentifierOrThrow(
                activity,
                app.morphe.extension.shared.ResourceType.ID,
                "fragment_container"
        );
        getFragmentManager()
                .beginTransaction()
                .replace(containerId, forGroup(group))
                .addToBackStack(group.id)
                .commit();
    }

    private Activity requireActivity() {
        Activity activity = getActivity();
        if (activity == null) throw new IllegalStateException("X-Lite settings activity is missing");
        return activity;
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
