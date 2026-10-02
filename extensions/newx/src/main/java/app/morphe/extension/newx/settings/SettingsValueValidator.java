package app.morphe.extension.newx.settings;

import androidx.annotation.Nullable;

public interface SettingsValueValidator {
    @Nullable String errorMessage(String value);
}
