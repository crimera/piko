package app.revanced.extension.shared.settings.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceFragment;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import org.json.JSONObject;
import java.util.*;

/**
 * Shared categories, and helper methods.
 *
 * The various save methods store numbers as Strings,
 * which is required if using {@link PreferenceFragment}.
 *
 * If saved numbers will not be used with a preference fragment,
 * then store the primitive numbers using the {@link #preferences} itself.
 */
public class SharedPrefCategory {
    @NonNull
    public final String name;
    @NonNull
    public final SharedPreferences preferences;

    public SharedPrefCategory(@NonNull String name) {
        this.name = Objects.requireNonNull(name);
        preferences = Objects.requireNonNull(Utils.getContext()).getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    private void removeConflictingPreferenceKeyValue(@NonNull String key) {
        Logger.printException(() -> "Found conflicting preference: " + key);
        removeKey(key);
    }

    private void saveObjectAsString(@NonNull String key, @Nullable Object value) {
        preferences.edit().putString(key, (value == null ? null : value.toString())).apply();
    }


    public Set<String> getSet(@NonNull String key, Set<String> _default) {
        try {
            return preferences.getStringSet(key, _default);
        } catch (ClassCastException ex) {
            // Value stored is a completely different type (should never happen).
            removeConflictingPreferenceKeyValue(key);
            return _default;
        }
    }

    public JSONObject getAll(){
        try{
            Map<String, ?> allEntries = preferences.getAll();
            return new JSONObject(allEntries);
        }catch (Exception ex){
            return new JSONObject();
        }
    }

    /**
     * Removes any preference data type that has the specified key.
     */
    public void removeKey(@NonNull String key) {
        preferences.edit().remove(Objects.requireNonNull(key)).apply();
    }

    public boolean clearAll(){
        try{
            preferences.edit().clear().commit();
            return true;
        }catch(Exception e){
            Utils.showToastShort(e.toString());
            return false;
        }
    }

    public void saveBoolean(@NonNull String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    public void saveSet(@NonNull String key, Set<String> value) {
        preferences.edit().putStringSet(key, value).apply();
    }
    /**
     * @param value a NULL parameter removes the value from the preferences
     */
    public void saveEnumAsString(@NonNull String key, @Nullable Enum<?> value) {
        saveObjectAsString(key, value);
    }

    /**
     * @param value a NULL parameter removes the value from the preferences
     */
    public void saveIntegerString(@NonNull String key, @Nullable Integer value) {
        saveObjectAsString(key, value);
    }

    /**
     * @param value a NULL parameter removes the value from the preferences
     */
    public void saveLongString(@NonNull String key, @Nullable Long value) {
        saveObjectAsString(key, value);
    }

    /**
     * @param value a NULL parameter removes the value from the preferences
     */
    public void saveFloatString(@NonNull String key, @Nullable Float value) {
        saveObjectAsString(key, value);
    }

    /**
     * @param value a NULL parameter removes the value from the preferences
     */
    public void saveString(@NonNull String key, @Nullable String value) {
        saveObjectAsString(key, value);
    }

    @NonNull
    public String getString(@NonNull String key, @NonNull String _default) {
        Objects.requireNonNull(_default);
        try {
            return preferences.getString(key, _default);
        } catch (ClassCastException ex) {
            // Value stored is a completely different type (should never happen).
            removeConflictingPreferenceKeyValue(key);
            return _default;
        }
    }

    @NonNull
    public <T extends Enum<?>> T getEnum(@NonNull String key, @NonNull T _default) {
        Objects.requireNonNull(_default);
        try {
            String enumName = preferences.getString(key, null);
            if (enumName != null) {
                try {
                    // noinspection unchecked
                    return (T) Enum.valueOf(_default.getClass(), enumName);
                } catch (IllegalArgumentException ex) {
                    // Info level to allow removing enum values in the future without showing any user errors.
                    Logger.printInfo(() -> "Using default, and ignoring unknown enum value: "  + enumName);
                    removeKey(key);
                }
            }
        } catch (ClassCastException ex) {
            // Value stored is a completely different type (should never happen).
            removeConflictingPreferenceKeyValue(key);
        }
        return _default;
    }

    public boolean getBoolean(@NonNull String key, boolean _default) {
        try {
            return preferences.getBoolean(key, _default);
        } catch (ClassCastException ex) {
            // Value stored is a completely different type (should never happen).
            removeConflictingPreferenceKeyValue(key);
            return _default;
        }
    }

    @NonNull
    public Integer getIntegerString(@NonNull String key, @NonNull Integer _default) {
        try {
            String value = preferences.getString(key, null);
            if (value != null) {
                return Integer.valueOf(value);
            }
        } catch (ClassCastException | NumberFormatException ex) {
            try {
                // Old data previously stored as primitive.
                return preferences.getInt(key, _default);
            } catch (ClassCastException ex2) {
                // Value stored is a completely different type (should never happen).
                removeConflictingPreferenceKeyValue(key);
            }
        }
        return _default;
    }

    @NonNull
    public Long getLongString(@NonNull String key, @NonNull Long _default) {
        try {
            String value = preferences.getString(key, null);
            if (value != null) {
                return Long.valueOf(value);
            }
        } catch (ClassCastException | NumberFormatException ex) {
            try {
                return preferences.getLong(key, _default);
            } catch (ClassCastException ex2) {
                removeConflictingPreferenceKeyValue(key);
            }
        }
        return _default;
    }

    @NonNull
    public Float getFloatString(@NonNull String key, @NonNull Float _default) {
        try {
            String value = preferences.getString(key, null);
            if (value != null) {
                return Float.valueOf(value);
            }
        } catch (ClassCastException | NumberFormatException ex) {
            try {
                return preferences.getFloat(key, _default);
            } catch (ClassCastException ex2) {
                removeConflictingPreferenceKeyValue(key);
            }
        }
        return _default;
    }

    @NonNull
    @Override
    public String toString() {
        return name;
    }
}
