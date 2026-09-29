/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.customise.font;

import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.style.TypefaceSpan;

import androidx.annotation.RequiresApi;

/** Replaces the platform {@link TypefaceSpan}, which sets its font inside the framework. */
@SuppressWarnings("unused")
public final class CustomFontTypefaceSpan extends TypefaceSpan {
    public CustomFontTypefaceSpan(String family) {
        super(family);
    }

    @RequiresApi(28)
    public CustomFontTypefaceSpan(Typeface typeface) {
        super(typeface);
    }

    @Override
    public void updateDrawState(TextPaint paint) {
        super.updateDrawState(paint);
        paint.setTypeface(CustomFont.assign(paint.getTypeface()));
    }

    @Override
    public void updateMeasureState(TextPaint paint) {
        super.updateMeasureState(paint);
        paint.setTypeface(CustomFont.assign(paint.getTypeface()));
    }
}
