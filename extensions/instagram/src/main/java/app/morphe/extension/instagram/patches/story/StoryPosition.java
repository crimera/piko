/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.story;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

/**
 * Each story page starts with an empty spacer that keeps the story below the status bar.
 * In the modal story viewer the whole story is already below the status bar, so the spacer
 * pushes the media down a second time, under the reply bar. The spacer is shortened by the
 * offset the story already has in its window.
 */
public class StoryPosition {
    private static final String CONTENT_LAYOUT_ID = "reel_viewer_content_layout";

    // Story pages sit at root > view pager > page frame > content layout.
    private static final int MAX_SEARCH_DEPTH = 4;

    // Spacer height set by Instagram (index 0) and the height applied here (index 1).
    private static final Map<View, int[]> spacerHeights = new WeakHashMap<>();

    public static void onViewCreated(View root) {
        try {
            if (!Pref.fixStoryPosition()) return;
            int contentLayoutId = ResourceUtils.getIdentifier(ResourceType.ID, CONTENT_LAYOUT_ID);
            if (contentLayoutId == 0) return;

            // Story pages are added and recycled later, and the status bar inset can arrive late,
            // so check again on every layout.
            root.addOnLayoutChangeListener((view, left, top, right, bottom,
                                            oldLeft, oldTop, oldRight, oldBottom) ->
                    adjustSpacers(view, contentLayoutId));
        } catch (Exception e) {
            Logger.printException(() -> "StoryPosition onViewCreated failure", e);
        }
    }

    private static void adjustSpacers(View root, int contentLayoutId) {
        try {
            if (!(root instanceof ViewGroup)) return;
            List<View> changed = new ArrayList<>();
            adjustSpacersIn((ViewGroup) root, contentLayoutId, topInWindow(root), 0, changed);
            if (!changed.isEmpty()) relayoutBeforeDraw(root, changed);
        } catch (Exception e) {
            Logger.printException(() -> "StoryPosition adjustSpacers failure", e);
        }
    }

    // This runs during layout, and the coming draw would still show the old spacer height for one frame.
    // Cancel that draw and lay the spacers out again. The request has to start at each spacer,
    // because Android only measures again the views on the path of a layout request.
    private static void relayoutBeforeDraw(View root, List<View> spacers) {
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                ViewTreeObserver observer = root.getViewTreeObserver();
                if (observer.isAlive()) observer.removeOnPreDrawListener(this);
                for (View spacer : spacers) spacer.requestLayout();
                return false;
            }
        });
    }

    private static void adjustSpacersIn(ViewGroup group, int contentLayoutId, int offset, int depth, List<View> changed) {
        if (depth > MAX_SEARCH_DEPTH) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!(child instanceof ViewGroup)) continue;
            if (child.getId() == contentLayoutId) {
                adjustSpacer((ViewGroup) child, offset, changed);
            } else {
                adjustSpacersIn((ViewGroup) child, contentLayoutId, offset, depth + 1, changed);
            }
        }
    }

    private static void adjustSpacer(ViewGroup contentLayout, int offset, List<View> changed) {
        if (contentLayout.getChildCount() < 2) return;
        View spacer = contentLayout.getChildAt(0);
        if (spacer instanceof ViewGroup || spacer.getId() != View.NO_ID) return;

        ViewGroup.LayoutParams params = spacer.getLayoutParams();
        if (params == null) return;

        int[] heights = spacerHeights.get(spacer);
        if (heights == null || params.height != heights[1]) {
            // New spacer, or Instagram set the height again.
            int original = params.height > 0 ? params.height : spacer.getHeight();
            if (original <= 0) return;
            heights = new int[]{original, original};
            spacerHeights.put(spacer, heights);
        }

        int target = Math.max(0, heights[0] - offset);
        if (params.height == target) return;

        // Only set the field here: requestLayout() during layout forces an extra pass and logs a warning.
        heights[1] = target;
        params.height = target;
        changed.add(spacer);
    }

    // Uses layout positions only, so dragging a story down to close it does not count as an offset.
    private static int topInWindow(View view) {
        int top = 0;
        while (view != null) {
            top += view.getTop();
            ViewParent parent = view.getParent();
            view = parent instanceof View ? (View) parent : null;
        }
        return top;
    }
}
