/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches.overflowMenuButton.reels.buttons;

import android.view.View;
import android.content.Context;
import app.morphe.extension.crimera.ObjectBrowser;
import app.morphe.extension.instagram.entity.MediaData;

public class DebugButton extends ReelButton {
    public DebugButton(Context context, Object mediaObject) {
        super(context, mediaObject);
    }

    @Override
    public void onClick(View view) {
        ObjectBrowser.browseObject(this.context, new MediaData(this.mediaObject));
    }
}