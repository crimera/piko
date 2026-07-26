package app.morphe.extension.xlite.api;

import java.util.Objects;

/** A typed, stable setting identifier. */
public final class SettingKey<T> {
    private final String id;

    public SettingKey(String id) {
        this.id = Objects.requireNonNull(id);
    }

    public String getId() {
        return id;
    }
}
