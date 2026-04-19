package app.morphe.extension.crimera.settings;

import java.util.Objects;

public abstract class Setting<T> {

    /**
     * The key used to store the value in the shared preferences.
     */
    public final String key;

    /**
     * The default value of the setting.
     */
    public final T defaultValue;


    // Must be volatile, as some settings are read/write from different threads.
    // Of note, the object value is persistently stored using SharedPreferences (which is thread safe).
    /**
     * The value of the setting.
     */
    protected volatile T value;

    /**
     * A setting backed by a shared preference.
     *
     * @param key          The key used to store the value in the shared preferences.
     * @param defaultValue The default value of the setting.
     */
    public Setting(String key, T defaultValue) {
        this.key = Objects.requireNonNull(key);
        this.value = this.defaultValue = Objects.requireNonNull(defaultValue);
    }

}
