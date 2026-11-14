package app.morphe.extension.newx.misc;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.morphe.extension.shared.settings.StringSetting;

/**
 * Editor-managed NewX profile tab configuration: tab order and hidden tabs.
 *
 * <p>The values are extension-owned settings rather than registry entries because they are managed
 * by the drag-and-drop editor screen, not by generated single-choice rows.
 */
public final class ProfileTabsConfig {
    private static final String ORDER_KEY = "newx.profile.tabs.order";
    private static final String HIDDEN_KEY = "newx.profile.tabs.hidden";

    private final StringSetting order = new StringSetting(ORDER_KEY, "");
    private final StringSetting hidden = new StringSetting(HIDDEN_KEY, "");

    /** Package-private so tests can build an isolated configuration. */
    ProfileTabsConfig() {
    }

    public static ProfileTabsConfig shared() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final ProfileTabsConfig INSTANCE = new ProfileTabsConfig();
    }

    /** Stored order first, then any tab the app added and the stored order does not know yet. */
    public List<String> orderedTabs(List<String> available) {
        List<String> stored = storedOrder();
        List<String> result = new ArrayList<>(available.size());
        for (String tab : stored) {
            if (available.contains(tab) && !result.contains(tab)) result.add(tab);
        }
        for (String tab : available) {
            if (!result.contains(tab)) result.add(tab);
        }
        return result;
    }

    /** The raw stored order, including ids this build's catalog no longer registers. */
    public List<String> storedOrder() {
        return parseList(order.get());
    }

    public void saveOrder(List<String> tabs) {
        order.save(String.join(",", tabs));
    }

    public Set<String> hiddenTabs() {
        return new LinkedHashSet<>(parseList(hidden.get()));
    }

    public boolean isHidden(String tab) {
        return hiddenTabs().contains(tab);
    }

    public void setHidden(String tab, boolean hide) {
        LinkedHashSet<String> tabs = new LinkedHashSet<>(hiddenTabs());
        if (hide) {
            tabs.add(tab);
        } else {
            tabs.remove(tab);
        }
        hidden.save(String.join(",", tabs));
    }

    /** Clears the stored order and hidden set, restoring the app's default tab strip. */
    public void reset() {
        order.save("");
        hidden.save("");
    }

    private static List<String> parseList(String value) {
        if (value == null || value.isEmpty()) return new ArrayList<>();
        List<String> items = new ArrayList<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty() && !items.contains(trimmed)) items.add(trimmed);
        }
        return items;
    }
}
