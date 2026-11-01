/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.MotionEvent;
import android.view.View;

/**
 * A disc with the hue changing around it and the color turning white towards the center, dimmed by the brightness.
 * Touching it picks the hue and the saturation, which are reported to the listener.
 */
final class ColorWheelView extends View {

    interface Listener {
        void onPicked(float hue, float saturation);
    }

    private static final int[] HUES = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000,
    };

    private final Paint huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private final int thumbRadius;
    private float hue;
    private float saturation;
    private float brightness = 1f;
    private float centerX;
    private float centerY;
    private float radius;
    private Listener listener;

    ColorWheelView(Context context) {
        super(context);
        size = InstagramPreferenceStyle.dp(context, 240);
        thumbRadius = InstagramPreferenceStyle.dp(context, 12);
        thumbPaint.setStyle(Paint.Style.STROKE);
        dimPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
        // A touch cannot be done without one, so the hex field next to it is what is offered to a screen reader.
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    void setColor(float hue, float saturation, float brightness) {
        this.hue = hue;
        this.saturation = saturation;
        this.brightness = brightness;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
                ? size : Math.min(MeasureSpec.getSize(widthSpec), size);
        setMeasuredDimension(width, width);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        centerX = width / 2f;
        centerY = height / 2f;
        radius = Math.min(width, height) / 2f - thumbRadius;
        huePaint.setShader(new SweepGradient(centerX, centerY, HUES, null));
        whitePaint.setShader(new RadialGradient(centerX, centerY, Math.max(radius, 1f),
                0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // The dimming is drawn over the disc only, so the smoothed rim does not keep its hue at no brightness.
        int layer = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.drawCircle(centerX, centerY, radius, huePaint);
        canvas.drawCircle(centerX, centerY, radius, whitePaint);
        dimPaint.setColor(Color.argb(Math.round((1f - brightness) * 255), 0, 0, 0));
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);
        canvas.restoreToCount(layer);

        double angle = Math.toRadians(hue);
        float thumbX = centerX + (float) Math.cos(angle) * saturation * radius;
        float thumbY = centerY + (float) Math.sin(angle) * saturation * radius;
        thumbPaint.setStrokeWidth(InstagramPreferenceStyle.dp(getContext(), 4));
        thumbPaint.setColor(0x66000000);
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint);
        thumbPaint.setStrokeWidth(InstagramPreferenceStyle.dp(getContext(), 2));
        thumbPaint.setColor(Color.WHITE);
        canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // The dialog scrolls, and a drag on the wheel must not scroll it.
                getParent().requestDisallowInterceptTouchEvent(true);
                pick(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                pick(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void pick(float touchX, float touchY) {
        float deltaX = touchX - centerX;
        float deltaY = touchY - centerY;
        hue = (float) Math.toDegrees(Math.atan2(deltaY, deltaX));
        if (hue < 0f) hue += 360f;
        saturation = Math.min(1f, (float) Math.hypot(deltaX, deltaY) / Math.max(radius, 1f));
        invalidate();
        if (listener != null) listener.onPicked(hue, saturation);
    }
}
