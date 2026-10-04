package app.morphe.extension.shared.settings;

/** Test-only stand-in for the compile-only settings library. */
public final class StringSetting extends Setting<String> {
    public StringSetting(String key, String defaultValue, boolean rebootApp) {
        super(key, defaultValue, rebootApp);
    }

    public StringSetting(String key, String defaultValue) {
        super(key, defaultValue, false);
    }

    public StringSetting(String key, String defaultValue, String currentValue) {
        super(key, defaultValue, false);
        save(currentValue);
    }

    @Override
    public String get() {
        return super.get();
    }
}
