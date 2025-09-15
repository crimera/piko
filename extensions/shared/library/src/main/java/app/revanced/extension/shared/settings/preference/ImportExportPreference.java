package app.revanced.extension.shared.settings.preference;

import static app.revanced.extension.shared.StringRef.str;
import static app.revanced.extension.shared.Utils.dipToPixels;

import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Pair;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.settings.Setting;

@SuppressWarnings({"unused", "deprecation"})
public class ImportExportPreference extends EditTextPreference implements Preference.OnPreferenceClickListener {

    private String existingSettings;

    private void init() {
        setSelectable(true);

        EditText editText = getEditText();
        editText.setTextIsSelectable(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            editText.setAutofillHints((String) null);
        }
        editText.setInputType(editText.getInputType() | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_PT, 7); // Use a smaller font to reduce text wrap.

        setOnPreferenceClickListener(this);
    }

    public ImportExportPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }
    public ImportExportPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    public ImportExportPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    public ImportExportPreference(Context context) {
        super(context);
        init();
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        try {
            // Must set text before showing dialog,
            // otherwise text is non-selectable if this preference is later reopened.
            existingSettings = Setting.exportToJson(getContext());
            getEditText().setText(existingSettings);
        } catch (Exception ex) {
            Logger.printException(() -> "showDialog failure", ex);
        }
        return true;
    }

    @Override
    protected void showDialog(Bundle state) {
        try {
            Context context = getContext();
            EditText editText = getEditText();

            // Create a custom dialog with the EditText.
            Pair<Dialog, LinearLayout> dialogPair = Utils.createCustomDialog(
                    context,
                    str("revanced_pref_import_export_title"), // Title.
                    null,     // No message (EditText replaces it).
                    editText, // Pass the EditText.
                    str("revanced_settings_import"), // OK button text.
                    () -> importSettings(context, editText.getText().toString()), // OK button action.
                    () -> {}, // Cancel button action (dismiss only).
                    str("revanced_settings_import_copy"), // Neutral button (Copy) text.
                    () -> {
                        // Neutral button (Copy) action. Show the user the settings in JSON format.
                        Utils.setClipboard(editText.getText());
                    },
                    true // Dismiss dialog when onNeutralClick.
            );

            // Show the dialog.
            dialogPair.first.show();
        } catch (Exception ex) {
            Logger.printException(() -> "showDialog failure", ex);
        }
    }

    private void importSettings(Context context, String replacementSettings) {
        try {
            if (replacementSettings.equals(existingSettings)) {
                return;
            }
            AbstractPreferenceFragment.settingImportInProgress = true;

            final boolean rebootNeeded = Setting.importFromJSON(context, replacementSettings);
            if (rebootNeeded) {
                AbstractPreferenceFragment.showRestartDialog(context);
            }
        } catch (Exception ex) {
            Logger.printException(() -> "importSettings failure", ex);
        } finally {
            AbstractPreferenceFragment.settingImportInProgress = false;
        }
    }
}
