package app.revanced.extension.shared.settings.preference;

import static app.revanced.extension.shared.StringRef.str;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.util.Pair;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

import app.revanced.extension.shared.Logger;
import app.revanced.extension.shared.Utils;
import app.revanced.extension.shared.settings.BaseSettings;
import app.revanced.extension.shared.settings.BooleanSetting;
import app.revanced.extension.shared.settings.Setting;

@SuppressWarnings("deprecation")
public abstract class AbstractPreferenceFragment extends PreferenceFragment {

    /**
     * Indicates that if a preference changes,
     * to apply the change from the Setting to the UI component.
     */
    public static boolean settingImportInProgress;

    /**
     * Prevents recursive calls during preference <-> UI syncing from showing extra dialogs.
     */
    private static boolean updatingPreference;

    /**
     * Used to prevent showing reboot dialog, if user cancels a setting user dialog.
     */
    private static boolean showingUserDialogMessage;

    /**
     * Confirm and restart dialog button text and title.
     * Set by subclasses if Strings cannot be added as a resource.
     */
    @Nullable
    protected static String restartDialogButtonText, restartDialogTitle, confirmDialogTitle, restartDialogMessage;

    private final SharedPreferences.OnSharedPreferenceChangeListener listener = (sharedPreferences, str) -> {
        try {
            if (updatingPreference) {
                Logger.printDebug(() -> "Ignoring preference change as sync is in progress");
                return;
            }

            Setting<?> setting = Setting.getSettingFromPath(Objects.requireNonNull(str));
            if (setting == null) {
                return;
            }
            Preference pref = findPreference(str);
            if (pref == null) {
                return;
            }
            Logger.printDebug(() -> "Preference changed: " + setting.key);

            if (!settingImportInProgress && !showingUserDialogMessage) {
                if (setting.userDialogMessage != null && !prefIsSetToDefault(pref, setting)) {
                    // Do not change the setting yet, to allow preserving whatever
                    // list/text value was previously set if it needs to be reverted.
                    showSettingUserDialogConfirmation(pref, setting);
                    return;
                } else if (setting.rebootApp) {
                    showRestartDialog(getContext());
                }
            }

            updatingPreference = true;
            // Apply 'Setting <- Preference', unless during importing when it needs to be 'Setting -> Preference'.
            // Updating here can cause a recursive call back into this same method.
            updatePreference(pref, setting, true, settingImportInProgress);
            // Update any other preference availability that may now be different.
            updateUIAvailability();
            updatingPreference = false;
        } catch (Exception ex) {
            Logger.printException(() -> "OnSharedPreferenceChangeListener failure", ex);
        }
    };

    /**
     * Initialize this instance, and do any custom behavior.
     * <p>
     * To ensure all {@link Setting} instances are correctly synced to the UI,
     * it is important that subclasses make a call or otherwise reference their Settings class bundle
     * so all app specific {@link Setting} instances are loaded before this method returns.
     */
    protected void initialize() {
        String preferenceResourceName = BaseSettings.SHOW_MENU_ICONS.get()
                ? "revanced_prefs_icons"
                : "revanced_prefs";
        final var identifier = Utils.getResourceIdentifier(preferenceResourceName, "xml");
        if (identifier == 0) return;
        addPreferencesFromResource(identifier);

        PreferenceScreen screen = getPreferenceScreen();
        Utils.sortPreferenceGroups(screen);
        Utils.setPreferenceTitlesToMultiLineIfNeeded(screen);
    }

    private void showSettingUserDialogConfirmation(Preference pref, Setting<?> setting) {
        Utils.verifyOnMainThread();

        final var context = getContext();
        if (confirmDialogTitle == null) {
            confirmDialogTitle = str("revanced_settings_confirm_user_dialog_title");
        }

        showingUserDialogMessage = true;

        Pair<Dialog, LinearLayout> dialogPair = Utils.createCustomDialog(
                context,
                confirmDialogTitle, // Title.
                Objects.requireNonNull(setting.userDialogMessage).toString(), // No message.
                null, // No EditText.
                null, // OK button text.
                () -> {
                    // OK button action. User confirmed, save to the Setting.
                    updatePreference(pref, setting, true, false);

                    // Update availability of other preferences that may be changed.
                    updateUIAvailability();

                    if (setting.rebootApp) {
                        showRestartDialog(context);
                    }
                },
                () -> {
                    // Cancel button action. Restore whatever the setting was before the change.
                    updatePreference(pref, setting, true, true);
                },
                null, // No Neutral button.
                null, // No Neutral button action.
                true  // Dismiss dialog when onNeutralClick.
        );

        dialogPair.first.setOnDismissListener(d -> showingUserDialogMessage = false);

        // Show the dialog.
        dialogPair.first.show();
    }

    /**
     * Updates all Preferences values and their availability using the current values in {@link Setting}.
     */
    protected void updateUIToSettingValues() {
        updatePreferenceScreen(getPreferenceScreen(), true, true);
    }

    /**
     * Updates Preferences availability only using the status of {@link Setting}.
     */
    protected void updateUIAvailability() {
        updatePreferenceScreen(getPreferenceScreen(), false, false);
    }

    /**
     * @return If the preference is currently set to the default value of the Setting.
     */
    protected boolean prefIsSetToDefault(Preference pref, Setting<?> setting) {
        Object defaultValue = setting.defaultValue;
        if (pref instanceof SwitchPreference switchPref) {
            return switchPref.isChecked() == (Boolean) defaultValue;
        }
        String defaultValueString = defaultValue.toString();
        if (pref instanceof EditTextPreference editPreference) {
            return editPreference.getText().equals(defaultValueString);
        }
        if (pref instanceof ListPreference listPref) {
            return listPref.getValue().equals(defaultValueString);
        }

        throw new IllegalStateException("Must override method to handle "
                + "preference type: " + pref.getClass());
    }

    /**
     * Syncs all UI Preferences to any {@link Setting} they represent.
     */
    private void updatePreferenceScreen(@NonNull PreferenceGroup group,
                                        boolean syncSettingValue,
                                        boolean applySettingToPreference) {
        // Alternatively this could iterate thru all Settings and check for any matching Preferences,
        // but there are many more Settings than UI preferences so it's more efficient to only check
        // the Preferences.
        for (int i = 0, prefCount = group.getPreferenceCount(); i < prefCount; i++) {
            Preference pref = group.getPreference(i);
            if (pref instanceof PreferenceGroup subGroup) {
                updatePreferenceScreen(subGroup, syncSettingValue, applySettingToPreference);
            } else if (pref.hasKey()) {
                String key = pref.getKey();
                Setting<?> setting = Setting.getSettingFromPath(key);

                if (setting != null) {
                    updatePreference(pref, setting, syncSettingValue, applySettingToPreference);
                } else if (BaseSettings.DEBUG.get() && (pref instanceof SwitchPreference
                        || pref instanceof EditTextPreference || pref instanceof ListPreference)) {
                    // Probably a typo in the patches preference declaration.
                    Logger.printException(() -> "Preference key has no setting: " + key);
                }
            }
        }
    }

    /**
     * Handles syncing a UI Preference with the {@link Setting} that backs it.
     * If needed, subclasses can override this to handle additional UI Preference types.
     *
     * @param applySettingToPreference If true, then apply {@link Setting} -> Preference.
     *                                 If false, then apply {@link Setting} <- Preference.
     */
    protected void syncSettingWithPreference(@NonNull Preference pref,
                                             @NonNull Setting<?> setting,
                                             boolean applySettingToPreference) {
        if (pref instanceof SwitchPreference switchPref) {
            BooleanSetting boolSetting = (BooleanSetting) setting;
            if (applySettingToPreference) {
                switchPref.setChecked(boolSetting.get());
            } else {
                BooleanSetting.privateSetValue(boolSetting, switchPref.isChecked());
            }
        } else if (pref instanceof EditTextPreference editPreference) {
            if (applySettingToPreference) {
                editPreference.setText(setting.get().toString());
            } else {
                Setting.privateSetValueFromString(setting, editPreference.getText());
            }
        } else if (pref instanceof ListPreference listPref) {
            if (applySettingToPreference) {
                listPref.setValue(setting.get().toString());
            } else {
                Setting.privateSetValueFromString(setting, listPref.getValue());
            }
            updateListPreferenceSummary(listPref, setting);
        } else {
            Logger.printException(() -> "Setting cannot be handled: " + pref.getClass() + ": " + pref);
        }
    }

    /**
     * Updates a UI Preference with the {@link Setting} that backs it.
     *
     * @param syncSetting If the UI should be synced {@link Setting} <-> Preference
     * @param applySettingToPreference If true, then apply {@link Setting} -> Preference.
     *                                 If false, then apply {@link Setting} <- Preference.
     */
    private void updatePreference(@NonNull Preference pref, @NonNull Setting<?> setting,
                                  boolean syncSetting, boolean applySettingToPreference) {
        if (!syncSetting && applySettingToPreference) {
            throw new IllegalArgumentException();
        }

        if (syncSetting) {
            syncSettingWithPreference(pref, setting, applySettingToPreference);
        }

        updatePreferenceAvailability(pref, setting);
    }

    protected void updatePreferenceAvailability(@NonNull Preference pref, @NonNull Setting<?> setting) {
        pref.setEnabled(setting.isAvailable());
    }

    protected void updateListPreferenceSummary(ListPreference listPreference, Setting<?> setting) {
        String objectStringValue = setting.get().toString();
        final int entryIndex = listPreference.findIndexOfValue(objectStringValue);
        if (entryIndex >= 0) {
            listPreference.setSummary(listPreference.getEntries()[entryIndex]);
        } else {
            // Value is not an available option.
            // User manually edited import data, or options changed and current selection is no longer available.
            // Still show the value in the summary, so it's clear that something is selected.
            listPreference.setSummary(objectStringValue);
        }
    }

    public static void showRestartDialog(Context context) {
        Utils.verifyOnMainThread();
        if (restartDialogTitle == null) {
            restartDialogTitle = str("revanced_settings_restart_title");
        }
        if (restartDialogMessage == null) {
            restartDialogMessage = str("revanced_settings_restart_dialog_message");
        }
        if (restartDialogButtonText == null) {
            restartDialogButtonText = str("revanced_settings_restart");
        }

        Pair<Dialog, LinearLayout> dialogPair = Utils.createCustomDialog(context,
                restartDialogTitle,              // Title.
                restartDialogMessage,            // Message.
                null,                            // No EditText.
                restartDialogButtonText,         // OK button text.
                () -> Utils.restartApp(context), // OK button action.
                () -> {},                        // Cancel button action (dismiss only).
                null,                            // No Neutral button text.
                null,                            // No Neutral button action.
                true                             // Dismiss dialog when onNeutralClick.
        );

        // Show the dialog.
        dialogPair.first.show();
    }

    @SuppressLint("ResourceType")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            PreferenceManager preferenceManager = getPreferenceManager();
            preferenceManager.setSharedPreferencesName(Setting.preferences.name);

            // Must initialize before adding change listener,
            // otherwise the syncing of Setting -> UI
            // causes a callback to the listener even though nothing changed.
            initialize();
            updateUIToSettingValues();

            preferenceManager.getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);
        } catch (Exception ex) {
            Logger.printException(() -> "onCreate() failure", ex);
        }
    }

    @Override
    public void onDestroy() {
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(listener);
        super.onDestroy();
    }
}
