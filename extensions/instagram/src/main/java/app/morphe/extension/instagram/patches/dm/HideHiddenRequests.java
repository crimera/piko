/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.dm;

import android.view.View;
import android.view.ViewGroup;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;

/** Hides the button that opens the hidden requests (now called spam) in the message requests page. */
public final class HideHiddenRequests {

    private HideHiddenRequests() { }

    /** Called with the row once it is inflated; a hidden view in a list would still take its height, so it gets none. */
    public static void hideRow(View row) {
        try {
            if (row == null || !Pref.hideHiddenRequests()) return;
            row.setVisibility(View.GONE);
            ViewGroup.LayoutParams params = row.getLayoutParams();
            if (params != null) {
                params.height = 0;
                row.setLayoutParams(params);
            }
        } catch (Throwable t) {
            Logger.printException(() -> "HideHiddenRequests.hideRow failed", t);
        }
    }
}
