package app.morphe.extension.newx.featureswitches;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import app.morphe.extension.newx.settings.NewXLogger;
import app.morphe.extension.shared.settings.StringSetting;

public final class FeatureSwitchStore {
    private static final int SCHEMA_VERSION = 1;
    private static final String PERSISTENCE_KEY = "newx.advanced.feature_switches.overrides";
    private static final String SEEN_PERSISTENCE_KEY = "newx.advanced.feature_switches.seen";

    public enum ValueType {
        BOOLEAN,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        STRING,
        STRING_LIST,
    }

    public static final class Entry {
        private final String key;
        private final ValueType type;
        @Nullable private final Object observedValue;
        @Nullable private final Object effectiveValue;
        private final boolean overridden;
        private final boolean isNew;

        private Entry(
                String key,
                ValueType type,
                @Nullable Object observedValue,
                @Nullable Object effectiveValue,
                boolean overridden,
                boolean isNew
        ) {
            this.key = key;
            this.type = type;
            this.observedValue = copyValue(observedValue);
            this.effectiveValue = copyValue(effectiveValue);
            this.overridden = overridden;
            this.isNew = isNew;
        }

        public String getKey() {
            return key;
        }

        public ValueType getType() {
            return type;
        }

        @Nullable
        public Object getObservedValue() {
            return copyValue(observedValue);
        }

        @Nullable
        public Object getEffectiveValue() {
            return copyValue(effectiveValue);
        }

        public boolean isOverridden() {
            return overridden;
        }

        public boolean isNew() {
            return isNew;
        }
    }

    interface Persistence {
        String read();
        void write(String value);
    }

    private static final class Observation {
        volatile ValueType type;
        @Nullable volatile Object value;

        Observation(ValueType type, @Nullable Object value) {
            this.type = type;
            this.value = copyValue(value);
        }

        void update(ValueType updatedType, @Nullable Object updatedValue) {
            type = updatedType;
            value = copyValue(updatedValue);
        }
    }

    private static final class OverrideValue {
        final ValueType type;
        final Object value;

        OverrideValue(ValueType type, Object value) {
            this.type = type;
            this.value = copyValue(value);
        }
    }

    private final Object loadLock = new Object();
    private final Object persistenceLock = new Object();
    private final Object seenLock = new Object();
    private final Persistence persistence;
    private final Persistence seenPersistence;
    private final Map<String, Observation> observations = new ConcurrentHashMap<>();
    private final Map<String, OverrideValue> overrides = new ConcurrentHashMap<>();
    private final Set<String> seenKeys = ConcurrentHashMap.newKeySet();
    private volatile boolean loaded;
    private volatile boolean seenLoaded;
    private volatile boolean baselineEstablished;

    FeatureSwitchStore(Persistence persistence) {
        this(persistence, new MemoryPersistence());
    }

    FeatureSwitchStore(Persistence persistence, Persistence seenPersistence) {
        this.persistence = persistence;
        this.seenPersistence = seenPersistence;
    }

    public static FeatureSwitchStore shared() {
        return Holder.INSTANCE;
    }

    public static boolean resolveBoolean(String key, boolean currentValue) {
        return shared().resolve(key, ValueType.BOOLEAN, currentValue, Boolean.class);
    }

    public static int resolveInt(String key, int currentValue) {
        return shared().resolve(key, ValueType.INT, currentValue, Integer.class);
    }

    public static long resolveLong(String key, long currentValue) {
        return shared().resolve(key, ValueType.LONG, currentValue, Long.class);
    }

    public static float resolveFloat(String key, float currentValue) {
        return shared().resolve(key, ValueType.FLOAT, currentValue, Float.class);
    }

    public static double resolveDouble(String key, double currentValue) {
        return shared().resolve(key, ValueType.DOUBLE, currentValue, Double.class);
    }

    @Nullable
    public static String resolveString(String key, @Nullable String currentValue) {
        return shared().resolve(key, ValueType.STRING, currentValue, String.class);
    }

    @Nullable
    public static List<?> resolveList(String key, @Nullable List<?> currentValue) {
        if (!isStringList(currentValue)) return currentValue;
        @SuppressWarnings("unchecked")
        List<String> strings = (List<String>) currentValue;
        return shared().resolve(key, ValueType.STRING_LIST, strings, List.class);
    }

    public List<Entry> snapshot(String query) {
        return snapshot(query, null);
    }

    public List<Entry> snapshot(String query, @Nullable Set<String> sessionNewKeys) {
        ensureLoaded();
        ensureSeenLoaded();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Entry> entries = new ArrayList<>();
        observations.forEach((key, observation) -> {
            if (!matchesQuery(key, normalizedQuery)) return;
            OverrideValue override = overrides.get(key);
            boolean overridden = override != null && override.type == observation.type;
            Object effective = overridden ? override.value : observation.value;
            boolean isNew = sessionNewKeys != null ? sessionNewKeys.contains(key) : isKeyNew(key);
            entries.add(new Entry(
                    key,
                    observation.type,
                    observation.value,
                    effective,
                    overridden,
                    isNew
            ));
        });
        overrides.forEach((key, override) -> {
            if (observations.containsKey(key) || !matchesQuery(key, normalizedQuery)) return;
            boolean isNew = sessionNewKeys != null ? sessionNewKeys.contains(key) : isKeyNew(key);
            entries.add(new Entry(key, override.type, null, override.value, true, isNew));
        });
        entries.sort(
                Comparator.comparing(Entry::isOverridden).reversed()
                        .thenComparing(Comparator.comparing(Entry::isNew).reversed())
                        .thenComparing(Entry::getKey)
        );
        return Collections.unmodifiableList(entries);
    }

    public boolean isBaselineEstablished() {
        ensureSeenLoaded();
        return baselineEstablished;
    }

    public void establishBaseline() {
        ensureLoaded();
        ensureSeenLoaded();
        synchronized (seenLock) {
            baselineEstablished = true;
            seenKeys.addAll(observations.keySet());
            seenKeys.addAll(overrides.keySet());
            persistSeen();
        }
    }

    public Set<String> getUnseenKeys() {
        ensureLoaded();
        ensureSeenLoaded();
        if (!baselineEstablished) return Collections.emptySet();
        Set<String> unseen = new HashSet<>();
        observations.keySet().forEach(key -> {
            if (!seenKeys.contains(key)) unseen.add(key);
        });
        overrides.keySet().forEach(key -> {
            if (!seenKeys.contains(key)) unseen.add(key);
        });
        return Collections.unmodifiableSet(unseen);
    }

    public boolean isKeyNew(String key) {
        ensureSeenLoaded();
        return baselineEstablished && !seenKeys.contains(key);
    }

    public void markAllAsSeen() {
        ensureLoaded();
        ensureSeenLoaded();
        if (!baselineEstablished) {
            establishBaseline();
            return;
        }
        synchronized (seenLock) {
            seenKeys.addAll(observations.keySet());
            seenKeys.addAll(overrides.keySet());
            persistSeen();
        }
    }

    public void markKeysAsSeen(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        ensureLoaded();
        ensureSeenLoaded();
        if (!baselineEstablished) {
            establishBaseline();
            return;
        }
        synchronized (seenLock) {
            seenKeys.addAll(keys);
            persistSeen();
        }
    }

    public boolean hasEntry(String key) {
        ensureLoaded();
        return observations.containsKey(key) || overrides.containsKey(key);
    }

    public void setOverride(String key, ValueType type, Object value) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(type);
        ensureLoaded();
        Object normalizedValue = normalizeValue(type, value);
        overrides.put(key, new OverrideValue(type, normalizedValue));
        persist();
    }

    public void removeOverride(String key) {
        ensureLoaded();
        if (overrides.remove(key) == null) return;
        persist();
    }

    public String exportOverrides() {
        ensureLoaded();
        return serialize(overrides);
    }

    public void importOverrides(String serialized) throws JSONException {
        Map<String, OverrideValue> importedOverrides = deserialize(serialized);
        ensureLoaded();
        synchronized (persistenceLock) {
            overrides.clear();
            overrides.putAll(importedOverrides);
            persistence.write(serialize(overrides));
        }
    }

    <T> T resolve(
            String key,
            ValueType type,
            @Nullable T currentValue,
            Class<?> expectedType
    ) {
        Objects.requireNonNull(key);
        ensureLoaded();
        observations.compute(key, (ignored, existing) -> {
            if (existing == null) return new Observation(type, currentValue);
            existing.update(type, currentValue);
            return existing;
        });
        OverrideValue override = overrides.get(key);
        if (override == null || override.type != type) return currentValue;
        if (!expectedType.isInstance(override.value)) return currentValue;
        @SuppressWarnings("unchecked")
        T resolved = (T) copyValue(override.value);
        return resolved;
    }

    private void ensureLoaded() {
        if (loaded) return;
        synchronized (loadLock) {
            if (loaded) return;
            String serialized = persistence.read();
            if (!serialized.isEmpty()) {
                try {
                    overrides.putAll(deserialize(serialized));
                } catch (JSONException | IllegalArgumentException exception) {
                    NewXLogger.printException(
                            () -> "Failed to read NewX feature switch overrides",
                            exception
                    );
                }
            }
            loaded = true;
        }
    }

    private void persist() {
        synchronized (persistenceLock) {
            persistence.write(serialize(overrides));
        }
    }

    static String serialize(Map<String, OverrideValue> values) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", SCHEMA_VERSION);
            JSONArray serializedOverrides = new JSONArray();
            values.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> serializedOverrides.put(serialize(entry.getKey(), entry.getValue())));
            root.put("overrides", serializedOverrides);
            return root.toString();
        } catch (JSONException exception) {
            throw new IllegalStateException("Could not serialize feature switch overrides", exception);
        }
    }

    private static JSONObject serialize(String key, OverrideValue override) {
        try {
            JSONObject serialized = new JSONObject();
            serialized.put("key", key);
            serialized.put("type", override.type.name());
            if (override.type == ValueType.STRING_LIST) {
                serialized.put("value", new JSONArray((List<?>) override.value));
            } else {
                serialized.put("value", override.value);
            }
            return serialized;
        } catch (JSONException exception) {
            throw new IllegalStateException("Could not serialize feature switch: " + key, exception);
        }
    }

    static Map<String, OverrideValue> deserialize(String serialized) throws JSONException {
        JSONObject root = new JSONObject(serialized);
        if (root.getInt("version") != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported feature switch override schema");
        }
        Map<String, OverrideValue> values = new ConcurrentHashMap<>();
        JSONArray serializedOverrides = root.getJSONArray("overrides");
        for (int index = 0; index < serializedOverrides.length(); index++) {
            JSONObject serializedOverride = serializedOverrides.getJSONObject(index);
            String key = serializedOverride.getString("key");
            if (key.isEmpty() || values.containsKey(key)) {
                throw new IllegalArgumentException("Invalid or duplicate feature switch key");
            }
            ValueType type = ValueType.valueOf(serializedOverride.getString("type"));
            Object value = deserializeValue(type, serializedOverride.get("value"));
            values.put(key, new OverrideValue(type, value));
        }
        return values;
    }

    private static boolean matchesQuery(String key, String normalizedQuery) {
        return normalizedQuery.isEmpty()
                || key.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private static Object deserializeValue(ValueType type, Object value) throws JSONException {
        return switch (type) {
            case BOOLEAN -> (Boolean) value;
            case INT -> ((Number) value).intValue();
            case LONG -> ((Number) value).longValue();
            case FLOAT -> ((Number) value).floatValue();
            case DOUBLE -> ((Number) value).doubleValue();
            case STRING -> (String) value;
            case STRING_LIST -> {
                JSONArray array = (JSONArray) value;
                List<String> strings = new ArrayList<>();
                for (int index = 0; index < array.length(); index++) {
                    strings.add(array.getString(index));
                }
                yield Collections.unmodifiableList(strings);
            }
        };
    }

    private static Object normalizeValue(ValueType type, Object value) {
        Objects.requireNonNull(value);
        return switch (type) {
            case BOOLEAN -> requireType(value, Boolean.class);
            case INT -> requireType(value, Integer.class);
            case LONG -> requireType(value, Long.class);
            case FLOAT -> finite((Float) requireType(value, Float.class));
            case DOUBLE -> finite((Double) requireType(value, Double.class));
            case STRING -> requireType(value, String.class);
            case STRING_LIST -> normalizeStringList(value);
        };
    }

    private static Object requireType(Object value, Class<?> expectedType) {
        if (expectedType.isInstance(value)) return value;
        throw new IllegalArgumentException(
                "Expected " + expectedType.getSimpleName() + ", found "
                        + value.getClass().getSimpleName()
        );
    }

    private static Float finite(Float value) {
        if (Float.isFinite(value)) return value;
        throw new IllegalArgumentException("Feature switch float must be finite");
    }

    private static Double finite(Double value) {
        if (Double.isFinite(value)) return value;
        throw new IllegalArgumentException("Feature switch double must be finite");
    }

    private static List<String> normalizeStringList(Object value) {
        if (!(value instanceof List<?> list) || !isStringList(list)) {
            throw new IllegalArgumentException("Expected a list of strings");
        }
        List<String> strings = new ArrayList<>();
        for (Object item : list) strings.add((String) item);
        return Collections.unmodifiableList(strings);
    }

    private static boolean isStringList(@Nullable List<?> list) {
        if (list == null) return true;
        for (Object item : list) {
            if (!(item instanceof String)) return false;
        }
        return true;
    }

    @Nullable
    private static Object copyValue(@Nullable Object value) {
        if (!(value instanceof List<?> list)) return value;
        return Collections.unmodifiableList(new ArrayList<>(list));
    }

    static final class MemoryPersistence implements Persistence {
        private String value = "";

        MemoryPersistence() {
        }

        MemoryPersistence(String initialValue) {
            this.value = initialValue == null ? "" : initialValue;
        }

        @Override
        public String read() {
            return value;
        }

        @Override
        public void write(String updatedValue) {
            this.value = updatedValue == null ? "" : updatedValue;
        }
    }

    private void ensureSeenLoaded() {
        if (seenLoaded) return;
        synchronized (seenLock) {
            if (seenLoaded) return;
            String serialized = seenPersistence.read();
            if (serialized != null && !serialized.isEmpty()) {
                try {
                    JSONObject root = new JSONObject(serialized);
                    baselineEstablished = root.optBoolean("baseline", false);
                    JSONArray array = root.optJSONArray("seen");
                    if (array != null) {
                        for (int i = 0; i < array.length(); i++) {
                            seenKeys.add(array.getString(i));
                        }
                    }
                } catch (JSONException | IllegalArgumentException exception) {
                    NewXLogger.printException(
                            () -> "Failed to read NewX seen feature switches",
                            exception
                    );
                }
            }
            seenLoaded = true;
        }
    }

    private void persistSeen() {
        synchronized (seenLock) {
            try {
                JSONObject root = new JSONObject();
                root.put("version", SCHEMA_VERSION);
                root.put("baseline", baselineEstablished);
                JSONArray array = new JSONArray();
                seenKeys.stream().sorted().forEach(array::put);
                root.put("seen", array);
                seenPersistence.write(root.toString());
            } catch (JSONException exception) {
                NewXLogger.printException(
                        () -> "Failed to persist NewX seen feature switches",
                        exception
                );
            }
        }
    }

    private static final class Holder {
        private static final FeatureSwitchStore INSTANCE =
                new FeatureSwitchStore(
                        new SettingPersistence(PERSISTENCE_KEY),
                        new SettingPersistence(SEEN_PERSISTENCE_KEY)
                );
    }

    private static final class SettingPersistence implements Persistence {
        private final StringSetting setting;

        SettingPersistence(String key) {
            this.setting = new StringSetting(key, "");
        }

        @Override
        public String read() {
            return setting.get();
        }

        @Override
        public void write(String value) {
            setting.save(value);
        }
    }
}
