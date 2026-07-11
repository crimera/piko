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
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

/**
 * The bold "X" brand icon used as an alternative to the default Piko settings gear.
 * Drawn as two crossing rectangular bars with sharp (non-rounded) ends to match the
 * reference art's geometry, in a single flat color so it themes like every other
 * action-bar icon (adapts to light/dark via the host ImageView's color filter).
 */
public class PikoXIcon extends Drawable {

    private final Paint fillPaint;
    private final int sizePx;

    public PikoXIcon(Context context) {
        sizePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 24, context.getResources().getDisplayMetrics());

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.WHITE);
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
        float cx = bounds.centerX();
        float cy = bounds.centerY();
        float w = bounds.width();
        float h = bounds.height();

        float barLength = (float) Math.hypot(w, h) * 0.62f;
        float halfThickness = w * 0.155f;

        canvas.save();
        canvas.rotate(45, cx, cy);
        canvas.drawRect(cx - barLength / 2f, cy - halfThickness, cx + barLength / 2f, cy + halfThickness, fillPaint);
        canvas.restore();

        canvas.save();
        canvas.rotate(-45, cx, cy);
        canvas.drawRect(cx - barLength / 2f, cy - halfThickness, cx + barLength / 2f, cy + halfThickness, fillPaint);
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        fillPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
