package app.morphe.extension.shared.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

@SuppressWarnings("unused")
public class StringSetting extends Setting<String> {

    public StringSetting(String key, String defaultValue) {
        super(key, defaultValue);
    }
    public StringSetting(String key, String defaultValue, boolean rebootApp) {
        super(key, defaultValue, rebootApp);
    }
    public StringSetting(String key, String defaultValue, boolean rebootApp, boolean includeWithImportExport) {
        super(key, defaultValue, rebootApp, includeWithImportExport);
    }
    public StringSetting(String key, String defaultValue, String userDialogMessage) {
        super(key, defaultValue, userDialogMessage);
    }
    public StringSetting(String key, String defaultValue, Availability availability) {
        super(key, defaultValue, availability);
    }
    public StringSetting(String key, String defaultValue, boolean rebootApp, String userDialogMessage) {
        super(key, defaultValue, rebootApp, userDialogMessage);
    }
    public StringSetting(String key, String defaultValue, boolean rebootApp, Availability availability) {
        super(key, defaultValue, rebootApp, availability);
    }
    public StringSetting(String key, String defaultValue, boolean rebootApp, String userDialogMessage, Availability availability) {
        super(key, defaultValue, rebootApp, userDialogMessage, availability);
    }
    public StringSetting(@NonNull String key, @NonNull String defaultValue, boolean rebootApp, boolean includeWithImportExport, @Nullable String userDialogMessage, @Nullable Availability availability) {
        super(key, defaultValue, rebootApp, includeWithImportExport, userDialogMessage, availability);
    }

    @Override
    protected void load() {
        value = preferences.getString(key, defaultValue);
    }

    @Override
    protected String readFromJSON(JSONObject json, String importExportKey) throws JSONException {
        return json.getString(importExportKey);
    }

    @Override
    protected void setValueFromString(@NonNull String newValue) {
        value = Objects.requireNonNull(newValue);
    }

    @Override
    public void saveToPreferences() {
        preferences.saveString(key, value);
    }

    @NonNull
    @Override
    public String get() {
        return value;
    }
}
