/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import android.preference.Preference;

import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.instagram.patches.customise.font.FontStorage;

/**
 * Ties the rows of the font screen together: which font carries the mark, what is greyed out while
 * the switch is off, and when the screen that holds them has to hear about a change.
 */
public class FontSelection {

    /** What the screen holding the list has to do when the list changes. */
    public interface Listener {
        /** A font was added or removed, so the list itself has to be built again. */
        void onListChanged();
    }

    private final List<FontPref> rows = new ArrayList<>();

    /** Everything on the screen that means nothing while the switch is off. */
    private final List<Preference> dependents = new ArrayList<>();

    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** A font row: greyed out with the rest of the section, and redrawn when the choice moves. */
    public void registerRow(FontPref row) {
        rows.add(row);
        registerDependent(row);
    }

    /** Registers a row that is greyed out and unclickable while the switch is off. */
    public void registerDependent(Preference preference) {
        dependents.add(preference);
        preference.setEnabled(FontStorage.isEnabled());
    }

    /** Greys out or brings back everything below the switch. */
    public void notifyEnabledChanged() {
        boolean enabled = FontStorage.isEnabled();
        for (Preference preference : dependents) {
            preference.setEnabled(enabled);
        }
        notifySelectionChanged();
    }

    /** Moves the mark onto whichever font is chosen now. */
    void notifySelectionChanged() {
        for (FontPref row : rows) {
            row.refresh();
        }
    }

    /** The list no longer matches the fonts that are there, which a row cannot fix on its own. */
    void notifyListChanged() {
        if (listener != null) {
            listener.onListChanged();
        }
    }
}
