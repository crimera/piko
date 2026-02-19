package app.morphe.extension.shared.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

@SuppressWarnings("unused")
public class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String key, Boolean defaultValue) {
        super(key, defaultValue);
    }
    public BooleanSetting(String key, Boolean defaultValue, boolean rebootApp) {
        super(key, defaultValue, rebootApp);
    }
    public BooleanSetting(String key, Boolean defaultValue, boolean rebootApp, boolean includeWithImportExport) {
        super(key, defaultValue, rebootApp, includeWithImportExport);
    }
    public BooleanSetting(String key, Boolean defaultValue, String userDialogMessage) {
        super(key, defaultValue, userDialogMessage);
    }
    public BooleanSetting(String key, Boolean defaultValue, Availability availability) {
        super(key, defaultValue, availability);
    }
    public BooleanSetting(String key, Boolean defaultValue, boolean rebootApp, String userDialogMessage) {
        super(key, defaultValue, rebootApp, userDialogMessage);
    }
    public BooleanSetting(String key, Boolean defaultValue, boolean rebootApp, Availability availability) {
        super(key, defaultValue, rebootApp, availability);
    }
    public BooleanSetting(String key, Boolean defaultValue, boolean rebootApp, String userDialogMessage, Availability availability) {
        super(key, defaultValue, rebootApp, userDialogMessage, availability);
    }
    public BooleanSetting(@NonNull String key, @NonNull Boolean defaultValue, boolean rebootApp, boolean includeWithImportExport, @Nullable String userDialogMessage, @Nullable Availability availability) {
        super(key, defaultValue, rebootApp, includeWithImportExport, userDialogMessage, availability);
    }

    /**
     * Sets, but does _not_ persistently save the value.
     * This method is only to be used by the Settings preference code.
     *
     * This intentionally is a static method to deter
     * accidental usage when {@link #save(Boolean)} was intnded.
     */
    public static void privateSetValue(@NonNull BooleanSetting setting, @NonNull Boolean newValue) {
        setting.value = Objects.requireNonNull(newValue);

        if (setting.isSetToDefault()) {
            setting.removeFromPreferences();
        }
    }

    @Override
    protected void load() {
        value = preferences.getBoolean(key, defaultValue);
    }

    @Override
    protected Boolean readFromJSON(JSONObject json, String importExportKey) throws JSONException {
        return json.getBoolean(importExportKey);
    }

    @Override
    protected void setValueFromString(@NonNull String newValue) {
        value = Boolean.valueOf(Objects.requireNonNull(newValue));
    }

    @Override
    public void saveToPreferences() {
        preferences.saveBoolean(key, value);
    }

    @NonNull
    @Override
    public Boolean get() {
        return value;
    }
}
