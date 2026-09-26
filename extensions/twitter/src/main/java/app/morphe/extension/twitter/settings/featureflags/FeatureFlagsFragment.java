/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings.featureflags;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Fragment;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.twitter.patches.FeatureSwitchPatch;
import app.morphe.extension.twitter.settings.ActivityHook;
import app.morphe.extension.twitter.settings.Settings;

@SuppressWarnings("deprecation")
public class FeatureFlagsFragment extends Fragment {
    ArrayList<FeatureFlag> flags;
    private final String bundleFlagNameKey = "flagName";
    private final String bundleFlagValueKey = "flagValue";
    private final FeatureFlagSelection selection = new FeatureFlagSelection();
    private boolean selecting;
    private Toolbar flagToolbar;
    private Runnable cancelSelection;
    private Runnable unregisterSelectionBack;
    private static final int FLAG_MENU_GROUP = 0x50494b4f;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        flags = new ArrayList<>();
        String flagsPref = app.morphe.extension.twitter.Utils.getStringPref(Settings.MISC_FEATURE_FLAGS);
        if (!flagsPref.isEmpty()) {
            for (String flag : flagsPref.split(",")) {
                String[] item = flag.split(":");
                flags.add(new FeatureFlag(item[0], Boolean.valueOf(item[1])));
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!selecting) ActivityHook.toolbar.setTitle(ResourceUtils.getString("piko_pref_feature_flags"));
        updateSelectionBack();
    }

    public static boolean handleBackPressed(android.app.Activity activity) {
        if (activity == null) return false;
        Fragment fragment = activity.getFragmentManager().findFragmentById(
                ResourceUtils.getIdentifier(ResourceType.ID, "fragment_container"));
        if (!(fragment instanceof FeatureFlagsFragment)) return false;
        FeatureFlagsFragment flagsFragment = (FeatureFlagsFragment) fragment;
        if (!flagsFragment.selecting || flagsFragment.cancelSelection == null) return false;
        flagsFragment.cancelSelection.run();
        return true;
    }

    private void updateSelectionBack() {
        if (!selecting) {
            if (unregisterSelectionBack != null) unregisterSelectionBack.run();
            unregisterSelectionBack = null;
        } else if (android.os.Build.VERSION.SDK_INT >= 33 && unregisterSelectionBack == null) {
            unregisterSelectionBack = Api33.register(getActivity(), () -> {
                if (cancelSelection != null) cancelSelection.run();
            });
        }
    }

    @android.annotation.TargetApi(33)
    private static final class Api33 {
        static Runnable register(android.app.Activity activity, Runnable cancel) {
            android.window.OnBackInvokedDispatcher dispatcher = activity.getOnBackInvokedDispatcher();
            android.window.OnBackInvokedCallback callback = cancel::run;
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback);
            return () -> dispatcher.unregisterOnBackInvokedCallback(callback);
        }
    }

    @Override
    public void onStop() {
        if (unregisterSelectionBack != null) unregisterSelectionBack.run();
        unregisterSelectionBack = null;
        super.onStop();
    }

    private void saveFlags() {
        app.morphe.extension.twitter.Utils.setStringPref(
                Settings.MISC_FEATURE_FLAGS.key, FeatureFlag.toStringPref(flags));
    }

    private EditText flagInput() {
        EditText input = new EditText(getContext());
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(ResourceUtils.getString("piko_pref_flag_name"));
        return input;
    }

    private boolean validateFlag(EditText input, int editingPosition) {
        String name = input.getText().toString().trim();
        if (!FeatureFlagCatalog.isValidName(name)) {
            input.setError(ResourceUtils.getString("piko_pref_flag_invalid_name"));
            return false;
        }
        for (int i = 0; i < flags.size(); i++) {
            if (i != editingPosition && flags.get(i).getName().trim().equals(name)) {
                input.setError(ResourceUtils.getString("piko_pref_flag_already_added"));
                return false;
            }
        }
        return true;
    }

    private void confirmFlagName(EditText input, int editingPosition, Runnable save) {
        if (!validateFlag(input, editingPosition)) return;
        String name = input.getText().toString().trim();
        FeatureFlagCatalog catalog = new FeatureFlagCatalog(FeatureSwitchPatch.FLAGS_SEARCH, flags);
        if (catalog.isKnown(name)) {
            save.run();
            return;
        }
        List<String> suggestions = catalog.corrections(name);
        if (suggestions.isEmpty()) {
            save.run();
            return;
        }
        new AlertDialog.Builder(getContext())
                .setTitle(ResourceUtils.getString("piko_pref_flag_verify_name"))
                .setItems(suggestions.toArray(new String[0]), (dialog, which) -> {
                    input.setText(suggestions.get(which));
                    if (validateFlag(input, editingPosition)) save.run();
                })
                .setNegativeButton(ResourceUtils.getString("cancel"), null)
                .show();
    }
    private void checkFlags(FeatureFlagAdapter adapter) {
        List<FeatureFlagAudit.Issue> issues = FeatureFlagAudit.inspect(flags,
                new FeatureFlagCatalog(FeatureSwitchPatch.FLAGS_SEARCH, flags));
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                .setTitle(ResourceUtils.getString("piko_pref_check_flags"))
                .setNegativeButton(ResourceUtils.getString("cancel"), null);
        if (issues.isEmpty()) {
            builder.setMessage(ResourceUtils.getString("piko_pref_flag_check_clear"));
        } else {
            String[] labels = new String[issues.size()];
            for (int i = 0; i < issues.size(); i++) {
                FeatureFlagAudit.Issue issue = issues.get(i);
                String status = issue.copies > 1
                        ? ResourceUtils.getString("piko_pref_flag_duplicates") + " (" + issue.copies + ")"
                        : ResourceUtils.getString(issue.suggestions.isEmpty()
                                ? "piko_pref_flag_unverified_short" : "piko_pref_flag_typo_short");
                labels[i] = status + "\n" + flags.get(issue.position).getName();
            }
            builder.setItems(labels, (dialog, which) -> showFlagIssue(adapter, issues.get(which)));
        }
        builder.show();
    }

    private void showFlagIssue(FeatureFlagAdapter adapter, FeatureFlagAudit.Issue issue) {
        FeatureFlag flag = flags.get(issue.position);
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                .setTitle(ResourceUtils.getString("piko_pref_check_flags"))
                .setNegativeButton(ResourceUtils.getString("cancel"), null);
        if (issue.copies > 1) {
            String message = flag.getName() + "\n\n" + ResourceUtils.getString("piko_pref_flag_duplicates") + ": " + issue.copies + "\n\n"
                    + ResourceUtils.getString(issue.conflicting ? "piko_pref_flag_merge_conflict" : "piko_pref_flag_merge_same");
            if (!issue.conflicting) message += "\n" + ResourceUtils.getString(flag.getEnabled()
                    ? "piko_pref_flags_enable" : "piko_pref_flags_disable");
            builder.setMessage(message);
            if (issue.conflicting) {
                builder.setPositiveButton(ResourceUtils.getString("piko_pref_flag_keep_on"),
                        (dialog, which) -> mergeFlagDuplicates(adapter, flag.getName(), true));
                builder.setNeutralButton(ResourceUtils.getString("piko_pref_flag_keep_off"),
                        (dialog, which) -> mergeFlagDuplicates(adapter, flag.getName(), false));
            } else {
                builder.setPositiveButton(ResourceUtils.getString("piko_pref_flag_merge"),
                        (dialog, which) -> mergeFlagDuplicates(adapter, flag.getName(), flag.getEnabled()));
            }
        } else {
            String message = flag.getName() + "\n\n" + ResourceUtils.getString("piko_pref_flag_unknown");
            if (!issue.suggestions.isEmpty()) message += "\n\n"
                    + ResourceUtils.getString("piko_pref_flag_suggested_name") + "\n"
                    + String.join("\n", issue.suggestions);
            builder.setMessage(message)
                    .setPositiveButton(ResourceUtils.getString("piko_pref_edit_flag_title"),
                            (dialog, which) -> modifyFlag(adapter, issue.position));
        }
        builder.show();
    }

    private void mergeFlagDuplicates(FeatureFlagAdapter adapter, String name, boolean enabled) {
        FeatureFlagAudit.mergeDuplicates(flags, name.trim(), enabled);
        saveFlags();
        adapter.notifyDataSetChanged();
        checkFlags(adapter);
    }
    public void modifyFlag(FeatureFlagAdapter adapter, int position) {
        try {
            FeatureFlag flag = flags.get(position);

            AlertDialog.Builder dia = new AlertDialog.Builder(getContext());
            dia.setTitle(ResourceUtils.getString("piko_pref_edit_flag_title"));

            LinearLayout ln = new LinearLayout(getContext());
            ln.setPadding(50, 50, 50, 50);

            EditText flagEditText = flagInput();
            flagEditText.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            flagEditText.setText(flag.getName());
            ln.addView(flagEditText);

            dia.setPositiveButton(ResourceUtils.getString("save"), null);

            dia.setNeutralButton(ResourceUtils.getString("remove"), ((dialogInterface, i) -> {
                flags.remove(position);
                adapter.notifyDataSetChanged();
                saveFlags();
            }));

            dia.setNegativeButton(ResourceUtils.getString("cancel"), null);

            dia.setView(ln);

            AlertDialog dialog = dia.create();
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                    confirmFlagName(flagEditText, position, () -> {
                String name = flagEditText.getText().toString().trim();
                if (!name.equals(flag.getName())) {
                    flags.set(position, new FeatureFlag(name, flag.getEnabled()));
                    adapter.notifyDataSetChanged();
                    saveFlags();
                }
                dialog.dismiss();
            }));
        } catch (Exception exception){
            Utils.showToastShort(exception.toString());
        }
    }

    private void searchFlagsDialog(FeatureFlagAdapter parentAdapter) {
        AlertDialog.Builder dia = new AlertDialog.Builder(getContext());
        dia.setTitle(ResourceUtils.getString("piko_pref_add_flag_title"));

        @SuppressLint({"NewApi", "LocalSuppress"}) View view = getLayoutInflater().inflate(
                ResourceUtils.getIdentifier(ResourceType.LAYOUT, "search_dialog"), null);
        ListView listView = view.findViewById(
                ResourceUtils.getIdentifier(ResourceType.ID, "featureFlagsSearchListView"));
        EditText filter = view.findViewById(
                ResourceUtils.getIdentifier(ResourceType.ID, "filterEditText"));

        FeatureFlagCatalog catalog = new FeatureFlagCatalog(FeatureSwitchPatch.FLAGS_SEARCH, flags);
        FeatureFlagSearchAdapter adapter = new FeatureFlagSearchAdapter(view.getContext(), catalog);
        listView.setAdapter(adapter);
        adapter.notifyDataSetChanged();

        filter.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                adapter.filter(charSequence.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });

        dia.setView(view);
        dia.setNegativeButton(ResourceUtils.getString("cancel"), null);
        dia.setPositiveButton(ResourceUtils.getString("piko_pref_add_selected_flags"), (dialogInterface, which) -> {
            for (String name : catalog.selected()) {
                if (FeatureFlagCatalog.indexOf(flags, name) < 0) flags.add(new FeatureFlag(name, true));
            }
            parentAdapter.notifyDataSetChanged();
            saveFlags();
        });
        AlertDialog dialog = dia.create();

        listView.setOnItemClickListener((adapterView, view1, i, l) -> {
            catalog.toggle(adapter.getItem(i));
            adapter.notifyDataSetChanged();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!catalog.selected().isEmpty());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(
                    ResourceUtils.getString("piko_pref_add_selected_flags") + " (" + catalog.selected().size() + ")");
        });

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
    }

    public void addFlag(FeatureFlagAdapter adapter, Bundle bundle) {
        boolean defaultFlagValue = true;
        AlertDialog.Builder dia = new AlertDialog.Builder(getContext());
        dia.setTitle(ResourceUtils.getString("piko_pref_add_flag_title"));

        LinearLayout ln = new LinearLayout(getContext());
        ln.setPadding(50, 50, 50, 50);

        EditText flagEditText = flagInput();
        // If flag is sent via deepLink.
        if (bundle != null && bundle.containsKey(bundleFlagNameKey)) { // If flag is sent.
            flagEditText.setText(bundle.getString(bundleFlagNameKey));
            defaultFlagValue =  bundle.getBoolean(bundleFlagValueKey, defaultFlagValue);
        }
        flagEditText.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ln.addView(flagEditText);
        final boolean flagValue = defaultFlagValue;
        dia.setPositiveButton(ResourceUtils.getString("save"), null);

        dia.setNeutralButton(ResourceUtils.getString("piko_pref_search_flags"), ((dialogInterface, i) -> {
            searchFlagsDialog(adapter);
        }));

        dia.setNegativeButton(ResourceUtils.getString("cancel"), null);

        dia.setView(ln);

        AlertDialog dialog = dia.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v ->
                confirmFlagName(flagEditText, -1, () -> {
            flags.add(new FeatureFlag(flagEditText.getText().toString().trim(), flagValue));
            adapter.notifyDataSetChanged();
            saveFlags();
            dialog.dismiss();
        }));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        Bundle bundle = getArguments(); // Required for deepLink flag addition.
        View view = inflater.inflate(ResourceUtils.getIdentifier(ResourceType.LAYOUT, "feature_flags_view"), container, false);
        FloatingActionButton floatingActionButton = view.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, "add_flag"));

        ListView rc = view.findViewById(ResourceUtils.getIdentifier(ResourceType.ID, "list"));
        rc.setClipToPadding(false);
        rc.setPadding(0, 0, 0, 200);

        FeatureFlagAdapter adapter = new FeatureFlagAdapter(getContext(), flags);
        selecting = false;
        selection.clear();
        flagToolbar = ActivityHook.toolbar;
        Runnable refreshSelection = () -> refreshToolbar(adapter, floatingActionButton);
        refreshSelection.run();
        floatingActionButton.setOnClickListener(view1 -> addFlag(adapter, bundle));

        adapter.setItemClickListener(position -> {
            if (selecting) {
                selection.toggle(position);
                refreshSelection.run();
            } else modifyFlag(adapter, position);
        });
        adapter.setItemLongClickListener(position -> {
            selecting = true;
            selection.toggle(position);
            refreshSelection.run();
        });

        adapter.setItemCheckedChangeListener((checked, position) -> {
            FeatureFlag flag = flags.get(position);
            flags.set(position, new FeatureFlag(flag.getName(), checked));
            saveFlags();
        });

        rc.setAdapter(adapter);

        // If flag is sent via deepLink.
        if (bundle != null && bundle.containsKey(bundleFlagNameKey)) { // If flag is sent.
            addFlag(adapter, bundle);
            bundle.remove(bundleFlagNameKey);
            bundle.remove(bundleFlagValueKey);
        }

        return view;
    }

    private void refreshToolbar(FeatureFlagAdapter adapter, FloatingActionButton addButton) {
        adapter.setSelection(selecting ? selection : null);
        addButton.setVisibility(selecting ? View.GONE : View.VISIBLE);
        flagToolbar.getMenu().removeGroup(FLAG_MENU_GROUP);
        flagToolbar.setTitle(selecting ? String.format(java.util.Locale.getDefault(),
                ResourceUtils.getString("piko_pref_flags_selected"), selection.size())
                : ResourceUtils.getString("piko_pref_feature_flags"));
        Runnable finish = () -> {
            selecting = false;
            selection.clear();
            refreshToolbar(adapter, addButton);
        };
        cancelSelection = finish;
        updateSelectionBack();
        flagToolbar.setNavigationOnClickListener(v -> {
            if (selecting) finish.run();
            else getActivity().onBackPressed();
        });
        if (!selecting) {
            toolbarItem("piko_pref_select_flags", false, true, () -> {
                selecting = true;
                refreshToolbar(adapter, addButton);
            });
            toolbarItem("piko_pref_check_flags", false, true, () -> checkFlags(adapter));
            android.graphics.drawable.Drawable overflow = flagToolbar.getContext().getDrawable(
                    ResourceUtils.getIdentifier(ResourceType.DRAWABLE, "ic_vector_overflow"));
            if (overflow != null) {
                overflow = overflow.mutate();
                overflow.setTint(app.morphe.extension.twitter.Utils.resolveColor(
                        flagToolbar.getContext(), "coreColorPrimaryText"));
                flagToolbar.setOverflowIcon(overflow);
            }
        } else {
            toolbarItem("remove", true, selection.size() > 0, () ->
                    new AlertDialog.Builder(getContext())
                            .setMessage(ResourceUtils.getString("piko_pref_flags_remove_selected") + " (" + selection.size() + ")")
                            .setNegativeButton(ResourceUtils.getString("cancel"), null)
                            .setPositiveButton(ResourceUtils.getString("remove"), (dialog, which) -> {
                                selection.remove(flags);
                                saveFlags();
                                finish.run();
                            }).show());
        }
    }

    private void toolbarItem(String title, boolean visible, boolean enabled, Runnable action) {
        android.view.MenuItem item = flagToolbar.getMenu().add(FLAG_MENU_GROUP, 0, 0, ResourceUtils.getString(title));
        item.setShowAsAction(visible ? android.view.MenuItem.SHOW_AS_ACTION_ALWAYS : android.view.MenuItem.SHOW_AS_ACTION_NEVER);
        item.setEnabled(enabled);
        item.setOnMenuItemClickListener(clicked -> {
            action.run();
            return true;
        });
    }

    @Override
    public void onDestroyView() {
        if (unregisterSelectionBack != null) unregisterSelectionBack.run();
        unregisterSelectionBack = null;
        cancelSelection = null;
        if (flagToolbar != null) {
            flagToolbar.getMenu().removeGroup(FLAG_MENU_GROUP);
            android.app.Activity activity = getActivity();
            if (activity != null) flagToolbar.setNavigationOnClickListener(v -> activity.onBackPressed());
            flagToolbar = null;
        }
        selecting = false;
        selection.clear();
        super.onDestroyView();
    }
}
