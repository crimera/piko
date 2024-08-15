package app.revanced.integrations.twitter.patches.links;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import app.revanced.integrations.twitter.Utils;
import app.revanced.integrations.twitter.settings.Settings;

import java.util.Set;

public final class OpenLinksWithAppChooserPatch {
    public static void openWithChooser(final Context context, final Intent originalIntent, final Bundle bundle) {
        Set<String> categories = originalIntent.getCategories();

        // original credit: TwiFucker, updated for newer build
        if (originalIntent.getAction() == null || !Utils.getBooleanPerf(Settings.MISC_BROWSER_CHOOSER)) {
            context.startActivity(originalIntent, bundle);
            return;
        }

        final Intent intent = new Intent("android.intent.action.VIEW", originalIntent.getData());
        context.startActivity(Intent.createChooser(intent, null));
    }
}
