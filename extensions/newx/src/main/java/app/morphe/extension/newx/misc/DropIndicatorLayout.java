package app.morphe.extension.newx.misc;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.widget.LinearLayout;

import app.morphe.extension.newx.ui.Theme;

/**
 * Vertical row container that paints the drag-and-drop indicator used by the NewX list editors.
 *
 * <p>The indicator is either an insertion line between two rows or an outline around the row a
 * drop would replace.
 */
final class DropIndicatorLayout extends LinearLayout {
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float horizontalInset;
    private final float strokeWidth;
    private float indicatorTop = -1f;
    private float indicatorBottom = -1f;

    DropIndicatorLayout(Context context) {
        super(context);
        horizontalInset = Theme.dpToPx(context, 12f);
        strokeWidth = Theme.dpToPx(context, 3f);
        indicatorPaint.setColor(Theme.primaryAccent(context));
        indicatorPaint.setStrokeWidth(strokeWidth);
    }

    void showInsertionLine(float y) {
        indicatorTop = y;
        indicatorBottom = y;
        invalidate();
    }

    void showDropTarget(View target) {
        indicatorTop = target.getTop();
        indicatorBottom = target.getBottom();
        invalidate();
    }

    void clearDropIndicator() {
        if (indicatorTop < 0f) return;
        indicatorTop = -1f;
        indicatorBottom = -1f;
        invalidate();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (indicatorTop < 0f) return;

        float left = horizontalInset;
        float right = getWidth() - horizontalInset;
        float radius = strokeWidth;
        if (indicatorTop == indicatorBottom) {
            indicatorPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(
                    left,
                    indicatorTop - strokeWidth / 2f,
                    right,
                    indicatorTop + strokeWidth / 2f,
                    radius,
                    radius,
                    indicatorPaint
            );
            return;
        }

        indicatorPaint.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(
                left,
                indicatorTop + strokeWidth / 2f,
                right,
                indicatorBottom - strokeWidth / 2f,
                Theme.dpToPx(getContext(), 8f),
                Theme.dpToPx(getContext(), 8f),
                indicatorPaint
        );
    }
}
