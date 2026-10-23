package app.morphe.extension.shared.settings;

import java.util.Objects;

/** Test-only stand-in for the compile-only settings library. */
public abstract class Setting<T> {
    public final String key;
    public final T defaultValue;
    private T value;

    protected Setting(String key, T defaultValue, boolean rebootApp) {
        this.key = Objects.requireNonNull(key);
        this.defaultValue = Objects.requireNonNull(defaultValue);
        this.value = defaultValue;
    }

    public T get() {
        return value;
    }

    public void save(T value) {
        this.value = Objects.requireNonNull(value);
    }

    public boolean isAvailable() {
        return true;
    }
}
