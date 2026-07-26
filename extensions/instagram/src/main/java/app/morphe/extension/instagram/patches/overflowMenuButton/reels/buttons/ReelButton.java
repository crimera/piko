/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches.overflowMenuButton.reels.buttons;

import android.view.View;
import android.content.Context;

public abstract class ReelButton implements View.OnClickListener {
    public final Context context;
    public final Object mediaObject;
    public final int currentMediaIndex;

    public ReelButton(Context context, Object mediaObject, int currentMediaIndex) {
        this.context = context;
        this.mediaObject = mediaObject;
        this.currentMediaIndex = currentMediaIndex;
    }

    // Every subclass will be forced to implement its own specific click logic
    @Override
    public abstract void onClick(View view);
}