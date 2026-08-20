package app.morphe.extension.newx.settings;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.settings.Setting;

public final class StringSetSetting extends Setting<Set<String>> {
    public StringSetSetting(String key, Set<String> defaultValue, boolean rebootApp) {
        super(key, immutableCopy(defaultValue), rebootApp);
    }

    @Override
    protected void load() {
        try {
            Set<String> stored = preferences.preferences.getStringSet(key, defaultValue);
            value = immutableCopy(stored == null ? defaultValue : stored);
        } catch (ClassCastException exception) {
            Logger.printInfo(() -> "Removing conflicting string-set preference: " + key, exception);
            preferences.removeKey(key);
            value = defaultValue;
        }
    }

    @Override
    protected Set<String> readFromJSON(JSONObject json, String importExportKey) throws JSONException {
        return parse(json.getString(importExportKey));
    }

    @Override
    protected void writeToJSON(JSONObject json, String importExportKey) throws JSONException {
        json.put(importExportKey, String.join(",", value));
    }

    @Override
    protected void setValueFromString(@NonNull String newValue) {
        value = parse(newValue);
    }

    @Override
    protected void saveToPreferences() {
        preferences.preferences
                .edit()
                .putStringSet(key, new LinkedHashSet<>(value))
                .commit();
    }

    @NonNull
    @Override
    public Set<String> get() {
        return value;
    }

    public static Set<String> immutableCopy(Set<String> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    private static Set<String> parse(String value) {
        if (value.isBlank()) return Collections.emptySet();

        return immutableCopy(
                Arrays.stream(value.split(","))
                        .filter(item -> !item.isBlank())
                        .collect(Collectors.toCollection(LinkedHashSet::new))
        );
    }
}
