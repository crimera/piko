/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.shared.ui;

import android.util.SparseBooleanArray;
import android.view.MotionEvent;
import android.view.animation.AnimationUtils;
import android.widget.ListView;

/** Owns drag-range selection and edge scrolling for a ListView. */
public final class ListViewDragSelectionController {
    private final ListView list;
    private final Runnable onSelectionChanged;
    private final Runnable autoScroll = this::performAutoScroll;

    private boolean[] baseChecked;
    private int anchorPosition = ListView.INVALID_POSITION;
    private int currentPosition = ListView.INVALID_POSITION;
    private int scrollDirection;
    private long scrollFrameTime;
    private float dragX;
    private float dragY;

    public ListViewDragSelectionController(ListView list, Runnable onSelectionChanged) {
        this.list = list;
        this.onSelectionChanged = onSelectionChanged;
    }

    public void begin(int position) {
        baseChecked = new boolean[list.getCount()];
        SparseBooleanArray checked = list.getCheckedItemPositions();
        for (int index = 0; index < baseChecked.length; index++) {
            baseChecked[index] = checked.get(index);
        }
        anchorPosition = position;
        currentPosition = position;
        applySelection(position);
    }

    public boolean handleTouch(MotionEvent event) {
        if (anchorPosition == ListView.INVALID_POSITION) return false;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                dragX = event.getX();
                dragY = event.getY();
                extendSelectionAt(dragX, dragY);
                updateAutoScroll(dragY);
                return true;
            case MotionEvent.ACTION_UP:
                stopAutoScroll();
                end();
                return true;
            case MotionEvent.ACTION_CANCEL:
                stopAutoScroll();
                restoreBaseSelection();
                end();
                onSelectionChanged.run();
                return true;
            default:
                return true;
        }
    }

    public void stop() {
        stopAutoScroll();
        end();
    }

    void performAutoScroll() {
        if (scrollDirection == 0 || anchorPosition == ListView.INVALID_POSITION) return;
        if (!list.canScrollList(scrollDirection)) return;
        long now = AnimationUtils.currentAnimationTimeMillis();
        long elapsed = Math.max(1, now - scrollFrameTime);
        scrollFrameTime = now;
        extendSelectionAt(dragX, dragY);
        list.scrollListBy(scrollDirection * scrollDistance(Dim.dp12, elapsed));
        if (list.canScrollList(scrollDirection)) list.postOnAnimation(autoScroll);
    }

    static boolean shouldCheckDuringDrag(
            boolean initiallyChecked, int position, int anchor, int current) {
        int first = Math.min(anchor, current);
        int last = Math.max(anchor, current);
        return initiallyChecked || (position >= first && position <= last);
    }

    static int scrollDistance(int distancePerFrame, long elapsedMillis) {
        return Math.max(1, Math.round(distancePerFrame * elapsedMillis / 16f));
    }

    private void extendSelectionAt(float x, float y) {
        int position = list.pointToPosition(
                Math.round(x),
                Math.max(0, Math.min(list.getHeight() - 1, Math.round(y)))
        );
        if (position == ListView.INVALID_POSITION) {
            position = y < list.getHeight() / 2f
                    ? list.getFirstVisiblePosition()
                    : list.getLastVisiblePosition();
        }
        if (position == currentPosition || position == ListView.INVALID_POSITION) return;
        applySelection(position);
    }

    private void applySelection(int position) {
        int firstChanged = Math.min(currentPosition, position);
        int lastChanged = Math.max(currentPosition, position);
        boolean changed = false;
        for (int index = firstChanged; index <= lastChanged; index++) {
            boolean shouldCheck = shouldCheckDuringDrag(
                    baseChecked[index], index, anchorPosition, position);
            if (list.isItemChecked(index) != shouldCheck) {
                list.setItemChecked(index, shouldCheck);
                changed = true;
            }
        }
        currentPosition = position;
        if (changed) onSelectionChanged.run();
    }

    private void restoreBaseSelection() {
        if (baseChecked == null) return;
        for (int index = 0; index < baseChecked.length; index++) {
            if (list.isItemChecked(index) != baseChecked[index]) {
                list.setItemChecked(index, baseChecked[index]);
            }
        }
    }

    private void updateAutoScroll(float y) {
        int direction = 0;
        if (y < Dim.dp48) {
            direction = -1;
        } else if (y > list.getHeight() - Dim.dp48) {
            direction = 1;
        }
        if (scrollDirection == direction) return;

        list.removeCallbacks(autoScroll);
        scrollDirection = direction;
        if (direction != 0) {
            scrollFrameTime = AnimationUtils.currentAnimationTimeMillis();
            list.postOnAnimation(autoScroll);
        }
    }

    private void stopAutoScroll() {
        scrollDirection = 0;
        list.removeCallbacks(autoScroll);
    }

    private void end() {
        baseChecked = null;
        anchorPosition = ListView.INVALID_POSITION;
        currentPosition = ListView.INVALID_POSITION;
    }
}
