package app.morphe.extension.shared.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

@SuppressWarnings("unused")
public class LongSetting extends Setting<Long> {

    public LongSetting(String key, Long defaultValue) {
        super(key, defaultValue);
    }
    public LongSetting(String key, Long defaultValue, boolean rebootApp) {
        super(key, defaultValue, rebootApp);
    }
    public LongSetting(String key, Long defaultValue, boolean rebootApp, boolean includeWithImportExport) {
        super(key, defaultValue, rebootApp, includeWithImportExport);
    }
    public LongSetting(String key, Long defaultValue, String userDialogMessage) {
        super(key, defaultValue, userDialogMessage);
    }
    public LongSetting(String key, Long defaultValue, Availability availability) {
        super(key, defaultValue, availability);
    }
    public LongSetting(String key, Long defaultValue, boolean rebootApp, String userDialogMessage) {
        super(key, defaultValue, rebootApp, userDialogMessage);
    }
    public LongSetting(String key, Long defaultValue, boolean rebootApp, Availability availability) {
        super(key, defaultValue, rebootApp, availability);
    }
    public LongSetting(String key, Long defaultValue, boolean rebootApp, String userDialogMessage, Availability availability) {
        super(key, defaultValue, rebootApp, userDialogMessage, availability);
    }
    public LongSetting(@NonNull String key, @NonNull Long defaultValue, boolean rebootApp, boolean includeWithImportExport, @Nullable String userDialogMessage, @Nullable Availability availability) {
        super(key, defaultValue, rebootApp, includeWithImportExport, userDialogMessage, availability);
    }

    @Override
    protected void load() {
        value = preferences.getLongString(key, defaultValue);
    }

    @Override
    protected Long readFromJSON(JSONObject json, String importExportKey) throws JSONException {
        return json.getLong(importExportKey);
    }

    @Override
    protected void setValueFromString(@NonNull String newValue) {
        value = Long.valueOf(Objects.requireNonNull(newValue));
    }

    @Override
    public void saveToPreferences() {
        preferences.saveLongString(key, value);
    }

    @NonNull
    @Override
    public Long get() {
        return value;
    }
}
