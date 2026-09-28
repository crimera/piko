/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.AlertDialog;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.instagram.patches.feed.ChangeLikeAnimationPatch;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.settings.Settings;
import app.morphe.extension.instagram.settings.preference.Helper;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

public final class LikeAnimationPreference extends ListPref {
    private int selectedIndex;
    private ImageView preview;
    private Drawable animation;
    private AnimatorSet defaultAnimation;
    private final Runnable playPreview = this::playPreview;

    public LikeAnimationPreference(Context context) {
        super(context);
        Helper helper = new Helper(context);
        setOnPreferenceChangeListener(helper::setValue);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        selectedIndex = findIndexOfValue(getValue());
        if (selectedIndex < 0) selectedIndex = 0;

        Context context = getContext();
        int padding = InstagramPreferenceStyle.dp(context, 20);
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(padding, padding, padding, 0);

        TextView title = new TextView(context);
        title.setText(getDialogTitle());
        title.setTextSize(20);
        title.setTextColor(InstagramPreferenceStyle.primaryTextColor());
        header.addView(title);

        preview = new ImageView(context);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setContentDescription(str("piko_like_animation_preview"));
        header.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, InstagramPreferenceStyle.dp(context, 128)));

        builder.setCustomTitle(header);
        builder.setSingleChoiceItems(getEntries(), selectedIndex, (dialog, index) -> {
            selectedIndex = index;
            playPreview();
        });
        builder.setPositiveButton(android.R.string.ok, this);
        builder.setNegativeButton(android.R.string.cancel, this);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        selectedIndex = ((AlertDialog) getDialog()).getListView().getCheckedItemPosition();
        preview.post(playPreview);
    }

    private void playPreview() {
        if (preview == null) return;
        stopPreview();
        preview.setContentDescription(str("piko_like_animation_preview"));
        String selection = getEntryValues()[selectedIndex].toString();
        try {
            String name = ChangeLikeAnimationPatch.previewAnimation(selection);
            if (Settings.CHANGE_LIKE_ANIMATION.defaultValue.equals(name)) {
                playDefaultPreview();
                return;
            }
            animation = ChangeLikeAnimationPatch.previewDrawable(getContext(), name);
            if (animation == null) {
                showUnavailablePreview();
                return;
            }
            preview.setImageDrawable(animation);
            ChangeLikeAnimationPatch.startPreview(animation);
        } catch (Exception | LinkageError exception) {
            stopPreview();
            showUnavailablePreview();
            Logger.printException(() -> "Could not preview like animation", exception);
        }
    }

    private void playDefaultPreview() {
        // The platform dialog theme does not define Instagram's heart gradient colors.
        Context instagramContext = Utils.getActivity();
        Drawable heart = instagramContext == null ? null
                : ChangeLikeAnimationPatch.defaultPreviewDrawable(instagramContext);
        if (heart == null) {
            showUnavailablePreview();
            return;
        }
        preview.setImageDrawable(heart);
        // The default heart is driven by the feed's spring, not the custom drawable player.
        AnimatorSet appear = new AnimatorSet();
        appear.playTogether(ObjectAnimator.ofFloat(preview, View.SCALE_X, 0.2f, 1f),
                ObjectAnimator.ofFloat(preview, View.SCALE_Y, 0.2f, 1f),
                ObjectAnimator.ofFloat(preview, View.ALPHA, 0f, 1f));
        appear.setDuration(350);
        appear.setInterpolator(new OvershootInterpolator());
        ObjectAnimator disappear = ObjectAnimator.ofFloat(preview, View.ALPHA, 1f, 0f);
        disappear.setStartDelay(350);
        disappear.setDuration(250);
        defaultAnimation = new AnimatorSet();
        defaultAnimation.playSequentially(appear, disappear);
        defaultAnimation.start();
    }

    private void stopPreview() {
        if (defaultAnimation != null) {
            defaultAnimation.cancel();
            defaultAnimation = null;
        }
        if (animation != null) {
            try {
                ChangeLikeAnimationPatch.stopPreview(animation);
                animation.setVisible(false, false);
            } catch (Exception | LinkageError exception) {
                Logger.printException(() -> "Could not stop like animation preview", exception);
            }
            animation = null;
        }
        if (preview != null) {
            preview.removeCallbacks(playPreview);
            preview.setImageDrawable(null);
            preview.clearColorFilter();
            preview.setAlpha(1f);
            preview.setScaleX(1f);
            preview.setScaleY(1f);
        }
    }

    private void showUnavailablePreview() {
        UI.setThemedIcon(preview, UI.DRAWABLE_FRAME_CROSSED_ICON);
        preview.setContentDescription(str("piko_like_animation_preview_unavailable"));
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        // Skip ListPreference's automatic save because this dialog saves its own selection.
        super.onDialogClosed(false);
        stopPreview();
        preview = null;
        if (positiveResult) {
            String value = getEntryValues()[selectedIndex].toString();
            if (callChangeListener(value)) setValue(value);
        }
    }
}
