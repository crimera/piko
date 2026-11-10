/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.comment.copyTextButton;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;

import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

@SuppressWarnings("unused")
public final class CopyUsernameButton {
    public static final CopyUsernameButton A00 = new CopyUsernameButton();

    public static int getLabelResourceId() {
        return ResourceUtils.getIdentifier(ResourceType.STRING, "piko_copy_username");
    }

    public static String getLabel(Context context, int resourceId) {
        // Instagram's downloaded translations can reuse IDs assigned to added Piko strings.
        if (resourceId == getLabelResourceId()) {
            return str("piko_copy_username");
        }
        return context.getString(resourceId);
    }
}
