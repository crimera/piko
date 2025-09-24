package app.revanced.extension.shared.settings;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.StringRef;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.settings.preference.SharedPrefCategory;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

import static app.revanced.extension.shared.StringRef.str;

public abstract class Setting<T> {

    /**
     * Indicates if a {@link Setting} is available to edit and use.
     * Typically this is dependent upon other BooleanSetting(s) set to 'true',
     * but this can be used to call into extension code and check other conditions.
     */
    public interface Availability {
        boolean isAvailable();
    }

    /**
     * Availability based on a single parent setting being enabled.
     */
    public static Availability parent(BooleanSetting parent) {
        return parent::get;
    }

    /**
     * Availability based on all parents being enabled.
     */
    public static Availability parentsAll(BooleanSetting... parents) {
        return () -> {
            for (BooleanSetting parent : parents) {
                if (!parent.get()) return false;
            }
            return true;
        };
    }

    /**
     * Availability based on any parent being enabled.
     */
    public static Availability parentsAny(BooleanSetting... parents) {
        return () -> {
            for (BooleanSetting parent : parents) {
                if (parent.get()) return true;
            }
            return false;
        };
    }

    /**
     * Callback for importing/exporting settings.
     */
    public interface ImportExportCallback {
        /**
         * Called after all settings have been imported.
         */
        void settingsImported(@Nullable Context context);

        /**
         * Called after all settings have been exported.
         */
        void settingsExported(@Nullable Context context);
    }

    private static final List<ImportExportCallback> importExportCallbacks = new ArrayList<>();

    /**
     * Adds a callback for {@link #importFromJSON(Context, String)} and {@link #exportToJson(Context)}.
     */
    public static void addImportExportCallback(ImportExportCallback callback) {
        importExportCallbacks.add(Objects.requireNonNull(callback));
    }

    /**
     * All settings that were instantiated.
     * When a new setting is created, it is automatically added to this list.
     */
    private static final List<Setting<?>> SETTINGS = new ArrayList<>();

    /**
     * Map of setting path to setting object.
     */
    private static final Map<String, Setting<?>> PATH_TO_SETTINGS = new HashMap<>();

    /**
     * Preference all instances are saved to.
     */
    public static final SharedPrefCategory preferences = new SharedPrefCategory("revanced_prefs");

    @Nullable
    public static Setting<?> getSettingFromPath(String str) {
        return PATH_TO_SETTINGS.get(str);
    }

    /**
     * @return All settings that have been created.
     */
    public static List<Setting<?>> allLoadedSettings() {
        return Collections.unmodifiableList(SETTINGS);
    }

    /**
     * @return All settings that have been created, sorted by keys.
     */
    private static List<Setting<?>> allLoadedSettingsSorted() {
        Collections.sort(SETTINGS, (Setting<?> o1, Setting<?> o2) -> o1.key.compareTo(o2.key));
        return allLoadedSettings();
    }

    /**
     * The key used to store the value in the shared preferences.
     */
    public final String key;

    /**
     * The default value of the setting.
     */
    public final T defaultValue;

    /**
     * If the app should be rebooted, if this setting is changed
     */
    public final boolean rebootApp;

    /**
     * If this setting should be included when importing/exporting settings.
     */
    public final boolean includeWithImportExport;

    /**
     * If this setting is available to edit and use.
     * Not to be confused with it's status returned from {@link #get()}.
     */
    @Nullable
    private final Availability availability;

    /**
     * Confirmation message to display, if the user tries to change the setting from the default value.
     */
    @Nullable
    public final StringRef userDialogMessage;

    // Must be volatile, as some settings are read/write from different threads.
    // Of note, the object value is persistently stored using SharedPreferences (which is thread safe).
    /**
     * The value of the setting.
     */
    protected volatile T value;

    public Setting(String key, T defaultValue) {
        this(key, defaultValue, false, true, null, null);
    }
    public Setting(String key, T defaultValue, boolean rebootApp) {
        this(key, defaultValue, rebootApp, true, null, null);
    }
    public Setting(String key, T defaultValue, boolean rebootApp, boolean includeWithImportExport) {
        this(key, defaultValue, rebootApp, includeWithImportExport, null, null);
    }
    public Setting(String key, T defaultValue, String userDialogMessage) {
        this(key, defaultValue, false, true, userDialogMessage, null);
    }
    public Setting(String key, T defaultValue, Availability availability) {
        this(key, defaultValue, false, true, null, availability);
    }
    public Setting(String key, T defaultValue, boolean rebootApp, String userDialogMessage) {
        this(key, defaultValue, rebootApp, true, userDialogMessage, null);
    }
    public Setting(String key, T defaultValue, boolean rebootApp, Availability availability) {
        this(key, defaultValue, rebootApp, true, null, availability);
    }
    public Setting(String key, T defaultValue, boolean rebootApp, String userDialogMessage, Availability availability) {
        this(key, defaultValue, rebootApp, true, userDialogMessage, availability);
    }

    /**
     * A setting backed by a shared preference.
     *
     * @param key                     The key used to store the value in the shared preferences.
     * @param defaultValue            The default value of the setting.
     * @param rebootApp               If the app should be rebooted, if this setting is changed.
     * @param includeWithImportExport If this setting should be shown in the import/export dialog.
     * @param userDialogMessage       Confirmation message to display, if the user tries to change the setting from the default value.
     * @param availability            Condition that must be true, for this setting to be available to configure.
     */
    public Setting(String key,
                   T defaultValue,
                   boolean rebootApp,
                   boolean includeWithImportExport,
                   @Nullable String userDialogMessage,
                   @Nullable Availability availability
    ) {
        this.key = Objects.requireNonNull(key);
        this.value = this.defaultValue = Objects.requireNonNull(defaultValue);
        this.rebootApp = rebootApp;
        this.includeWithImportExport = includeWithImportExport;
        this.userDialogMessage = (userDialogMessage == null) ? null : new StringRef(userDialogMessage);
        this.availability = availability;

        SETTINGS.add(this);
        if (PATH_TO_SETTINGS.put(key, this) != null) {
            // Debug setting may not be created yet so using Logger may cause an initialization crash.
            // Show a toast instead.
            Utils.showToastLong(this.getClass().getSimpleName()
                    + " error: Duplicate Setting key found: " + key);
        }

        load();
    }

    /**
     * Migrate a setting value if the path is renamed but otherwise the old and new settings are identical.
     */
    public static <T> void migrateOldSettingToNew(Setting<T> oldSetting, Setting<T> newSetting) {
        if (oldSetting == newSetting) throw new IllegalArgumentException();

        if (!oldSetting.isSetToDefault()) {
            Logger.printInfo(() -> "Migrating old setting value: " + oldSetting + " into replacement setting: " + newSetting);
            newSetting.save(oldSetting.value);
            oldSetting.resetToDefault();
        }
    }

    /**
     * Migrate an old Setting value previously stored in a different SharedPreference.
     *
     * This method will be deleted in the future.
     */
    @SuppressWarnings("rawtypes")
    public static void migrateFromOldPreferences(SharedPrefCategory oldPrefs, Setting setting, String settingKey) {
        if (!oldPrefs.preferences.contains(settingKey)) {
            return; // Nothing to do.
        }

        Object newValue = setting.get();
        final Object migratedValue;
        if (setting instanceof BooleanSetting) {
            migratedValue = oldPrefs.getBoolean(settingKey, (Boolean) newValue);
        } else if (setting instanceof IntegerSetting) {
            migratedValue = oldPrefs.getIntegerString(settingKey, (Integer) newValue);
        } else if (setting instanceof LongSetting) {
            migratedValue = oldPrefs.getLongString(settingKey, (Long) newValue);
        } else if (setting instanceof FloatSetting) {
            migratedValue = oldPrefs.getFloatString(settingKey, (Float) newValue);
        } else if (setting instanceof StringSetting) {
            migratedValue = oldPrefs.getString(settingKey, (String) newValue);
        } else {
            Logger.printException(() -> "Unknown setting: " + setting);
            // Remove otherwise it'll show a toast on every launch
            oldPrefs.preferences.edit().remove(settingKey).apply();
            return;
        }

        oldPrefs.preferences.edit().remove(settingKey).apply(); // Remove the old setting.
        if (migratedValue.equals(newValue)) {
            Logger.printDebug(() -> "Value does not need migrating: " + settingKey);
            return; // Old value is already equal to the new setting value.
        }

        Logger.printDebug(() -> "Migrating old preference value into current preference: " + settingKey);
        //noinspection unchecked
        setting.save(migratedValue);
    }

    /**
     * Sets, but does _not_ persistently save the value.
     * This method is only to be used by the Settings preference code.
     *
     * This intentionally is a static method to deter
     * accidental usage when {@link #save(Object)} was intended.
     */
    public static void privateSetValueFromString(Setting<?> setting, String newValue) {
        setting.setValueFromString(newValue);

        // Clear the preference value since default is used, to allow changing
        // the changing the default for a future release.  Without this after upgrading
        // the saved value will be whatever was the default when the app was first installed.
        if (setting.isSetToDefault()) {
            setting.removeFromPreferences();
        }
    }

    /**
     * Sets the value of {@link #value}, but do not save to {@link #preferences}.
     */
    protected abstract void setValueFromString(String newValue);

    /**
     * Load and set the value of {@link #value}.
     */
    protected abstract void load();

    /**
     * Persistently saves the value.
     */
    public final void save(T newValue) {
        if (value.equals(newValue)) {
            return;
        }

        // Must set before saving to preferences (otherwise importing fails to update UI correctly).
        value = Objects.requireNonNull(newValue);

        if (defaultValue.equals(newValue)) {
            removeFromPreferences();
        } else {
            saveToPreferences();
        }
    }

    /**
     * Save {@link #value} to {@link #preferences}.
     */
    protected abstract void saveToPreferences();

    /**
     * Remove {@link #value} from {@link #preferences}.
     */
    protected final void removeFromPreferences() {
        Logger.printDebug(() -> "Clearing stored preference value (reset to default): " + key);
        preferences.removeKey(key);
    }

    @NonNull
    public abstract T get();

    /**
     * Identical to calling {@link #save(Object)} using {@link #defaultValue}.
     *
     * @return The newly saved default value.
     */
    public T resetToDefault() {
        save(defaultValue);
        return defaultValue;
    }

    /**
     * @return if this setting can be configured and used.
     */
    public boolean isAvailable() {
        return availability == null || availability.isAvailable();
    }

    /**
     * @return if the currently set value is the same as {@link #defaultValue}
     */
    public boolean isSetToDefault() {
        return value.equals(defaultValue);
    }

    @NonNull
    @Override
    public String toString() {
        return key + "=" + get();
    }

    // region Import / export

    /**
     * If a setting path has this prefix, then remove it before importing/exporting.
     */
    private static final String OPTIONAL_REVANCED_SETTINGS_PREFIX = "revanced_";

    /**
     * The path, minus any 'revanced' prefix to keep json concise.
     */
    private String getImportExportKey() {
        if (key.startsWith(OPTIONAL_REVANCED_SETTINGS_PREFIX)) {
            return key.substring(OPTIONAL_REVANCED_SETTINGS_PREFIX.length());
        }
        return key;
    }

    /**
     * @param importExportKey The JSON key. The JSONObject parameter will contain data for this key.
     * @return the value stored using the import/export key.  Do not set any values in this method.
     */
    protected abstract T readFromJSON(JSONObject json, String importExportKey) throws JSONException;

    /**
     * Saves this instance to JSON.
     * <p>
     * To keep the JSON simple and readable,
     * subclasses should not write out any embedded types (such as JSON Array or Dictionaries).
     * <p>
     * If this instance is not a type supported natively by JSON (ie: it's not a String/Integer/Float/Long),
     * then subclasses can override this method and write out a String value representing the value.
     */
    protected void writeToJSON(JSONObject json, String importExportKey) throws JSONException {
        json.put(importExportKey, value);
    }

    public static String exportToJson(@Nullable Context alertDialogContext) {
        try {
            JSONObject json = new JSONObject();
            for (Setting<?> setting : allLoadedSettingsSorted()) {
                String importExportKey = setting.getImportExportKey();
                if (json.has(importExportKey)) {
                    throw new IllegalArgumentException("duplicate key found: " + importExportKey);
                }

                final boolean exportDefaultValues = false; // Enable to see what all settings looks like in the UI.
                //noinspection ConstantValue
                if (setting.includeWithImportExport && (!setting.isSetToDefault() || exportDefaultValues)) {
                    setting.writeToJSON(json, importExportKey);
                }
            }

            for (ImportExportCallback callback : importExportCallbacks) {
                callback.settingsExported(alertDialogContext);
            }

            if (json.length() == 0) {
                return "";
            }

            String export = json.toString(0);

            // Remove the outer JSON braces to make the output more compact,
            // and leave less chance of the user forgetting to copy it
            return export.substring(2, export.length() - 2);
        } catch (JSONException e) {
            Logger.printException(() -> "Export failure", e); // should never happen
            return "";
        }
    }

    /**
     * @return if any settings that require a reboot were changed.
     */
    public static boolean importFromJSON(Context alertDialogContext, String settingsJsonString) {
        try {
            if (!settingsJsonString.matches("[\\s\\S]*\\{")) {
                settingsJsonString = '{' + settingsJsonString + '}'; // Restore outer JSON braces
            }
            JSONObject json = new JSONObject(settingsJsonString);

            boolean rebootSettingChanged = false;
            int numberOfSettingsImported = 0;
            //noinspection rawtypes
            for (Setting setting : SETTINGS) {
                String key = setting.getImportExportKey();
                if (json.has(key)) {
                    Object value = setting.readFromJSON(json, key);
                    if (!setting.get().equals(value)) {
                        rebootSettingChanged |= setting.rebootApp;
                        //noinspection unchecked
                        setting.save(value);
                    }
                    numberOfSettingsImported++;
                } else if (setting.includeWithImportExport && !setting.isSetToDefault()) {
                    Logger.printDebug(() -> "Resetting to default: " + setting);
                    rebootSettingChanged |= setting.rebootApp;
                    setting.resetToDefault();
                }
            }

            for (ImportExportCallback callback : importExportCallbacks) {
                callback.settingsImported(alertDialogContext);
            }

            Utils.showToastLong(numberOfSettingsImported == 0
                    ? str("revanced_settings_import_reset")
                    : str("revanced_settings_import_success", numberOfSettingsImported));

            return rebootSettingChanged;
        } catch (JSONException | IllegalArgumentException ex) {
            Utils.showToastLong(str("revanced_settings_import_failure_parse", ex.getMessage()));
            Logger.printInfo(() -> "", ex);
        } catch (Exception ex) {
            Logger.printException(() -> "Import failure: " + ex.getMessage(), ex); // should never happen
        }
        return false;
    }

    // End import / export

}
