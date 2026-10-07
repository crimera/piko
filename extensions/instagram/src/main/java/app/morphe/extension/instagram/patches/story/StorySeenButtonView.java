/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.story;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import java.lang.reflect.Constructor;

import app.morphe.extension.instagram.constants.UI;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

final class StorySeenButtonView {
    private static final String BUTTON_TAG = "piko_mark_story_seen_button";
    private static final String[] ACTION_ID_NAMES = {
            "toolbar_like_container", "toolbar_reshare_button", "toolbar_menu_button"
    };

    private StorySeenButtonView() {
    }

    static StorySeenBindingRetry.FrameHost frameHost(View root) {
        return new ViewFrameHost(root);
    }

    static ViewGroup findActionRow(View root) {
        if (root == null) {
            return null;
        }
        View row = findView(root, "toolbar_buttons_container");
        return row instanceof ViewGroup ? (ViewGroup) row : null;
    }

    private static View findView(View root, String name) {
        int id = ResourceUtils.getIdentifier(ResourceType.ID, name);
        return id == 0 ? null : root.findViewById(id);
    }

    static void deactivate(View anchor) {
        setUnavailable(anchor, false);
    }

    static void setUnavailable(View toolbar, boolean hide) {
        if (!(toolbar instanceof ViewGroup)) {
            return;
        }
        ImageView button = findButton((ViewGroup) toolbar);
        if (button == null) {
            return;
        }
        button.setEnabled(false);
        button.setOnClickListener(null);
        if (hide) {
            button.setVisibility(View.GONE);
        }
    }

    static ImageView prepare(ViewGroup parent) {
        View anchor = findActionAnchor(parent);
        ImageView button = findButton(parent);
        if (anchor == null) {
            if (button != null) {
                button.setVisibility(View.VISIBLE);
            }
            return button;
        }
        if (button == null) {
            button = createButton(parent, anchor);
        } else {
            copyButtonGeometry(button, anchor);
            button.setLayoutParams(copyLayoutParams(anchor.getLayoutParams()));
            int anchorIndex = parent.indexOfChild(anchor);
            int buttonIndex = parent.indexOfChild(button);
            if (buttonIndex != anchorIndex - 1) {
                parent.removeView(button);
                parent.addView(button, parent.indexOfChild(anchor));
            }
        }
        button.setVisibility(View.VISIBLE);
        return button;
    }

    private static View findActionAnchor(ViewGroup parent) {
        for (String name : ACTION_ID_NAMES) {
            View candidate = findView(parent, name);
            if (candidate != null && candidate.getParent() == parent
                    && candidate.getVisibility() == View.VISIBLE) {
                return candidate;
            }
        }
        return null;
    }

    static boolean showState(ImageView button, StorySeenBridge.SeenState seenState) {
        Presentation presentation = presentationFor(seenState);
        button.setEnabled(presentation.enabled);
        button.setAlpha(presentation.alpha);
        button.setContentDescription(str(presentation.contentDescriptionName));
        if (button.getDrawable() == null) {
            UI.setThemedIcon(button, UI.DRAWABLE_EYE_ICON);
        }
        button.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
        return presentation.enabled;
    }

    private static Presentation presentationFor(StorySeenBridge.SeenState seenState) {
        switch (seenState) {
            case MARKED:
                return new Presentation(false, 0.50f, "piko_story_seen_sent");
            case PENDING:
                return new Presentation(false, 1.0f, "piko_story_seen_pending");
            case UNMARKED:
            default:
                return new Presentation(true, 1.0f, "piko_mark_story_seen");
        }
    }

    private static ImageView createButton(ViewGroup parent, View anchor) {
        ImageView button = new ImageView(parent.getContext());
        button.setTag(BUTTON_TAG);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        copyButtonGeometry(button, anchor);

        int insertIndex = parent.indexOfChild(anchor);
        ViewGroup.LayoutParams layoutParams = copyLayoutParams(anchor.getLayoutParams());
        parent.addView(button, insertIndex, layoutParams);
        new FirstLayoutDrawGate(button, parent, anchor).start();
        return button;
    }

    private static void copyButtonGeometry(ImageView button, View anchor) {
        button.setPadding(
                anchor.getPaddingLeft(),
                anchor.getPaddingTop(),
                anchor.getPaddingRight(),
                anchor.getPaddingBottom()
        );
        button.setMinimumWidth(anchor.getMinimumWidth());
        button.setMinimumHeight(anchor.getMinimumHeight());
    }

    private static ImageView findButton(ViewGroup parent) {
        for (int index = 0; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            if (child instanceof ImageView && BUTTON_TAG.equals(child.getTag())) {
                return (ImageView) child;
            }
        }
        return null;
    }

    private static ViewGroup.LayoutParams copyLayoutParams(ViewGroup.LayoutParams source) {
        if (source == null) {
            return new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        try {
            Constructor<?> constructor = source.getClass().getConstructor(ViewGroup.LayoutParams.class);
            return (ViewGroup.LayoutParams) constructor.newInstance(source);
        } catch (ReflectiveOperationException ignored) {
            if (source instanceof ViewGroup.MarginLayoutParams) {
                return new ViewGroup.MarginLayoutParams((ViewGroup.MarginLayoutParams) source);
            }
            return new ViewGroup.LayoutParams(source);
        }
    }

    private static final class Presentation {
        private final boolean enabled;
        private final float alpha;
        private final String contentDescriptionName;

        private Presentation(boolean enabled, float alpha, String contentDescriptionName) {
            this.enabled = enabled;
            this.alpha = alpha;
            this.contentDescriptionName = contentDescriptionName;
        }
    }

    private static final class ViewFrameHost implements
            StorySeenBindingRetry.FrameHost,
            ViewTreeObserver.OnPreDrawListener,
            View.OnAttachStateChangeListener {
        private final View root;
        private StorySeenBindingRetry.FrameCallback callback;
        private boolean observing;
        private boolean observingPreDraw;

        private ViewFrameHost(View root) {
            this.root = root;
        }

        @Override
        public boolean isAttached() {
            return root.isAttachedToWindow();
        }

        @Override
        public void addFrameCallback(StorySeenBindingRetry.FrameCallback value) {
            callback = value;
            observing = true;
            root.addOnAttachStateChangeListener(this);
            if (root.isAttachedToWindow()) {
                addPreDrawListener();
            }
        }

        @Override
        public void removeFrameCallback(StorySeenBindingRetry.FrameCallback value) {
            if (!observing || callback != value) {
                return;
            }
            observing = false;
            root.removeOnAttachStateChangeListener(this);
            removePreDrawListener();
            callback = null;
        }

        @Override
        public boolean onPreDraw() {
            StorySeenBindingRetry.FrameCallback current = callback;
            // Skip this frame after binding so the action row can lay out with the eye button.
            return current == null || !current.onFrame();
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            StorySeenBindingRetry.FrameCallback current = callback;
            if (current == null) {
                return;
            }
            try {
                addPreDrawListener();
                current.onFrame();
            } catch (Throwable ignored) {
                current.onFrame();
            }
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            StorySeenBindingRetry.FrameCallback current = callback;
            if (current != null) {
                current.onFrame();
            }
        }

        private void addPreDrawListener() {
            if (observingPreDraw) {
                return;
            }
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (!observer.isAlive()) {
                throw new IllegalStateException("Story root ViewTreeObserver is not alive");
            }
            observer.addOnPreDrawListener(this);
            observingPreDraw = true;
        }

        private void removePreDrawListener() {
            if (!observingPreDraw) {
                return;
            }
            observingPreDraw = false;
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnPreDrawListener(this);
            }
        }
    }

    private static final class FirstLayoutDrawGate implements
            ViewTreeObserver.OnPreDrawListener,
            View.OnAttachStateChangeListener {
        private final ImageView button;
        private final ViewGroup parent;
        private final View anchor;
        private boolean observing;
        private boolean deferred;

        private FirstLayoutDrawGate(ImageView button, ViewGroup parent, View anchor) {
            this.button = button;
            this.parent = parent;
            this.anchor = anchor;
        }

        private void start() {
            ViewTreeObserver observer = parent.getViewTreeObserver();
            if (!observer.isAlive()) {
                return;
            }
            observing = true;
            parent.addOnAttachStateChangeListener(this);
            observer.addOnPreDrawListener(this);
        }

        @Override
        public boolean onPreDraw() {
            boolean defer = button.isAttachedToWindow()
                    && !deferred
                    && (button.getWidth() == 0 || button.getHeight() == 0)
                    && anchor.getWidth() > 0
                    && anchor.getHeight() > 0;
            if (defer) {
                deferred = true;
                parent.requestLayout();
                return false;
            }
            stop();
            return true;
        }

        @Override
        public void onViewAttachedToWindow(View view) {
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            stop();
        }

        private void stop() {
            if (!observing) {
                return;
            }
            observing = false;
            parent.removeOnAttachStateChangeListener(this);
            ViewTreeObserver observer = parent.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnPreDrawListener(this);
            }
        }
    }
}
