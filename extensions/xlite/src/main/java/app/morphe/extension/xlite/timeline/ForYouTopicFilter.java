package app.morphe.extension.xlite.timeline;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import app.morphe.extension.shared.settings.BooleanSetting;
import app.morphe.extension.shared.settings.StringSetting;

public final class ForYouTopicFilter {
    private static final String ENABLED_KEY = "xlite.timeline.topic_filtering.enabled";
    private static final String SELECTED_TOPIC_IDS_KEY = "xlite.timeline.topic_filtering.selected_topic_ids";
    private static final String TOPIC_CATALOG_KEY = "xlite.timeline.topic_filtering.topic_catalog";
    private static final String HOME_FILTER_OPTION_PREFIX = "HomeFilterOption(identifier=";
    private static final String RAW_FILTER_OPTION_PREFIX = "Filter(label=";
    private static final String DISPLAY_NAME_SEPARATOR = ", displayName=";
    private static final String TAB_DISPLAY_NAME_SEPARATOR = ", tabDisplayName=";
    private static final String RAW_VALUE_SEPARATOR = ", value=";
    private static final String RAW_ICON_SEPARATOR = ", icon_name=";

    private static final Object LOCK = new Object();
    private static final Map<String, Topic> TOPIC_CATALOG = new LinkedHashMap<>();
    private static final CopyOnWriteArrayList<Runnable> TOPIC_LISTENERS = new CopyOnWriteArrayList<>();
    private static boolean topicCatalogLoaded;

    public static final class Settings {
        public final BooleanSetting enabled = new BooleanSetting(ENABLED_KEY, false);
        public final StringSetting selectedTopicIds =
                new StringSetting(SELECTED_TOPIC_IDS_KEY, "");
        public final StringSetting topicCatalog =
                new StringSetting(TOPIC_CATALOG_KEY, "");

        private Settings() {
        }
    }

    private ForYouTopicFilter() {
    }

    public static Settings shared() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final Settings INSTANCE = new Settings();
    }

    public static final class Topic {
        private final String id;
        private final String name;

        private Topic(String id, String name) {
            this.id = Objects.requireNonNull(id);
            this.name = Objects.requireNonNull(name);
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Topic topic)) return false;
            return id.equals(topic.id) && name.equals(topic.name);
        }

        @Override
        public int hashCode() {
            return 31 * id.hashCode() + name.hashCode();
        }
    }

    /** Captures the normalized topic group emitted by HomeTimelineFilters. */
    public static void captureTopicOptions(
            @Nullable Object filterType,
            @Nullable Object options
    ) {
        if (!isTopicFilterType(filterType)) return;
        if (!(options instanceof Iterable<?> iterable)) return;

        LinkedHashMap<String, Topic> captured = new LinkedHashMap<>();
        for (Object option : iterable) {
            if (option == null) continue;
            Topic topic = parseTopicOptionText(String.valueOf(option));
            if (topic != null) captured.put(topic.id, topic);
        }
        if (captured.isEmpty()) return;

        Settings settings = shared();
        boolean changed;
        synchronized (LOCK) {
            loadTopicCatalogLocked(settings);
            changed = !TOPIC_CATALOG.equals(captured);
            if (!changed) return;
            TOPIC_CATALOG.clear();
            TOPIC_CATALOG.putAll(captured);
            saveTopicCatalogLocked(settings);
        }
        notifyTopicListeners();
    }

    public static void addTopicCatalogListener(Runnable listener) {
        TOPIC_LISTENERS.add(Objects.requireNonNull(listener));
    }

    public static void removeTopicCatalogListener(Runnable listener) {
        TOPIC_LISTENERS.remove(listener);
    }

    public static List<Topic> topicOptions() {
        Settings settings = shared();
        synchronized (LOCK) {
            loadTopicCatalogLocked(settings);
            return Collections.unmodifiableList(new ArrayList<>(TOPIC_CATALOG.values()));
        }
    }

    @Nullable
    public static List<String> resolveForYouTopicIds(@Nullable List<String> originalTopicIds) {
        return resolveForYouTopicIds(
                originalTopicIds,
                shared().enabled.get(),
                parseTopicIds(shared().selectedTopicIds.get())
        );
    }

    @Nullable
    public static List<String> resolveForYouTopicIds(
            @Nullable List<String> originalTopicIds,
            boolean enabled,
            @Nullable Set<String> configuredTopicIds
    ) {
        if (!enabled || configuredTopicIds == null || configuredTopicIds.isEmpty()) {
            return originalTopicIds;
        }

        LinkedHashSet<String> validTopicIds = new LinkedHashSet<>();
        for (String topicId : configuredTopicIds) {
            if (isPositiveTopicId(topicId)) validTopicIds.add(topicId);
        }
        if (validTopicIds.isEmpty()) return originalTopicIds;
        return new ArrayList<>(validTopicIds);
    }

    @Nullable
    static Topic parseTopicOptionText(@Nullable String text) {
        if (text == null) return null;
        if (text.startsWith(HOME_FILTER_OPTION_PREFIX)) {
            return parseHomeFilterOption(text);
        }
        if (text.startsWith(RAW_FILTER_OPTION_PREFIX)) {
            return parseRawFilterOption(text);
        }
        return null;
    }

    static boolean isPositiveTopicId(@Nullable String topicId) {
        if (topicId == null || topicId.isEmpty()) return false;
        boolean hasNonZeroDigit = false;
        for (int index = 0; index < topicId.length(); index++) {
            char character = topicId.charAt(index);
            if (character < '0' || character > '9') return false;
            if (character != '0') hasNonZeroDigit = true;
        }
        return hasNonZeroDigit;
    }

    @Nullable
    private static Topic parseHomeFilterOption(String text) {
        int idStart = HOME_FILTER_OPTION_PREFIX.length();
        int nameStart = text.indexOf(DISPLAY_NAME_SEPARATOR, idStart);
        if (nameStart < 0) return null;
        int nameValueStart = nameStart + DISPLAY_NAME_SEPARATOR.length();
        int nameEnd = text.indexOf(TAB_DISPLAY_NAME_SEPARATOR, nameValueStart);
        if (nameEnd < 0) return null;
        return createTopic(text.substring(idStart, nameStart), text.substring(nameValueStart, nameEnd));
    }

    @Nullable
    private static Topic parseRawFilterOption(String text) {
        int nameStart = RAW_FILTER_OPTION_PREFIX.length();
        int idStart = text.indexOf(RAW_VALUE_SEPARATOR, nameStart);
        if (idStart < 0) return null;
        String name = text.substring(nameStart, idStart);
        int valueStart = idStart + RAW_VALUE_SEPARATOR.length();
        int valueEnd = text.indexOf(RAW_ICON_SEPARATOR, valueStart);
        if (valueEnd < 0) return null;
        return createTopic(text.substring(valueStart, valueEnd), name);
    }

    @Nullable
    private static Topic createTopic(String id, String name) {
        String normalizedName = name.trim();
        if (!isPositiveTopicId(id) || normalizedName.isEmpty()) return null;
        return new Topic(id, normalizedName);
    }

    private static boolean isTopicFilterType(@Nullable Object filterType) {
        if (filterType == null) return false;
        String value = filterType instanceof Enum<?> enumValue
                ? enumValue.name()
                : String.valueOf(filterType);
        return "TOPIC".equalsIgnoreCase(value)
                || "TOPIC".equalsIgnoreCase(value.substring(value.lastIndexOf('.') + 1));
    }

    static Set<String> parseTopicIds(@Nullable String serialized) {
        if (serialized == null || serialized.isEmpty()) return Collections.emptySet();
        LinkedHashSet<String> topicIds = new LinkedHashSet<>();
        for (String topicId : serialized.split(",")) {
            if (isPositiveTopicId(topicId)) topicIds.add(topicId);
        }
        return Collections.unmodifiableSet(topicIds);
    }

    private static void loadTopicCatalogLocked(Settings settings) {
        if (topicCatalogLoaded) return;
        topicCatalogLoaded = true;
        String serialized = settings.topicCatalog.get();
        if (serialized.isEmpty()) return;

        try {
            JSONArray array = new JSONArray(serialized);
            for (int index = 0; index < array.length(); index++) {
                JSONObject object = array.optJSONObject(index);
                if (object == null) continue;
                Topic topic = createTopic(object.optString("id", ""), object.optString("name", ""));
                if (topic != null) TOPIC_CATALOG.put(topic.id, topic);
            }
        } catch (JSONException ignored) {
            settings.topicCatalog.save("");
        }
    }

    private static void saveTopicCatalogLocked(Settings settings) {
        JSONArray array = new JSONArray();
        for (Topic topic : TOPIC_CATALOG.values()) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", topic.id);
                object.put("name", topic.name);
                array.put(object);
            } catch (JSONException ignored) {
                return;
            }
        }
        settings.topicCatalog.save(array.toString());
    }

    private static void notifyTopicListeners() {
        for (Runnable listener : TOPIC_LISTENERS) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // A settings-screen observer must not break GraphQL response handling.
            }
        }
    }
}
