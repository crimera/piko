package app.morphe.extension.shared.settings;

/** Test-only stand-in for the compile-only settings library. */
public final class StringSetting extends Setting<String> {
    public StringSetting(String key, String defaultValue) {
        super(key, defaultValue, false);
    }

    @Override
    public String get() {
        return super.get();
    }
}
