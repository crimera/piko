package app.morphe.extension.newx.misc;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.shared.settings.StringSetting;

/**
 * Editor-managed NewX navigation bar configuration: item order, hidden items, and per-item
 * replacement destinations.
 *
 * <p>The values are extension-owned settings rather than registry entries because they are managed
 * by the drag-and-drop editor screen, not by generated single-choice rows.
 */
public final class NavBarConfig {
    public static final int MAX_VISIBLE_ITEMS = 5;

    private static final String ORDER_KEY = "newx.navigation.items.order";
    private static final String HIDDEN_KEY = "newx.navigation.items.hidden";
    private static final String REPLACEMENT_KEY = "newx.navigation.items.replacement";
    private static final String DESTINATION_FIELD = "d";

    private final StringSetting order = new StringSetting(ORDER_KEY, "");
    private final StringSetting hidden = new StringSetting(HIDDEN_KEY, "");
    private final StringSetting replacement = new StringSetting(REPLACEMENT_KEY, "");

    private NavBarConfig() {
    }

    public static NavBarConfig shared() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final NavBarConfig INSTANCE = new NavBarConfig();
    }

    /** Stored order first, then any tab the app added and the stored order does not know yet. */
    public List<String> orderedTabs(List<String> available) {
        List<String> stored = parseList(order.get());
        List<String> result = new ArrayList<>(available.size());
        for (String tab : stored) {
            if (available.contains(tab) && !result.contains(tab)) result.add(tab);
        }
        for (String tab : available) {
            if (!result.contains(tab)) result.add(tab);
        }
        return result;
    }

    /** Returns the configured native slots that can be shown, capped by the app's bar capacity. */
    public List<String> shownTabs(List<String> available) {
        Set<String> hidden = hiddenTabs();
        List<String> result = new ArrayList<>(Math.min(available.size(), MAX_VISIBLE_ITEMS));
        for (String tab : orderedTabs(available)) {
            if (hidden.contains(tab)) continue;
            result.add(tab);
            if (result.size() == MAX_VISIBLE_ITEMS) break;
        }
        return result;
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

    /** @return the drawer destination id for the tab, or {@code null} when unchanged. */
    @Nullable
    public String destinationFor(String tab) {
        JSONObject entry = replacementEntry(tab);
        if (entry == null) return null;
        String destination = entry.optString(DESTINATION_FIELD, "");
        return destination.isEmpty() ? null : destination;
    }

    public void setReplacement(String tab, @Nullable String destinationId) {
        try {
            JSONObject replacements = replacements();
            boolean hasDestination = destinationId != null && !destinationId.isEmpty();
            if (!hasDestination) {
                replacements.remove(tab);
            } else {
                removeDestinationFromOtherTabs(replacements, tab, destinationId);
                JSONObject entry = new JSONObject();
                entry.put(DESTINATION_FIELD, destinationId);
                replacements.put(tab, entry);
            }
            this.replacement.save(replacements.toString());
        } catch (JSONException exception) {
            NewXLogger.printException(
                    () -> "Failed to save the NewX navigation bar replacement for " + tab,
                    exception
            );
        }
    }

    private static void removeDestinationFromOtherTabs(
            JSONObject replacements,
            String tab,
            String destinationId
    ) throws JSONException {
        List<String> tabs = new ArrayList<>();
        Iterator<String> keys = replacements.keys();
        while (keys.hasNext()) tabs.add(keys.next());

        for (String otherTab : tabs) {
            if (tab.equals(otherTab)) continue;
            JSONObject entry = replacements.optJSONObject(otherTab);
            if (entry == null || !destinationId.equals(entry.optString(DESTINATION_FIELD, ""))) {
                continue;
            }
            replacements.remove(otherTab);
        }
    }

    @Nullable
    private JSONObject replacementEntry(String tab) {
        if (tab == null || tab.isEmpty()) return null;
        try {
            JSONObject replacements = replacements();
            JSONObject entry = replacements.optJSONObject(tab);
            if (entry == null) return null;
            if (entry.optString(DESTINATION_FIELD, "").isEmpty()) return null;
            return entry;
        } catch (JSONException exception) {
            NewXLogger.printException(
                    () -> "Failed to read the NewX navigation bar replacement for " + tab,
                    exception
            );
            return null;
        }
    }

    private JSONObject replacements() throws JSONException {
        String stored = replacement.get();
        if (stored == null || stored.isEmpty()) return new JSONObject();
        return new JSONObject(stored);
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
