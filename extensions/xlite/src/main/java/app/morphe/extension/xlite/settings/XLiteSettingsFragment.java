package app.morphe.extension.xlite.settings;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.view.View;
import android.widget.ListView;

import java.util.List;

import app.morphe.extension.shared.StringRef;

@SuppressWarnings("deprecation")
public final class XLiteSettingsFragment extends PreferenceFragment {
    private static final String GROUP_ID_ARGUMENT = "group_id";
    private SettingsNode.Group group;
    private PreferenceScreen screen;
    private SettingsSearchField searchField;

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
        screen = getPreferenceManager().createPreferenceScreen(preferenceContext);
        setPreferenceScreen(screen);
        if (group == null) {
            renderRoot();
            return;
        }
        SettingsRenderer.renderGroup(
                activity,
                screen,
                group,
                this::openGroup,
                this::openScreen
        );
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (group != null) return;

        View list = view.findViewById(android.R.id.list);
        if (!(list instanceof ListView listView)) return;

        Context context = requireActivity();
        searchField = new SettingsSearchField(context);
        listView.addHeaderView(searchField, null, false);

        searchField.setOnQueryChangedListener(query -> {
            SettingsSearchSession.update(query);
            renderRoot();
        });
        searchField.setQuery(SettingsSearchSession.query());
        renderRoot();
    }

    private void renderRoot() {
        if (group != null || screen == null) return;

        String query = SettingsSearchSession.query();
        screen.removeAll();
        if (query.trim().isEmpty()) {
            setSearchEmptyState(false, query);
            SettingsRenderer.render(screen, this::openGroup);
            return;
        }

        List<SettingsSearchMatcher.Match> matches =
                SettingsSearchMatcher.match(SettingsSearchIndex.results(), query);
        int resultCount = SettingsRenderer.renderSearchResults(
                requireActivity(),
                screen,
                query,
                matches,
                this::openScreen
        );
        setSearchEmptyState(resultCount == 0, query);
    }


    private void setSearchEmptyState(boolean visible, String query) {
        if (searchField == null) return;
        CharSequence message = StringRef.str(
                "piko_xlite_settings_search_no_results",
                query == null ? "" : query.trim()
        );
        searchField.setNoResults(visible, message);
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

    private void openScreen(SettingsNode.CustomScreen screen) {
        Activity activity = requireActivity();
        try {
            Fragment fragment = instantiateFragment(activity, screen.fragmentClassDescriptor);
            int containerId = app.morphe.extension.shared.ResourceUtils.getIdentifierOrThrow(
                    activity,
                    app.morphe.extension.shared.ResourceType.ID,
                    "fragment_container"
            );
            getFragmentManager()
                    .beginTransaction()
                    .replace(containerId, fragment)
                    .addToBackStack(screen.id)
                    .commit();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not open X-Lite custom screen: " + screen.id, exception);
        }
    }

    private static Fragment instantiateFragment(Activity activity, String descriptor)
            throws ReflectiveOperationException {
        String className = descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
        Class<?> fragmentClass = Class.forName(className, true, activity.getClassLoader());
        if (!Fragment.class.isAssignableFrom(fragmentClass)) {
            throw new IllegalArgumentException("Not an Android fragment: " + className);
        }
        return (Fragment) fragmentClass.getDeclaredConstructor().newInstance();
    }

    private Activity requireActivity() {
        Activity activity = getActivity();
        if (activity == null) throw new IllegalStateException("X-Lite settings activity is missing");
        return activity;
    }

    @Override
    public void onDestroy() {
        Activity activity = getActivity();
        if (group == null && activity != null && activity.isFinishing()) {
            SettingsSearchSession.reset();
        }
        super.onDestroy();
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
