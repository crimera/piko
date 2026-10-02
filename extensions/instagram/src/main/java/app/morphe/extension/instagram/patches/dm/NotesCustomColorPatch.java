/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.notes;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.settings.preference.widgets.InstagramPreferenceStyle;
import app.morphe.extension.instagram.utils.Pref;

@SuppressWarnings("unused")
public class NotesCustomColorPatch {

    private static final String POG_VIEW_CLASS =
            "instagram.features.direct.inbox.notes.creation.presentation.view.NotesCreationPogView";

    private static View sColorButton;
    private static ViewGroup sParent;
    private static ViewTreeObserver.OnGlobalLayoutListener sGlobalLayoutListener;

    // In-memory only, never persisted — forgotten on composer close (post or cancel) and on app restart.
    private static volatile boolean sCustomColorEnabled = false;
    private static volatile String sCustomColorHex = null;

    public static void attachThemeButtonLongClick(View themeButton) {
        if (!Pref.enableNotesCustomTextColor()) return;

        themeButton.setOnLongClickListener(v -> {
            showColorDialog(themeButton);
            return true;
        });

        themeButton.post(() -> addVisibleColorButton(themeButton));

        themeButton.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {}

            @Override
            public void onViewDetachedFromWindow(View v) {
                removeVisibleColorButton();
                sCustomColorHex = null;
                sCustomColorEnabled = false;
            }
        });
    }

    private static void addVisibleColorButton(View themeButton) {
        removeVisibleColorButton();

        if (!(themeButton.getParent() instanceof ViewGroup)) return;

        // Parent is themeButton's own ConstraintLayout, not android.R.id.content — being in the same subtree that gets covered/detached by another screen (song search, GIF picker) is what makes the button hide/reappear correctly.
        ViewGroup parent = (ViewGroup) themeButton.getParent();

        View pogView = findViewByClassName(themeButton.getRootView(), POG_VIEW_CLASS);
        View anchor = pogView != null ? pogView : themeButton;

        Context context = themeButton.getContext();
        int size = dp(context, 40);
        View colorButton = buildCircularButton(context, themeButton);
        colorButton.setId(View.generateViewId());

        ViewGroup.LayoutParams params;
        try {
            params = themeButton.getLayoutParams().getClass()
                    .getConstructor(int.class, int.class)
                    .newInstance(size, size);
        } catch (Exception e) {
            return;
        }
        parent.addView(colorButton, params);

        ViewTreeObserver.OnGlobalLayoutListener listener =
                () -> updateColorButtonState(colorButton, anchor, parent, context);
        parent.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        listener.onGlobalLayout();

        sColorButton = colorButton;
        sParent = parent;
        sGlobalLayoutListener = listener;
    }

    private static void updateColorButtonState(View colorButton, View anchor, ViewGroup parent, Context context) {
        if (!anchor.isShown() || anchor.getWidth() == 0) {
            if (colorButton.getVisibility() != View.GONE) colorButton.setVisibility(View.GONE);
            return;
        }
        if (colorButton.getVisibility() != View.VISIBLE) colorButton.setVisibility(View.VISIBLE);

        int[] anchorLoc = new int[2];
        anchor.getLocationOnScreen(anchorLoc);
        int[] parentLoc = new int[2];
        parent.getLocationOnScreen(parentLoc);

        // Top-right corner of the profile photo/bubble, nudged slightly further right/up.
        float newX = anchorLoc[0] - parentLoc[0] + anchor.getWidth() - dp(context, 16);
        float newY = anchorLoc[1] - parentLoc[1] - dp(context, 8);
        if (colorButton.getX() != newX) colorButton.setX(newX);
        if (colorButton.getY() != newY) colorButton.setY(newY);
    }

    private static void removeVisibleColorButton() {
        if (sParent != null && sGlobalLayoutListener != null) {
            sParent.getViewTreeObserver().removeOnGlobalLayoutListener(sGlobalLayoutListener);
        }
        if (sColorButton != null && sColorButton.getParent() instanceof ViewGroup) {
            ((ViewGroup) sColorButton.getParent()).removeView(sColorButton);
        }
        sColorButton = null;
        sParent = null;
        sGlobalLayoutListener = null;
    }

    private static View buildCircularButton(Context context, View themeButton) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0x99000000);

        ImageView colorButton = new ImageView(context);
        colorButton.setBackground(circle);
        int paddingPx = dp(context, 8);
        colorButton.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        try {
            UI.setThemedIcon(colorButton, "instagram_text_pano_filled_24");
        } catch (Exception ignored) {
        }
        if (colorButton.getDrawable() != null) {
            colorButton.setOnClickListener(v -> showColorDialog(themeButton));
            return colorButton;
        }

        TextView fallback = new TextView(context);
        fallback.setBackground(circle);
        fallback.setGravity(Gravity.CENTER);
        fallback.setText("Aa");
        fallback.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        fallback.setTextColor(0xFFFFFFFF);
        fallback.setOnClickListener(v -> showColorDialog(themeButton));
        return fallback;
    }

    private static int dp(Context context, int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, context.getResources().getDisplayMetrics());
    }

    private static void triggerThemeRefresh(View screenView) {
        View pogView = findViewByClassName(screenView.getRootView(), POG_VIEW_CLASS);
        if (pogView != null) pogView.performClick();
    }

    private static View findViewByClassName(View view, String className) {
        if (view.getClass().getName().equals(className)) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findViewByClassName(group.getChildAt(i), className);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void showColorDialog(View themeButton) {
        Context context = themeButton.getContext();
        Context themed = InstagramPreferenceStyle.dialogContext(context);

        EditText input = new EditText(themed);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint(str("piko_notes_custom_color_hint"));

        new AlertDialog.Builder(themed)
                .setTitle(str("piko_notes_custom_color_title"))
                .setView(input)
                .setNegativeButton(str("piko_cancel"), null)
                .setPositiveButton(str("piko_ok"), (dialog, which) -> {
                    String hex = input.getText().toString().trim();
                    if (!isValidHex(hex)) {
                        PikoUtils.toast(str("piko_notes_custom_color_invalid"));
                        return;
                    }
                    if (!hex.startsWith("#")) hex = "#" + hex;
                    sCustomColorHex = hex;
                    sCustomColorEnabled = true;
                    triggerThemeRefresh(themeButton);
                })
                .show();
    }

    public static Integer overrideTextColorArgb(Integer original) {
        if (!sCustomColorEnabled) return original;
        try {
            return 0xFF000000 | (Integer.parseInt(sCustomColorHex.replace("#", ""), 16) & 0xFFFFFF);
        } catch (Exception e) {
            return original;
        }
    }

    public static String overrideTextColorHex(String original) {
        if (!sCustomColorEnabled) return original;
        return isValidHex(sCustomColorHex) ? sCustomColorHex : original;
    }

    public static Integer overrideSecondaryColorIfOwnNote(Integer resolvedPrimaryColor, Integer original) {
        if (!sCustomColorEnabled || resolvedPrimaryColor == null || !isValidHex(sCustomColorHex)) return original;
        try {
            int customArgb = 0xFF000000 | (Integer.parseInt(sCustomColorHex.replace("#", ""), 16) & 0xFFFFFF);
            return resolvedPrimaryColor == customArgb ? Integer.valueOf(customArgb) : original;
        } catch (Exception e) {
            return original;
        }
    }

    public static Integer suppressPhotoTintIfOwnNote(Integer resolvedPrimaryColor) {
        if (!sCustomColorEnabled || resolvedPrimaryColor == null || !isValidHex(sCustomColorHex)) {
            return resolvedPrimaryColor;
        }
        try {
            int customArgb = 0xFF000000 | (Integer.parseInt(sCustomColorHex.replace("#", ""), 16) & 0xFFFFFF);
            return resolvedPrimaryColor == customArgb ? null : resolvedPrimaryColor;
        } catch (Exception e) {
            return resolvedPrimaryColor;
        }
    }

    public static void applyBubbleTextColor(Integer resolvedPrimaryColor, TextView view) {
        if (!sCustomColorEnabled || resolvedPrimaryColor == null || view == null || !isValidHex(sCustomColorHex)) return;
        try {
            int customArgb = 0xFF000000 | (Integer.parseInt(sCustomColorHex.replace("#", ""), 16) & 0xFFFFFF);
            if (resolvedPrimaryColor == customArgb) view.setTextColor(customArgb);
        } catch (Exception ignored) {
        }
    }

    private static boolean isValidHex(String hex) {
        if (hex == null) return false;
        String stripped = hex.startsWith("#") ? hex.substring(1) : hex;
        if (stripped.length() != 6) return false;
        try {
            Integer.parseInt(stripped, 16);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}