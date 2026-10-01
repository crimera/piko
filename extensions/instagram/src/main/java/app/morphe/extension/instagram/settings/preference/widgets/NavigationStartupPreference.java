/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;
import android.preference.ListPreference;
import android.preference.Preference;
import android.view.View;
import android.view.ViewGroup;

import app.morphe.extension.instagram.patches.navigation.NavigationBarPatch;
import app.morphe.extension.instagram.patches.navigation.NavigationBarPatch.Config;
import app.morphe.extension.instagram.patches.navigation.NavigationBarPatch.Tab;

import java.util.ArrayList;
import java.util.List;

public class NavigationStartupPreference extends ListPreference {
    private Config config;

    public NavigationStartupPreference(Context context) {
        super(InstagramPreferenceStyle.dialogContext(context));
        setPersistent(false);
        setOnPreferenceChangeListener(this::saveStartup);
        refreshChoices();
    }

    @Override
    protected void onClick() {
        refreshChoices();
        if (getEntries().length == 0) return;
        super.onClick();
    }

    private void refreshChoices() {
        config = NavigationBarPatch.loadConfig();
        List<Tab> choices = new ArrayList<>();
        for (Tab tab : config.order()) {
            if (tab != Tab.CREATE && config.visible().contains(tab)) choices.add(tab);
        }
        CharSequence[] entries = new CharSequence[choices.size()];
        CharSequence[] values = new CharSequence[choices.size()];
        for (int index = 0; index < choices.size(); index++) {
            Tab tab = choices.get(index);
            entries[index] = str(tab.labelName());
            values[index] = tab.key();
        }
        setEntries(entries);
        setEntryValues(values);
        setValue(config.startup().key());
    }

    private boolean saveStartup(Preference preference, Object value) {
        if (!(value instanceof String)) return false;
        Tab tab = Tab.fromKey((String) value);
        if (tab == null || tab == Tab.CREATE || !config.visible().contains(tab)) return false;
        if (!NavigationBarPatch.saveConfig(config.order(), config.visible(), tab)) return false;
        config = NavigationBarPatch.loadConfig();
        return true;
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        return InstagramPreferenceStyle.createPreferenceView(
                getContext(),
                InstagramPreferenceStyle.TRAILING_CHEVRON
        );
    }

    @Override
    protected void onBindView(View view) {
        InstagramPreferenceStyle.bindText(this, view);
    }
}
