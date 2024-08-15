package app.revanced.integrations.shared.settings.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceFragment;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.revanced.integrations.shared.Logger;
import app.revanced.integrations.shared.Utils;
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
        preferences.edit().remove(key).apply();
    }

    private void saveObjectAsString(@NonNull String key, @Nullable Object value) {
        preferences.edit().putString(key, (value == null ? null : value.toString())).apply();
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

    public Set<String> getSet(@NonNull String key, Set<String> _default) {
        try {
            return preferences.getStringSet(key, _default);
        } catch (ClassCastException ex) {
            // Value stored is a completely different type (should never happen).
            removeConflictingPreferenceKeyValue(key);
            return _default;
        }
    }
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
            return _default;
        } catch (ClassCastException ex) {
            try {
                // Old data previously stored as primitive.
                return preferences.getInt(key, _default);
            } catch (ClassCastException ex2) {
                // Value stored is a completely different type (should never happen).
                removeConflictingPreferenceKeyValue(key);
                return _default;
            }
        }
    }

    @NonNull
    public Long getLongString(@NonNull String key, @NonNull Long _default) {
        try {
            String value = preferences.getString(key, null);
            if (value != null) {
                return Long.valueOf(value);
            }
            return _default;
        } catch (ClassCastException ex) {
            try {
                return preferences.getLong(key, _default);
            } catch (ClassCastException ex2) {
                removeConflictingPreferenceKeyValue(key);
                return _default;
            }
        }
    }

    @NonNull
    public Float getFloatString(@NonNull String key, @NonNull Float _default) {
        try {
            String value = preferences.getString(key, null);
            if (value != null) {
                return Float.valueOf(value);
            }
            return _default;
        } catch (ClassCastException ex) {
            try {
                return preferences.getFloat(key, _default);
            } catch (ClassCastException ex2) {
                removeConflictingPreferenceKeyValue(key);
                return _default;
            }
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

    public void removeKey(@NonNull String key) {
        preferences.edit().remove(key).commit();
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

    @NonNull
    @Override
    public String toString() {
        return name;
    }
}
