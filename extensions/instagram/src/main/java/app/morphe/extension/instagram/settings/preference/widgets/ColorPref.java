/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.content.Context;
import android.preference.Preference;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import app.morphe.extension.crimera.sharedPreference.SharedPref;
import app.morphe.extension.instagram.settings.StringSetting;
import app.morphe.extension.instagram.utils.HexColor;

/** A row that shows a color setting as a swatch and opens a dialog to change it. */
public class ColorPref extends Preference {
    private static final String TAG_SWATCH = "piko_color_swatch";

    private final StringSetting setting;
    private final String automaticSummary;
    private final String description;
    private final boolean alpha;
    private String color;

    /**
     * @param automatic whether a color left unset is worked out by itself, rather than being Instagram's own.
     * @param alpha whether the color can be made see-through.
     * @param description a line shown under the color, or null.
     */
    public ColorPref(Context context, StringSetting setting, boolean automatic, boolean alpha, String description) {
        super(context);
        this.setting = setting;
        this.alpha = alpha;
        this.automaticSummary = str(automatic ? "piko_color_automatic" : "piko_color_default");
        this.description = description;
        setKey(setting.key);
        // The color is written to the settings by the dialog, as a plain color or nothing.
        setPersistent(false);
        color = HexColor.normalize(SharedPref.getStringPref(setting));
        // A color stored with an alpha where none is allowed is shown, and used, without it.
        if (!alpha && color != null && color.length() == 8) color = color.substring(2);
        showColor();
    }

    private void showColor() {
        String shown = color == null ? automaticSummary : "#" + color;
        setSummary(description == null || description.isEmpty() ? shown : shown + "\n" + description);
    }

    @Override
    protected View onCreateView(ViewGroup parent) {
        View row = InstagramPreferenceStyle.createPreferenceView(getContext(), InstagramPreferenceStyle.TRAILING_NONE);
        View swatch = new View(getContext());
        swatch.setTag(TAG_SWATCH);
        int size = InstagramPreferenceStyle.dp(getContext(), 28);
        ((LinearLayout) row).addView(swatch, new LinearLayout.LayoutParams(size, size));
        return row;
    }

    @Override
    protected void onBindView(View view) {
        InstagramPreferenceStyle.bindText(this, view);
        // Found in the view being bound, as a row Android has used before is handed to another preference.
        View swatch = view.findViewWithTag(TAG_SWATCH);
        if (swatch != null) ColorPickerDialog.paintSwatch(swatch, color);
    }

    @Override
    protected void onClick() {
        ColorPickerDialog.show(getContext(), getTitle(), color, alpha, picked -> {
            if (!SharedPref.setStringPref(setting.key, picked == null ? "" : picked)) return;
            color = picked;
            showColor();
        });
    }
}
