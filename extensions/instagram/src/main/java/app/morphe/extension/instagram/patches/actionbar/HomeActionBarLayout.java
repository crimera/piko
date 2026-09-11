/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.actionbar;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import app.morphe.extension.instagram.patches.navigation.NavigationBarPatch.NotificationsVisibility;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

final class HomeActionBarLayout {
    private static final String TRAILING_ACTION_CONTAINER_ID =
            "action_bar_buttons_container_right";
    private static final Map<ViewGroup, Monitor> MONITORS = new WeakHashMap<>();

    private HomeActionBarLayout() {
    }

    static void observe(
            ViewGroup actionBar,
            Supplier<NotificationsVisibility> stateProvider
    ) {
        if (actionBar == null || stateProvider == null) return;
        actionBar.post(() -> {
            try {
                Monitor monitor;
                synchronized (MONITORS) {
                    monitor = MONITORS.get(actionBar);
                    if (monitor == null) {
                        monitor = new Monitor(actionBar);
                        MONITORS.put(actionBar, monitor);
                    }
                    monitor.stateProvider = stateProvider;
                }
                monitor.start();
            } catch (Exception exception) {
                Logger.printException(() -> "Failed observeTrailingActionContainer: ", exception);
            }
        });
    }

    private static final class Monitor
            implements ViewTreeObserver.OnGlobalLayoutListener,
            View.OnAttachStateChangeListener {
        private final WeakReference<ViewGroup> actionBar;
        private Supplier<NotificationsVisibility> stateProvider;
        private WeakReference<ViewGroup> collapsedContainer;
        private int originalVisibility = -1;
        private ViewTreeObserver observer;
        private boolean attachListenerRegistered;
        private boolean evaluating;

        private Monitor(ViewGroup actionBar) {
            this.actionBar = new WeakReference<>(actionBar);
        }

        private void start() {
            ViewGroup bar = actionBar.get();
            if (bar == null) return;
            if (!attachListenerRegistered) {
                bar.addOnAttachStateChangeListener(this);
                attachListenerRegistered = true;
            }
            if (bar.isAttachedToWindow()) attach(bar);
        }

        private void attach(ViewGroup bar) {
            if (observer == null || !observer.isAlive()) {
                observer = bar.getViewTreeObserver();
                if (observer.isAlive()) observer.addOnGlobalLayoutListener(this);
            }
            evaluate();
        }

        private void detach() {
            if (observer != null && observer.isAlive()) {
                observer.removeOnGlobalLayoutListener(this);
            }
            observer = null;
            restore();
        }

        @Override
        public void onGlobalLayout() {
            evaluate();
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            if (view instanceof ViewGroup) attach((ViewGroup) view);
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            detach();
        }

        private void evaluate() {
            if (evaluating) return;
            evaluating = true;
            try {
                ViewGroup bar = actionBar.get();
                if (bar == null || stateProvider == null) {
                    restore();
                    return;
                }
                int id = ResourceUtils.getIdentifier(
                        ResourceType.ID,
                        TRAILING_ACTION_CONTAINER_ID
                );
                View view = id == 0 ? null : bar.findViewById(id);
                if (!(view instanceof ViewGroup)) {
                    restore();
                    return;
                }

                ViewGroup container = (ViewGroup) view;
                ViewGroup collapsed = collapsed();
                if (collapsed != null && collapsed != container) restore();

                NotificationsVisibility state = stateProvider.get();
                if (state != NotificationsVisibility.VISIBLE
                        || hasVisibleDirectChild(container)) {
                    restore();
                } else if (collapsed() != container
                        && container.getVisibility() != View.GONE) {
                    collapsedContainer = new WeakReference<>(container);
                    originalVisibility = container.getVisibility();
                    container.setVisibility(View.GONE);
                }
            } catch (Exception exception) {
                restore();
                Logger.printException(
                        () -> "Failed updateTrailingActionContainer: ",
                        exception
                );
            } finally {
                evaluating = false;
            }
        }

        private static boolean hasVisibleDirectChild(ViewGroup container) {
            for (int index = 0; index < container.getChildCount(); index++) {
                View child = container.getChildAt(index);
                if (child != null && child.getVisibility() == View.VISIBLE) return true;
            }
            return false;
        }

        private ViewGroup collapsed() {
            return collapsedContainer == null ? null : collapsedContainer.get();
        }

        private void restore() {
            ViewGroup container = collapsed();
            int visibility = originalVisibility;
            collapsedContainer = null;
            originalVisibility = -1;
            if (container != null && visibility >= 0
                    && container.getVisibility() != visibility) {
                container.setVisibility(visibility);
            }
        }
    }
}
