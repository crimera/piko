package app.morphe.extension.newx.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/**
 * Theme-aware indeterminate loading indicator for NewX surfaces.
 *
 * <p>The arc is drawn directly so its color, stroke width, and rounded caps remain independent of
 * the host app's opaque widget style.</p>
 */
public final class LoadingIndicatorView extends View {
    private static final float DEFAULT_SIZE_DP = 48f;
    private static final float STROKE_WIDTH_DP = 6f;
    private static final float SWEEP_DEGREES = 112f;
    private static final long ROTATION_DURATION_MS = 1000L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();
    private long animationStartMs;

    public LoadingIndicatorView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        refreshTheme();
    }

    /** Refreshes the accent after the host theme or accent changes. */
    public void refreshTheme() {
        paint.setColor(Theme.primaryAccent(getContext()));
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animationStartMs = SystemClock.uptimeMillis();
        refreshTheme();
        postInvalidateOnAnimation();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (changedView == this && visibility == VISIBLE) {
            animationStartMs = SystemClock.uptimeMillis();
            refreshTheme();
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredSize = Theme.dpToPx(getContext(), DEFAULT_SIZE_DP);
        setMeasuredDimension(
                resolveSize(desiredSize, widthMeasureSpec),
                resolveSize(desiredSize, heightMeasureSpec)
        );
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float strokeWidth = Theme.dpToPx(getContext(), STROKE_WIDTH_DP);
        float inset = strokeWidth / 2f + Theme.dpToPx(getContext(), 2f);
        float diameter = Math.min(getWidth(), getHeight()) - inset * 2f;
        if (diameter <= 0f) return;

        float left = (getWidth() - diameter) / 2f;
        float top = (getHeight() - diameter) / 2f;
        arcBounds.set(left, top, left + diameter, top + diameter);

        long elapsed = Math.max(0L, SystemClock.uptimeMillis() - animationStartMs);
        float rotation = (elapsed % ROTATION_DURATION_MS) * 360f / ROTATION_DURATION_MS;
        paint.setStrokeWidth(strokeWidth);
        canvas.drawArc(arcBounds, rotation - 90f, SWEEP_DEGREES, false, paint);

        if (isShown()) postInvalidateOnAnimation();
    }
}
