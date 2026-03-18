package app.morphe.extension.shared.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Objects;

import app.morphe.extension.shared.Logger;

/**
 * If an Enum value is removed or changed, any saved or imported data using the
 * non-existent value will be reverted to the default value
 * (the event is logged, but no user error is displayed).
 *
 * All saved JSON text is converted to lowercase to keep the output less obnoxious.
 */
@SuppressWarnings("unused")
public class EnumSetting<T extends Enum<?>> extends Setting<T> {
    public EnumSetting(String key, T defaultValue) {
        super(key, defaultValue);
    }
    public EnumSetting(String key, T defaultValue, boolean rebootApp) {
        super(key, defaultValue, rebootApp);
    }
    public EnumSetting(String key, T defaultValue, boolean rebootApp, boolean includeWithImportExport) {
        super(key, defaultValue, rebootApp, includeWithImportExport);
    }
    public EnumSetting(String key, T defaultValue, String userDialogMessage) {
        super(key, defaultValue, userDialogMessage);
    }
    public EnumSetting(String key, T defaultValue, Availability availability) {
        super(key, defaultValue, availability);
    }
    public EnumSetting(String key, T defaultValue, boolean rebootApp, String userDialogMessage) {
        super(key, defaultValue, rebootApp, userDialogMessage);
    }
    public EnumSetting(String key, T defaultValue, boolean rebootApp, Availability availability) {
        super(key, defaultValue, rebootApp, availability);
    }
    public EnumSetting(String key, T defaultValue, boolean rebootApp, String userDialogMessage, Availability availability) {
        super(key, defaultValue, rebootApp, userDialogMessage, availability);
    }
    public EnumSetting(@NonNull String key, @NonNull T defaultValue, boolean rebootApp, boolean includeWithImportExport, @Nullable String userDialogMessage, @Nullable Availability availability) {
        super(key, defaultValue, rebootApp, includeWithImportExport, userDialogMessage, availability);
    }

    @Override
    protected void load() {
        value = preferences.getEnum(key, defaultValue);
    }

    @Override
    protected T readFromJSON(JSONObject json, String importExportKey) throws JSONException {
        String enumName = json.getString(importExportKey);
        try {
            return getEnumFromString(enumName);
        } catch (IllegalArgumentException ex) {
            // Info level to allow removing enum values in the future without showing any user errors.
            Logger.printInfo(() -> "Using default, and ignoring unknown enum value: "  + enumName, ex);
            return defaultValue;
        }
    }

    @Override
    protected void writeToJSON(JSONObject json, String importExportKey) throws JSONException {
        // Use lowercase to keep the output less ugly.
        json.put(importExportKey, value.name().toLowerCase(Locale.ENGLISH));
    }

    /**
     * @param enumName Enum name.  Casing does not matter.
     * @return Enum of this type with the same declared name.
     * @throws IllegalArgumentException if the name is not a valid enum of this type.
     */
    protected T getEnumFromString(String enumName) {
        //noinspection ConstantConditions
        for (Enum<?> value : defaultValue.getClass().getEnumConstants()) {
            if (value.name().equalsIgnoreCase(enumName)) {
                //noinspection unchecked
                return (T) value;
            }
        }

        throw new IllegalArgumentException("Unknown enum value: " + enumName);
    }

    @Override
    protected void setValueFromString(@NonNull String newValue) {
        value = getEnumFromString(Objects.requireNonNull(newValue));
    }

    @Override
    public void saveToPreferences() {
        preferences.saveEnumAsString(key, value);
    }

    @NonNull
    @Override
    public T get() {
        return value;
    }

    /**
     * Availability based on if this setting is currently set to any of the provided types.
     */
    @SafeVarargs
    public final Setting.Availability availability(T... types) {
        Objects.requireNonNull(types);

        return () -> {
            T currentEnumType = get();
            for (T enumType : types) {
                if (currentEnumType == enumType) return true;
            }
            return false;
        };
    }
}
