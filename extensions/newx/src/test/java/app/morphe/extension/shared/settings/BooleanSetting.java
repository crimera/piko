package app.morphe.extension.shared.settings;

/** Test-only stand-in for the compile-only settings library. */
public final class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String key, Boolean defaultValue, boolean rebootApp) {
        super(key, defaultValue, rebootApp);
    }

    @Override
    public Boolean get() {
        return super.get();
    }
}
