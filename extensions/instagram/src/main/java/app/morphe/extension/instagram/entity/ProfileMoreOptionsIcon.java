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
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

/**
 * Draws a "person in a circle with a bottom-right '...' badge" glyph, matching the
 * same visual weight/size as Instagram's native 24dp outline action-bar icons
 * (e.g. instagram_info_outline_24) so it drops in as a like-for-like replacement.
 */
public class ProfileMoreOptionsIcon extends Drawable {

    private final Paint strokePaint;
    private final Paint dotPaint;
    private final Paint clearPaint;
    private final int sizePx;

    public ProfileMoreOptionsIcon(Context context) {
        sizePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 24, context.getResources().getDisplayMetrics());

        strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setColor(Color.WHITE);
        strokePaint.setStrokeCap(Paint.Cap.ROUND);
        strokePaint.setStrokeJoin(Paint.Join.ROUND);
        strokePaint.setStrokeWidth(sizePx * 0.075f);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(Color.WHITE);

        clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
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

        int layer = canvas.saveLayer(bounds, null);

        // Outer avatar ring.
        float outerRadius = w * 0.40f;
        canvas.drawCircle(cx, cy, outerRadius, strokePaint);

        // Head.
        canvas.drawCircle(cx, cy - h * 0.14f, h * 0.135f, strokePaint);

        // Shoulders arc, clipped inside the ring below the head.
        RectF shoulderRect = new RectF(cx - w * 0.27f, cy + h * 0.02f, cx + w * 0.27f, cy + h * 0.50f);
        canvas.drawArc(shoulderRect, 195, 150, false, strokePaint);

        // Badge (bottom-right), punched transparent first so it reads as a separate
        // floating circle rather than overlapping the ring/shoulders.
        float badgeCx = cx + w * 0.29f;
        float badgeCy = cy + h * 0.27f;
        float badgeR = w * 0.185f;

        canvas.drawCircle(badgeCx, badgeCy, badgeR + strokePaint.getStrokeWidth() * 0.6f, clearPaint);
        canvas.drawCircle(badgeCx, badgeCy, badgeR, strokePaint);

        // Three dots inside the badge.
        float dotR = badgeR * 0.145f;
        float dotSpacing = badgeR * 0.55f;
        canvas.drawCircle(badgeCx - dotSpacing, badgeCy, dotR, dotPaint);
        canvas.drawCircle(badgeCx, badgeCy, dotR, dotPaint);
        canvas.drawCircle(badgeCx + dotSpacing, badgeCy, dotR, dotPaint);

        canvas.restoreToCount(layer);
    }

    @Override
    public void setAlpha(int alpha) {
        strokePaint.setAlpha(alpha);
        dotPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        strokePaint.setColorFilter(colorFilter);
        dotPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
