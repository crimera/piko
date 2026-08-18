package app.morphe.extension.xlite.settings;

import androidx.annotation.Nullable;

public interface SettingsValueValidator {
    @Nullable String errorMessage(String value);
}
