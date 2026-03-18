package app.morphe.extension.shared.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

@SuppressWarnings("unused")
public class IntegerSetting extends Setting<Integer> {

    public IntegerSetting(String key, Integer defaultValue) {
        super(key, defaultValue);
    }
    public IntegerSetting(String key, Integer defaultValue, boolean rebootApp) {
        super(key, defaultValue, rebootApp);
    }
    public IntegerSetting(String key, Integer defaultValue, boolean rebootApp, boolean includeWithImportExport) {
        super(key, defaultValue, rebootApp, includeWithImportExport);
    }
    public IntegerSetting(String key, Integer defaultValue, String userDialogMessage) {
        super(key, defaultValue, userDialogMessage);
    }
    public IntegerSetting(String key, Integer defaultValue, Availability availability) {
        super(key, defaultValue, availability);
    }
    public IntegerSetting(String key, Integer defaultValue, boolean rebootApp, String userDialogMessage) {
        super(key, defaultValue, rebootApp, userDialogMessage);
    }
    public IntegerSetting(String key, Integer defaultValue, boolean rebootApp, Availability availability) {
        super(key, defaultValue, rebootApp, availability);
    }
    public IntegerSetting(String key, Integer defaultValue, boolean rebootApp, String userDialogMessage, Availability availability) {
        super(key, defaultValue, rebootApp, userDialogMessage, availability);
    }
    public IntegerSetting(@NonNull String key, @NonNull Integer defaultValue, boolean rebootApp, boolean includeWithImportExport, @Nullable String userDialogMessage, @Nullable Availability availability) {
        super(key, defaultValue, rebootApp, includeWithImportExport, userDialogMessage, availability);
    }

    @Override
    protected void load() {
        value = preferences.getIntegerString(key, defaultValue);
    }

    @Override
    protected Integer readFromJSON(JSONObject json, String importExportKey) throws JSONException {
        return json.getInt(importExportKey);
    }

    @Override
    protected void setValueFromString(@NonNull String newValue) {
        value = Integer.valueOf(Objects.requireNonNull(newValue));
    }

    @Override
    public void saveToPreferences() {
        preferences.saveIntegerString(key, value);
    }

    @NonNull
    @Override
    public Integer get() {
        return value;
    }
}
