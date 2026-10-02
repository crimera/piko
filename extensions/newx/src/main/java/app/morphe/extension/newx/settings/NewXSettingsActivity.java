package app.morphe.extension.newx.settings;

import android.content.Intent;

import androidx.annotation.Nullable;

import app.morphe.extension.crimera.settings.PikoSettingsActivity;
import app.morphe.extension.newx.featureswitches.FeatureSwitchImportExport;
import app.morphe.extension.newx.misc.UpdateFont;

/**
 * The activity the patch declares in the manifest. Layout, navigation and theming live in the shared
 * {@link PikoSettingsActivity}; NewX supplies its configuration through {@link NewXSettingsHost} and
 * only handles the results of the NewX features launched from its screens.
 */
public final class NewXSettingsActivity extends PikoSettingsActivity {
    @Override
    protected void onUnhandledActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (FeatureSwitchImportExport.handleActivityResult(this, requestCode, resultCode, data)) {
            return;
        }
        UpdateFont.handleActivityResult(this, requestCode, resultCode, data);
    }
}
