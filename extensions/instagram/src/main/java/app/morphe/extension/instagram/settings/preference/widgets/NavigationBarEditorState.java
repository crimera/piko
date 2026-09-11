/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import app.morphe.extension.instagram.patches.navigation.NavigationBarPatch;
import app.morphe.extension.instagram.patches.navigation.NavigationBarPatch.Config;
import app.morphe.extension.instagram.patches.navigation.NavigationBarPatch.Tab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

final class NavigationBarEditorState {
    private final List<Tab> order;
    private final EnumSet<Tab> visible;

    private NavigationBarEditorState(Config config) {
        order = new ArrayList<>(config.order());
        visible = EnumSet.copyOf(config.visible());
    }

    static NavigationBarEditorState from(Config config) {
        return new NavigationBarEditorState(config);
    }

    List<Tab> order() {
        return Collections.unmodifiableList(order);
    }

    Set<Tab> visible() {
        return Collections.unmodifiableSet(visible);
    }


    void setVisible(Tab tab, boolean checked) {
        if (checked) visible.add(tab); else visible.remove(tab);
    }

    void move(Tab tab, int target) {
        int source = order.indexOf(tab);
        if (source < 0 || target < 0 || target >= order.size() || source == target) return;
        order.remove(source);
        order.add(target, tab);
    }

    void reset() {
        Config defaults = NavigationBarPatch.defaultConfig();
        order.clear();
        order.addAll(defaults.order());
        visible.clear();
        visible.addAll(defaults.visible());
    }
}
