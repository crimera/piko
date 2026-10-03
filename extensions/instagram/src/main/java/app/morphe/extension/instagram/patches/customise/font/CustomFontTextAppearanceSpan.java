/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.customise.font;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextPaint;
import android.text.style.TextAppearanceSpan;

/** Replaces the platform {@link TextAppearanceSpan}, which sets its font inside the framework. */
@SuppressWarnings("unused")
public final class CustomFontTextAppearanceSpan extends TextAppearanceSpan {
    public CustomFontTextAppearanceSpan(Context context, int appearance) {
        super(context, appearance);
    }

    public CustomFontTextAppearanceSpan(Context context, int appearance, int colorList) {
        super(context, appearance, colorList);
    }

    public CustomFontTextAppearanceSpan(
            String family, int style, int size, ColorStateList color, ColorStateList linkColor) {
        super(family, style, size, color, linkColor);
    }

    // updateDrawState() calls this too, so it covers drawing as well.
    @Override
    public void updateMeasureState(TextPaint paint) {
        super.updateMeasureState(paint);
        paint.setTypeface(CustomFont.assign(paint.getTypeface()));
    }
}
