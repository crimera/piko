/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.instagram.patches.customise.font.FontStorage;

/**
 * One of the fonts the user added, or {@link FontStorage#SYSTEM_FONT} itself. The row shows the
 * font in its own face, marks the font that is chosen, and deletes the font on a long press - the
 * system font is never a file, so {@link #confirmDelete} leaves it alone.
 *
 * The long press is not wired here: {@link #onCreateView} runs once per recycled row {@link View},
 * not once per font, and a listener attached to that view would keep firing for whichever
 * {@code FontPref} happened to create it long after the list has scrolled it on to a different
 * font. {@code SettingsActivity}'s list resolves the current item by position on every long press
 * instead, and calls {@link #confirmDelete} on it directly.
 */
public class FontPref extends android.preference.Preference {
    private static final String TAG_CHECK = "piko_font_pref_check";

    private final Context context;
    private final String fontFileName;
    private final FontSelection selection;

    public FontPref(Context context, String fontFileName, FontSelection selection) {
        super(context);
        this.context = context;
        this.fontFileName = fontFileName;
        this.selection = selection;
        setKey("piko_font_" + fontFileName);
        setTitle(FontStorage.displayName(fontFileName));
        setPersistent(false);
    }

    @Override
    protected void onClick() {
        if (!isEnabled() || FontStorage.isSelected(fontFileName)) {
            return;
        }

        FontStorage.select(fontFileName);
        selection.notifySelectionChanged();
        PikoUtils.toast(str("piko_restart_app"));
    }

    /** Redraws the row so the mark sits on whichever font is chosen now. */
    void refresh() {
        notifyChanged();
    }

    /** What a long press opens - a no-op for {@link FontStorage#SYSTEM_FONT}, which stays put. */
    public void confirmDelete() {
        if (!isEnabled() || FontStorage.isSystemFont(fontFileName)) {
            return;
        }

        new AlertDialog.Builder(InstagramPreferenceStyle.dialogContext(context))
                .setTitle(str("piko_pref_delete_font_confirm"))
                .setMessage(FontStorage.displayName(fontFileName))
                .setNegativeButton(str("piko_cancel"), null)
                .setPositiveButton(str("piko_ok"), (dialog, which) -> delete())
                .show();
    }

    private void delete() {
        boolean deleted = FontStorage.delete(fontFileName);
        PikoUtils.toast(str(deleted
                ? "piko_pref_delete_font_success"
                : "piko_pref_delete_font_fail"));

        // The row itself has to go, which the list cannot do in place.
        selection.notifyListChanged();
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        LinearLayout row = InstagramPreferenceStyle.createPreferenceView(
                context,
                InstagramPreferenceStyle.TRAILING_NONE
        );

        ImageView check = new ImageView(context);
        check.setTag(TAG_CHECK);
        check.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(check, new LinearLayout.LayoutParams(
                InstagramPreferenceStyle.dp(context, 24),
                InstagramPreferenceStyle.dp(context, 24)
        ));

        return row;
    }

    @Override
    protected void onBindView(View view) {
        boolean selected = FontStorage.isSelected(fontFileName);
        boolean enabled = isEnabled();

        InstagramPreferenceStyle.bindText(this, view);
        InstagramPreferenceStyle.setPressedHighlightEnabled(view, enabled);

        TextView title = InstagramPreferenceStyle.findTitle(view);
        if (title != null) {
            // Each font is shown in its own face, so the list can be read as a preview.
            Typeface typeface = FontStorage.previewTypeface(fontFileName);
            title.setTypeface(typeface == null ? Typeface.DEFAULT : typeface,
                    selected ? Typeface.BOLD : Typeface.NORMAL);
        }

        ImageView check = view.findViewWithTag(TAG_CHECK);
        if (check != null) {
            check.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
            check.setAlpha(enabled ? 1.0f : 0.5f);
            check.setContentDescription(selected ? str("piko_pref_font_selected") : null);
            if (selected) {
                UI.setThemedIcon(check, UI.DRAWABLE_CHECK_ICON, "igds_color_primary_icon");
            }
        }
    }
}
