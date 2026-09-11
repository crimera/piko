/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.settings.preference.widgets;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.animation.TimeInterpolator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.BaseAdapter;
import android.widget.ListView;

import app.morphe.extension.instagram.patches.navigation.NavigationBarPatch;
import app.morphe.extension.instagram.patches.navigation.NavigationBarPatch.Tab;

import java.util.HashMap;
import java.util.Map;

final class NavigationBarAdapter extends BaseAdapter {
    private static final TimeInterpolator MOVE_INTERPOLATOR =
            new PathInterpolator(0.77f, 0f, 0.175f, 1f);
    private static final TimeInterpolator SETTLE_INTERPOLATOR =
            new PathInterpolator(0.23f, 1f, 0.32f, 1f);

    private final Context context;
    private final NavigationBarEditorState state = NavigationBarEditorState.from(
            NavigationBarPatch.loadConfig()
    );
    private final DragSession dragSession = new DragSession();
    private final Runnable edgeScroll = this::scrollWhileDragging;
    private boolean edgeScrollPending;
    private float dragPointerY;
    private ListView listView;

    NavigationBarAdapter(Context context) {
        this.context = context;
    }

    void attachListView(ListView listView) {
        this.listView = listView;
        listView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                cancelDragImmediately();
            }
        });
    }

    @Override
    public int getCount() {
        return state.order().size();
    }

    @Override
    public Tab getItem(int position) {
        return state.order().get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).ordinal();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Tab tab = getItem(position);
        View row = createRow(tab);
        row.setTag(tab);
        row.setAlpha(tab == dragSession.tab ? 0f : 1f);
        return row;
    }

    private View createRow(Tab tab) {
        boolean checked = state.visible().contains(tab);
        return InstagramPreferenceStyle.createNavigationCheckRow(
                context,
                str(tab.labelName()),
                tab.iconName(),
                checked
        );
    }

    void toggle(int position) {
        if (position < 0 || position >= getCount()) return;
        Tab tab = getItem(position);
        setVisible(tab, !state.visible().contains(tab));
    }

    private void setVisible(Tab tab, boolean visible) {
        state.setVisible(tab, visible);
        notifyDataSetChanged();
    }

    boolean onListTouch(View ignored, MotionEvent event) {
        if (dragSession.settling) return true;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (dragSession.isActive()) return true;
                int position = listView.pointToPosition(
                        Math.round(event.getX()),
                        Math.round(event.getY())
                );
                View row = rowAt(position);
                if (row == null || !InstagramPreferenceStyle.isNavigationDragHandleHit(
                        row,
                        event.getRawX(),
                        event.getRawY()
                )) {
                    return false;
                }
                Tab tab = getItem(position);
                View overlayView = createOverlayRow(tab, row);
                dragSession.begin(
                        tab,
                        position,
                        event.getPointerId(0),
                        event.getY() - row.getTop(),
                        overlayView
                );
                row.setAlpha(0f);
                listView.requestDisallowInterceptTouchEvent(true);
                updateEdgeScroll(event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragSession.isActive()) return false;
                int pointerIndex = event.findPointerIndex(dragSession.pointerId);
                if (pointerIndex < 0) {
                    settleDrag();
                    return true;
                }
                float y = event.getY(pointerIndex);
                View overlay = dragSession.overlayView;
                overlay.setY(y - dragSession.touchOffsetY);
                movePlaceholderIfNeeded(
                        overlay.getY() + (overlay.getHeight() / 2f)
                );
                updateEdgeScroll(y);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                return dragSession.isActive();
            case MotionEvent.ACTION_POINTER_UP:
                if (!dragSession.isActive()) return false;
                if (event.getPointerId(event.getActionIndex()) == dragSession.pointerId) {
                    settleDrag();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragSession.isActive()) return false;
                settleDrag();
                return true;
            default:
                return dragSession.isActive();
        }
    }

    private void updateEdgeScroll(float pointerY) {
        dragPointerY = pointerY;
        if (edgeScrollPending) return;
        edgeScrollPending = true;
        listView.postDelayed(edgeScroll, 16L);
    }

    private void scrollWhileDragging() {
        edgeScrollPending = false;
        if (!dragSession.isActive() || dragSession.settling
                || !listView.isAttachedToWindow()) return;

        int edge = Math.min(InstagramPreferenceStyle.dp(context, 48), listView.getHeight() / 2);
        int direction = dragPointerY < edge ? -1
                : dragPointerY > listView.getHeight() - edge ? 1 : 0;
        if (direction == 0 || !listView.canScrollVertically(direction)) return;

        if (!dragSession.layoutPending) {
            listView.scrollListBy(direction * InstagramPreferenceStyle.dp(context, 8));
            View overlay = dragSession.overlayView;
            movePlaceholderIfNeeded(overlay.getY() + overlay.getHeight() / 2f);
        }
        updateEdgeScroll(dragPointerY);
    }

    private void stopEdgeScroll() {
        listView.removeCallbacks(edgeScroll);
        edgeScrollPending = false;
    }

    private View createOverlayRow(Tab tab, View sourceRow) {
        View overlay = createRow(tab);
        overlay.setTag(tab);
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        int width = sourceRow.getWidth();
        int height = sourceRow.getHeight();
        overlay.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        );
        overlay.layout(0, 0, width, height);
        overlay.setX(sourceRow.getLeft());
        overlay.setY(sourceRow.getTop());
        listView.getOverlay().add(overlay);
        InstagramPreferenceStyle.setNavigationRowDragging(overlay, true, true);
        return overlay;
    }

    private void movePlaceholderIfNeeded(float draggedCenterY) {
        if (dragSession.layoutPending) return;

        float[] centers = visibleRowCenters();
        int target = targetPosition(
                draggedCenterY,
                dragSession.position,
                listView.getFirstVisiblePosition(),
                centers
        );
        if (target < 0 || target >= getCount() || target == dragSession.position) return;

        Map<Tab, Float> previousTops = captureVisibleTops();
        if (!dragSession.moveTo(target, getCount())) return;
        state.move(dragSession.tab, target);
        animateNextLayout(previousTops);
        notifyDataSetChanged();
    }

    private float[] visibleRowCenters() {
        int childCount = listView.getChildCount();
        float[] centers = new float[childCount];
        for (int index = 0; index < childCount; index++) {
            View child = listView.getChildAt(index);
            centers[index] = child.getTop() + (child.getHeight() / 2f);
        }
        return centers;
    }

    private Map<Tab, Float> captureVisibleTops() {
        Map<Tab, Float> tops = new HashMap<>();
        for (int index = 0; index < listView.getChildCount(); index++) {
            View child = listView.getChildAt(index);
            Object tag = child.getTag();
            if (tag instanceof Tab) tops.put((Tab) tag, child.getY());
        }
        return tops;
    }

    private void animateNextLayout(Map<Tab, Float> previousTops) {
        ViewTreeObserver observer = listView.getViewTreeObserver();
        if (!observer.isAlive()) return;

        dragSession.layoutPending = true;
        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                ViewTreeObserver currentObserver = listView.getViewTreeObserver();
                if (currentObserver.isAlive()) currentObserver.removeOnPreDrawListener(this);
                dragSession.layoutPending = false;
                animateDisplacedRows(previousTops);
                if (dragSession.settling) settleOverlayToPlaceholder();
                return true;
            }
        });
    }

    private void animateDisplacedRows(Map<Tab, Float> previousTops) {
        for (int index = 0; index < listView.getChildCount(); index++) {
            View child = listView.getChildAt(index);
            Object tag = child.getTag();
            if (!(tag instanceof Tab) || tag == dragSession.tab) continue;

            Float previousTop = previousTops.get(tag);
            if (previousTop == null) continue;
            float translation = previousTop - child.getTop();
            if (Math.abs(translation) < 0.5f) continue;

            child.animate().cancel();
            child.setTranslationY(translation);
            child.animate()
                    .translationY(0f)
                    .setDuration(InstagramPreferenceStyle.NAVIGATION_DRAG_DURATION_MS)
                    .setInterpolator(MOVE_INTERPOLATOR)
                    .start();
        }
    }

    private void settleDrag() {
        if (!dragSession.isActive() || dragSession.settling) return;

        stopEdgeScroll();
        dragSession.settling = true;
        if (dragSession.layoutPending) return;
        settleOverlayToPlaceholder();
    }

    private void settleOverlayToPlaceholder() {
        View overlay = dragSession.overlayView;
        View finalRow = rowAt(dragSession.position);
        if (finalRow == null) {
            completeDrag(overlay);
            return;
        }

        overlay.animate().cancel();
        overlay.animate()
                .y(finalRow.getTop())
                .translationZ(0f)
                .setDuration(InstagramPreferenceStyle.NAVIGATION_DRAG_DURATION_MS)
                .setInterpolator(SETTLE_INTERPOLATOR)
                .withEndAction(() -> completeDrag(overlay))
                .start();
    }

    private void cancelDragImmediately() {
        if (!dragSession.isActive()) return;
        completeDrag(dragSession.overlayView);
    }

    private void completeDrag(View overlay) {
        if (overlay == null || overlay != dragSession.overlayView) return;

        stopEdgeScroll();
        overlay.animate().withEndAction(null);
        overlay.animate().cancel();
        InstagramPreferenceStyle.setNavigationRowDragging(overlay, false, false);
        listView.getOverlay().remove(overlay);
        dragSession.finish();
        notifyDataSetChanged();
        listView.requestDisallowInterceptTouchEvent(false);
    }

    void reset() {
        cancelDragImmediately();
        state.reset();
        notifyDataSetChanged();
    }

    boolean needsSettingsLockoutWarning() {
        try {
            return !NavigationSettingsAccessPolicy.hasDirectAccess(state.visible());
        } catch (Throwable ignored) {
            return true;
        }
    }

    boolean save() {
        return NavigationBarPatch.saveConfig(
                state.order(),
                state.visible()
        );
    }

    private View rowAt(int position) {
        int childIndex = position - listView.getFirstVisiblePosition();
        return childIndex >= 0 && childIndex < listView.getChildCount()
                ? listView.getChildAt(childIndex)
                : null;
    }

    private static int targetPosition(
            float draggedCenterY,
            int currentPosition,
            int firstVisiblePosition,
            float[] rowCenters
    ) {
        if (rowCenters.length == 0) return currentPosition;

        int currentVisibleIndex = currentPosition - firstVisiblePosition;
        if (currentVisibleIndex < 0) {
            int targetPosition = currentPosition;
            for (int index = 0; index < rowCenters.length; index++) {
                if (draggedCenterY <= rowCenters[index]) break;
                targetPosition = firstVisiblePosition + index;
            }
            return targetPosition;
        }
        if (currentVisibleIndex >= rowCenters.length) {
            int targetPosition = currentPosition;
            for (int index = rowCenters.length - 1; index >= 0; index--) {
                if (draggedCenterY >= rowCenters[index]) break;
                targetPosition = firstVisiblePosition + index;
            }
            return targetPosition;
        }

        int targetVisibleIndex = currentVisibleIndex;
        while (targetVisibleIndex < rowCenters.length - 1
                && draggedCenterY > rowCenters[targetVisibleIndex + 1]) {
            targetVisibleIndex++;
        }
        while (targetVisibleIndex > 0
                && draggedCenterY < rowCenters[targetVisibleIndex - 1]) {
            targetVisibleIndex--;
        }
        return firstVisiblePosition + targetVisibleIndex;
    }

    private static final class DragSession {
        private Tab tab;
        private int position = ListView.INVALID_POSITION;
        private int pointerId = MotionEvent.INVALID_POINTER_ID;
        private float touchOffsetY;
        private View overlayView;
        private boolean layoutPending;
        private boolean settling;

        void begin(
                Tab tab,
                int position,
                int pointerId,
                float touchOffsetY,
                View overlayView
        ) {
            this.tab = tab;
            this.position = position;
            this.pointerId = pointerId;
            this.touchOffsetY = touchOffsetY;
            this.overlayView = overlayView;
            layoutPending = false;
            settling = false;
        }

        boolean isActive() {
            return tab != null
                    && position != ListView.INVALID_POSITION
                    && overlayView != null;
        }

        boolean moveTo(int target, int count) {
            if (!isActive()
                    || settling
                    || target < 0
                    || target >= count
                    || target == position) {
                return false;
            }
            position = target;
            return true;
        }

        void finish() {
            tab = null;
            position = ListView.INVALID_POSITION;
            pointerId = MotionEvent.INVALID_POINTER_ID;
            touchOffsetY = 0f;
            overlayView = null;
            layoutPending = false;
            settling = false;
        }
    }
}
