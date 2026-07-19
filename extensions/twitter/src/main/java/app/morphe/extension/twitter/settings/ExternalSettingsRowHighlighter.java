/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.lang.reflect.Method;
import java.util.Locale;

import app.morphe.extension.shared.Logger;

final class ExternalSettingsRowHighlighter {
    private static final int MAX_TARGET_SEARCH_ATTEMPTS = 8;
    private static final SettingsSearchOwnerTracker<PendingHighlightTarget> pendingTargetOwner =
            new SettingsSearchOwnerTracker<>();
    private static Application.ActivityLifecycleCallbacks highlightCallbacks;
    private static Application pendingApplication;

    private ExternalSettingsRowHighlighter() {
    }

    static void schedule(Activity sourceActivity, String activityClassName, String targetTitle) {
        if (sourceActivity == null || TextUtils.isEmpty(activityClassName) || TextUtils.isEmpty(targetTitle)) {
            return;
        }

        Application application = sourceActivity.getApplication();
        if (application == null) {
            return;
        }

        PendingHighlightTarget previousTarget = pendingTargetOwner.current();
        if (previousTarget != null) {
            clear(previousTarget, null);
        }

        PendingHighlightTarget target = new PendingHighlightTarget(activityClassName, targetTitle);
        pendingApplication = application;
        pendingTargetOwner.replaceWith(target);
        highlightCallbacks = new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
            }

            @Override
            public void onActivityResumed(Activity activity) {
                if (activity != null
                        && pendingTargetOwner.current() == target
                        && TextUtils.equals(target.activityClassName, activity.getClass().getName())) {
                    highlightPendingTarget(activity, target, 0);
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        };
        application.registerActivityLifecycleCallbacks(highlightCallbacks);
        View decorView = sourceActivity.getWindow() == null ? null : sourceActivity.getWindow().getDecorView();
        if (decorView != null) {
            decorView.postDelayed(() -> clear(target, null), 5000);
        }
    }

    private static void highlightPendingTarget(Activity activity, PendingHighlightTarget target, int attempts) {
        if (activity == null || pendingTargetOwner.current() != target) {
            return;
        }

        Window window = activity.getWindow();
        View decorView = window == null ? null : window.getDecorView();
        TextView targetView = findTextViewWithText(decorView, target.targetTitle);

        if (targetView == null) {
            View scrollableView = findScrollableView(decorView);
            MissingTargetAction action = nextMissingTargetAction(
                    attempts,
                    scrollableView != null,
                    target.initialPositionReset
            );
            if (action == MissingTargetAction.GIVE_UP || decorView == null) {
                clear(target, activity);
                return;
            }

            if (action == MissingTargetAction.RESET_TO_TOP_AND_RETRY) {
                resetToTop(scrollableView);
                target.initialPositionReset = true;
            } else if (action == MissingTargetAction.SCROLL_AND_RETRY) {
                scrollForward(scrollableView);
            }
            int nextAttempts = nextAttemptCount(attempts, action);
            decorView.postDelayed(() -> highlightPendingTarget(activity, target, nextAttempts), 150);
            return;
        }

        View targetRow = findSearchTargetRow(targetView);
        SettingsSearchNavigator.highlightRow(
                targetRow,
                () -> isCurrentSearchTarget(targetView, targetRow, target.targetTitle)
        );
        clear(target, activity);
    }

    @Nullable
    private static TextView findTextViewWithText(View view, String targetText) {
        if (view == null || TextUtils.isEmpty(targetText)) {
            return null;
        }

        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            CharSequence text = textView.getText();
            String normalizedText = normalizeText(text == null ? "" : text.toString());
            String normalizedTarget = normalizeText(targetText);
            if (TextUtils.equals(normalizedText, normalizedTarget)) {
                return textView;
            }
        }

        if (!(view instanceof ViewGroup)) {
            return null;
        }

        ViewGroup viewGroup = (ViewGroup) view;
        for (int index = 0; index < viewGroup.getChildCount(); index++) {
            TextView match = findTextViewWithText(viewGroup.getChildAt(index), targetText);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    @Nullable
    private static View findScrollableView(View view) {
        if (view == null) {
            return null;
        }

        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int index = 0; index < viewGroup.getChildCount(); index++) {
                View scrollableChild = findScrollableView(viewGroup.getChildAt(index));
                if (scrollableChild != null) {
                    return scrollableChild;
                }
            }
        }

        return view.canScrollVertically(-1) || view.canScrollVertically(1) ? view : null;
    }

    private static void resetToTop(View scrollableView) {
        if (scrollableView == null) {
            return;
        }

        if (scrollableView instanceof AbsListView) {
            ((AbsListView) scrollableView).setSelection(0);
            return;
        }

        try {
            Method scrollToPosition = scrollableView.getClass().getMethod("scrollToPosition", int.class);
            scrollToPosition.invoke(scrollableView, 0);
            return;
        } catch (ReflectiveOperationException ignored) {
        }
        scrollableView.scrollTo(0, 0);
    }

    private static void scrollForward(View scrollableView) {
        if (scrollableView == null) {
            return;
        }

        int distance = Math.max(scrollableView.getHeight() / 2, dp(scrollableView.getContext(), 120));
        if (scrollableView instanceof AbsListView) {
            ((AbsListView) scrollableView).smoothScrollBy(distance, 150);
        } else {
            scrollableView.scrollBy(0, distance);
        }
    }

    static MissingTargetAction nextMissingTargetAction(
            int attempts,
            boolean hasScrollableView,
            boolean initialPositionReset
    ) {
        if (attempts >= MAX_TARGET_SEARCH_ATTEMPTS) {
            return MissingTargetAction.GIVE_UP;
        }
        if (!hasScrollableView) {
            return MissingTargetAction.RETRY;
        }
        return initialPositionReset
                ? MissingTargetAction.SCROLL_AND_RETRY
                : MissingTargetAction.RESET_TO_TOP_AND_RETRY;
    }

    static int nextAttemptCount(int attempts, MissingTargetAction action) {
        return action == MissingTargetAction.SCROLL_AND_RETRY ? attempts + 1 : attempts;
    }

    private static View findSearchTargetRow(TextView textView) {
        View candidate = textView;
        View current = textView;
        int rootWidth = textView.getRootView() == null ? 0 : textView.getRootView().getWidth();
        int minimumRowHeight = dp(textView.getContext(), 40);
        int maximumRowHeight = dp(textView.getContext(), 160);

        for (int depth = 0; depth < 7; depth++) {
            ViewParent parent = current.getParent();
            if (!(parent instanceof View)) {
                break;
            }

            View parentView = (View) parent;
            int height = parentView.getHeight();
            int width = parentView.getWidth();
            if (height >= minimumRowHeight
                    && height <= maximumRowHeight
                    && (rootWidth <= 0 || width >= rootWidth / 2)) {
                candidate = parentView;
            }
            current = parentView;
        }
        return candidate;
    }

    private static boolean isCurrentSearchTarget(TextView targetView, View targetRow, String targetTitle) {
        if (!targetView.isAttachedToWindow() || !targetRow.isAttachedToWindow()) {
            return false;
        }

        CharSequence text = targetView.getText();
        return TextUtils.equals(
                normalizeText(text == null ? "" : text.toString()),
                normalizeText(targetTitle)
        ) && findSearchTargetRow(targetView) == targetRow;
    }

    private static String normalizeText(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static void clear(PendingHighlightTarget target, Activity activity) {
        pendingTargetOwner.clearIfOwnedBy(target, () -> {
            Application application = activity == null ? null : activity.getApplication();
            if (application == null) {
                application = pendingApplication;
            }
            pendingApplication = null;
            unregisterCallback(application);
        });
    }

    private static void unregisterCallback(Application application) {
        if (application == null || highlightCallbacks == null) {
            return;
        }

        try {
            application.unregisterActivityLifecycleCallbacks(highlightCallbacks);
        } catch (Throwable throwable) {
            Logger.printException(() -> "external settings search highlight callback unregistration failed", throwable);
        }
        highlightCallbacks = null;
    }

    private static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class PendingHighlightTarget {
        final String activityClassName;
        final String targetTitle;
        boolean initialPositionReset;

        PendingHighlightTarget(String activityClassName, String targetTitle) {
            this.activityClassName = activityClassName;
            this.targetTitle = targetTitle;
        }
    }

    enum MissingTargetAction {
        RETRY,
        RESET_TO_TOP_AND_RETRY,
        SCROLL_AND_RETRY,
        GIVE_UP
    }
}
