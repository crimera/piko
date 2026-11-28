/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings;

import android.content.pm.ShortcutManager;
import android.os.Build;

public final class ShortcutLimit {
    private ShortcutLimit() {}

    public static int getDynamicLimit(ShortcutManager manager) {
        int limit = manager.getMaxShortcutCountPerActivity();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) return limit;

        // Instagram counts only dynamic entries on the legacy addDynamicShortcuts path.
        return Math.max(0, limit - manager.getManifestShortcuts().size());
    }
}
