package app.revanced.extension.shared.settings.preference;

import static app.revanced.extension.shared.StringRef.str;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.util.Pair;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import java.util.Objects;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.settings.Setting;

@SuppressWarnings({"unused", "deprecation"})
public class ResettableEditTextPreference extends EditTextPreference {

    /**
     * Setting to reset.
     */
    @Nullable
    private Setting<?> setting;

    public ResettableEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
    public ResettableEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
    public ResettableEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public ResettableEditTextPreference(Context context) {
        super(context);
    }

    public void setSetting(@Nullable Setting<?> setting) {
        this.setting = setting;
    }

    @Override
    protected void showDialog(Bundle state) {
        try {
            Context context = getContext();
            EditText editText = getEditText();

            // Resolve setting if not already set.
            if (setting == null) {
                String key = getKey();
                if (key != null) {
                    setting = Setting.getSettingFromPath(key);
                }
            }

            // Set initial EditText value to the current persisted value or empty string.
            String initialValue = getText() != null ? getText() : "";
            editText.setText(initialValue);
            editText.setSelection(initialValue.length()); // Move cursor to end.

            // Create custom dialog.
            String neutralButtonText = (setting != null) ? str("revanced_settings_reset") : null;
            Pair<Dialog, LinearLayout> dialogPair = Utils.createCustomDialog(
                    context,
                    getTitle() != null ? getTitle().toString() : "", // Title.
                    null,     // Message is replaced by EditText.
                    editText, // Pass the EditText.
                    null,     // OK button text.
                    () -> {
                        // OK button action. Persist the EditText value when OK is clicked.
                        String newValue = editText.getText().toString();
                        if (callChangeListener(newValue)) {
                            setText(newValue);
                        }
                    },
                    () -> {}, // Cancel button action (dismiss only).
                    neutralButtonText, // Neutral button text (Reset).
                    () -> {
                        // Neutral button action.
                        if (setting != null) {
                            try {
                                String defaultStringValue = Objects.requireNonNull(setting).defaultValue.toString();
                                editText.setText(defaultStringValue);
                                editText.setSelection(defaultStringValue.length()); // Move cursor to end of text.
                            } catch (Exception ex) {
                                Logger.printException(() -> "reset failure", ex);
                            }
                        }
                    },
                    false // Do not dismiss dialog when onNeutralClick.
            );

            // Show the dialog.
            dialogPair.first.show();
        } catch (Exception ex) {
            Logger.printException(() -> "showDialog failure", ex);
        }
    }
}
