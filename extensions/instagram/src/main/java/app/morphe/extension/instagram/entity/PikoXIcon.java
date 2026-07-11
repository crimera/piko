/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.entity;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

/**
 * The red gradient "X" brand icon used as an alternative to the default Piko
 * settings gear. Unlike the other action-bar icons, this one is a brand mark:
 * it keeps its own red gradient in both light and dark theme, so
 * {@link #setColorFilter(ColorFilter)} intentionally ignores whatever tint the
 * host ImageView tries to apply.
 */
public class PikoXIcon extends Drawable {

    private final Paint strokePaint;
    private final int sizePx;

    public PikoXIcon(Context context) {
        sizePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 24, context.getResources().getDisplayMetrics());

        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeCap(Paint.Cap.BUTT);
        strokePaint.setStrokeJoin(Paint.Join.MITER);
    }

    @Override
    public int getIntrinsicWidth() {
        return sizePx;
    }

    @Override
    public int getIntrinsicHeight() {
        return sizePx;
    }

    @Override
    public void draw(Canvas canvas) {
        RectF bounds = new RectF(getBounds());
        float w = bounds.width();
        float h = bounds.height();

        float strokeW = w * 0.30f;
        strokePaint.setStrokeWidth(strokeW);
        strokePaint.setShader(new LinearGradient(
                bounds.left, bounds.top, bounds.left, bounds.top + h,
                Color.parseColor("#FF3B30"), Color.parseColor("#7A0000"),
                Shader.TileMode.CLAMP));

        float inset = w * 0.18f;
        canvas.drawLine(bounds.left + inset, bounds.top + inset,
                bounds.left + w - inset, bounds.top + h - inset, strokePaint);
        canvas.drawLine(bounds.left + w - inset, bounds.top + inset,
                bounds.left + inset, bounds.top + h - inset, strokePaint);
    }

    @Override
    public void setAlpha(int alpha) {
        strokePaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // Intentionally ignored: this is a brand mark, not a themed glyph.
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
