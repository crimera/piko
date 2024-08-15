package app.revanced.integrations.shared.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

@SuppressWarnings("unused")
public class FloatSetting extends Setting<Float> {

    public FloatSetting(String key, Float defaultValue) {
        super(key, defaultValue);
    }
    public FloatSetting(String key, Float defaultValue, boolean rebootApp) {
        super(key, defaultValue, rebootApp);
    }
    public FloatSetting(String key, Float defaultValue, boolean rebootApp, boolean includeWithImportExport) {
        super(key, defaultValue, rebootApp, includeWithImportExport);
    }
    public FloatSetting(String key, Float defaultValue, String userDialogMessage) {
        super(key, defaultValue, userDialogMessage);
    }
    public FloatSetting(String key, Float defaultValue, Availability availability) {
        super(key, defaultValue, availability);
    }
    public FloatSetting(String key, Float defaultValue, boolean rebootApp, String userDialogMessage) {
        super(key, defaultValue, rebootApp, userDialogMessage);
    }
    public FloatSetting(String key, Float defaultValue, boolean rebootApp, Availability availability) {
        super(key, defaultValue, rebootApp, availability);
    }
    public FloatSetting(String key, Float defaultValue, boolean rebootApp, String userDialogMessage, Availability availability) {
        super(key, defaultValue, rebootApp, userDialogMessage, availability);
    }
    public FloatSetting(@NonNull String key, @NonNull Float defaultValue, boolean rebootApp, boolean includeWithImportExport, @Nullable String userDialogMessage, @Nullable Availability availability) {
        super(key, defaultValue, rebootApp, includeWithImportExport, userDialogMessage, availability);
    }

    @Override
    protected void load() {
        value = preferences.getFloatString(key, defaultValue);
    }

    @Override
    protected Float readFromJSON(JSONObject json, String importExportKey) throws JSONException {
        return (float) json.getDouble(importExportKey);
    }

    @Override
    protected void setValueFromString(@NonNull String newValue) {
        value = Float.valueOf(Objects.requireNonNull(newValue));
    }

    @Override
    public void save(@NonNull Float newValue) {
        // Must set before saving to preferences (otherwise importing fails to update UI correctly).
        value = Objects.requireNonNull(newValue);
        preferences.saveFloatString(key, newValue);
    }

    @NonNull
    @Override
    public Float get() {
        return value;
    }
}
