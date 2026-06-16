/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
*/


package app.morphe.extension.instagram.patches.overflowMenuButton.reels;

import app.morphe.extension.instagram.patches.overflowMenuButton.reels.buttons.ReelButton;

public class ReelOverflowButton {
    public String drawableResId;
    public ReelButton reelButton;
    public String buttonText;

    public ReelOverflowButton(String drawableResId, ReelButton reelButton, String buttonText) {
        this.drawableResId = drawableResId;
        this.reelButton = reelButton;
        this.buttonText = buttonText;
    }
}