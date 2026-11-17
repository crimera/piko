package app.morphe.extension.newx.misc;

import android.graphics.Point;
import android.view.View;

/**
 * Drag shadow that keeps the finger anchored at the point where the drag started instead of the
 * view center, so long rows do not jump under the touch point.
 */
final class RowDragShadow extends View.DragShadowBuilder {
    private final int touchPointX;
    private final int touchPointY;

    RowDragShadow(View view, int touchPointX, int touchPointY) {
        super(view);
        this.touchPointX = touchPointX;
        this.touchPointY = touchPointY;
    }

    @Override
    public void onProvideShadowMetrics(Point shadowSize, Point shadowTouchPoint) {
        super.onProvideShadowMetrics(shadowSize, shadowTouchPoint);
        shadowTouchPoint.x = Math.max(0, Math.min(touchPointX, shadowSize.x));
        shadowTouchPoint.y = Math.max(0, Math.min(touchPointY, shadowSize.y));
    }
}
