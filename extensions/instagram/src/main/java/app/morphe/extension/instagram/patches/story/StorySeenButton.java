/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.story;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Activity;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.ResourceType;
import app.morphe.extension.shared.ResourceUtils;
import app.morphe.extension.shared.Utils;

public final class StorySeenButton {
    private static final String HEADER_MENU_ID_NAME = "header_menu_button";
    private static final int MAX_DEFERRED_BINDING_ATTEMPTS = 8;
    private static final long DEFERRED_BINDING_TIMEOUT_MILLIS = 250L;
    private static final AtomicLong CAPTURE_GENERATION = new AtomicLong();
    private static Object queuedItem;
    private static Object boundItem;
    private static WeakReference<View> queuedRoot = new WeakReference<>(null);
    private static WeakReference<View> boundAnchor = new WeakReference<>(null);
    private static WeakReference<View> transitionAnchor = new WeakReference<>(null);
    private static WeakReference<StorySeenBindingRetry> pendingBindingRetry =
            new WeakReference<>(null);
    private static long pendingBindingGeneration = -1L;
    private static final Map<ImageView, BindingState> BINDINGS = new WeakHashMap<>();
    private static final StorySeenBindingUpdates BINDING_UPDATES =
            new StorySeenBindingUpdates();

    static final class CapturedStory {
        final Object session;
        final Object item;
        final Object reel;
        final Object source;

        private CapturedStory(Object session, Object item, Object reel, Object source) {
            this.session = session;
            this.item = item;
            this.reel = reel;
            this.source = source;
        }
    }

    private static final class BindingState {
        private final CapturedStory story;
        private final WeakReference<ImageView> button;
        private final StorySeenKey storyKey;

        private BindingState(CapturedStory story, ImageView button, StorySeenKey storyKey) {
            this.story = story;
            this.button = new WeakReference<>(button);
            this.storyKey = storyKey;
        }
    }

    private StorySeenButton() {
    }

    public static void captureCurrentStoryFromProgress(
            Object session,
            Object item,
            Object reel,
            Object source,
            Object root) {
        captureCurrentStory(
                new CapturedStory(session, item, reel, source),
                root instanceof View ? (View) root : null,
                false
        );
    }

    public static void captureCurrentStoryFromHeader(
            Object session,
            Object item,
            Object reel,
            Object source,
            Object root) {
        captureCurrentStory(
                new CapturedStory(session, item, reel, source),
                root instanceof View ? (View) root : null,
                true
        );
    }

    public static void invalidateCurrentStory() {
        final long generation;
        final View anchor;
        synchronized (StorySeenButton.class) {
            generation = CAPTURE_GENERATION.incrementAndGet();
            queuedItem = null;
            queuedRoot = new WeakReference<>(null);
            View currentAnchor = boundAnchor.get();
            View retainedAnchor = transitionAnchor.get();
            anchor = currentAnchor != null ? currentAnchor : retainedAnchor;
            if (anchor != null) {
                transitionAnchor = new WeakReference<>(anchor);
            }
            boundItem = null;
            boundAnchor = new WeakReference<>(null);
        }
        Runnable invalidation = () -> {
            cancelPendingBindingThrough(generation);
            StorySeenButtonView.deactivate(anchor);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            invalidation.run();
        } else {
            Utils.runOnMainThread(invalidation);
        }
    }

    private static void captureCurrentStory(
            CapturedStory story,
            View root,
            boolean headerBinding) {
        final long generation;
        final View staleAnchor;
        synchronized (StorySeenButton.class) {
            View anchor = boundAnchor.get();
            View pendingRoot = queuedRoot.get();
            boolean boundStillUsable = anchor != null
                    && anchor.isAttachedToWindow()
                    && anchor.isShown();
            boolean differentPendingRoot = story.item == queuedItem
                    && root != null
                    && root != pendingRoot;
            if (!shouldScheduleCapture(
                    story.item,
                    queuedItem,
                    boundItem,
                    boundStillUsable,
                    headerBinding,
                    differentPendingRoot
            )) {
                return;
            }
            View retainedTransitionAnchor = transitionAnchor.get();
            staleAnchor = selectTransitionAnchor(
                    retainedTransitionAnchor,
                    staleBoundAnchorFor(story.item, headerBinding)
            );
            if (staleAnchor != null) {
                transitionAnchor = new WeakReference<>(staleAnchor);
            }
            queuedItem = story.item;
            queuedRoot = new WeakReference<>(root);
            generation = CAPTURE_GENERATION.incrementAndGet();
        }
        Runnable binding = () -> bindCapturedStory(
                generation,
                story,
                root,
                staleAnchor
        );
        if (root != null && Looper.myLooper() == Looper.getMainLooper()) {
            binding.run();
        } else {
            Utils.runOnMainThread(binding);
        }
    }

    private static void bindCapturedStory(
            long generation,
            CapturedStory story,
            View root,
            View staleAnchor) {
        if (!isLatestCapture(generation, CAPTURE_GENERATION.get())) {
            return;
        }
        cancelPendingBindingThrough(generation);
        if (!isLatestCapture(generation, CAPTURE_GENERATION.get())) {
            return;
        }
        StorySeenButtonView.deactivate(staleAnchor);

        View bound = null;
        boolean deferred = false;
        try {
            Activity activity = Utils.getActivity();
            int headerMenuId = ResourceUtils.getIdentifier(ResourceType.ID, HEADER_MENU_ID_NAME);
            View overflow = headerMenuId == 0
                    ? null
                    : root == null
                            ? activity == null ? null : activity.findViewById(headerMenuId)
                            : root.findViewById(headerMenuId);
            if (headerMenuId != 0 && root != null) {
                if (overflow == null) {
                    deferred = deferBinding(
                            generation,
                            story,
                            root,
                            headerMenuId
                    );
                } else {
                    bound = bind(overflow, story);
                    if (bound == null) {
                        deferred = deferBinding(
                                generation,
                                story,
                                root,
                                headerMenuId
                        );
                    }
                }
            } else {
                bound = bind(overflow, story);
            }
        } catch (Throwable throwable) {
            Logger.printException(() -> "Failed to locate the story header menu", throwable);
        } finally {
            if (!deferred) {
                finishCapture(generation, story.item, bound);
            }
        }
    }

    private static boolean deferBinding(
            long generation,
            CapturedStory story,
            View root,
            int headerMenuId) {
        StorySeenBindingRetry retry = new StorySeenBindingRetry(
                StorySeenButtonView.frameHost(root),
                () -> isLatestCapture(generation, CAPTURE_GENERATION.get()),
                () -> attemptDeferredBinding(
                        generation,
                        story,
                        root,
                        headerMenuId
                ),
                completed -> deferredBindingStopped(
                        generation,
                        story.item,
                        completed
                ),
                System::currentTimeMillis,
                MAX_DEFERRED_BINDING_ATTEMPTS,
                DEFERRED_BINDING_TIMEOUT_MILLIS
        );
        synchronized (StorySeenButton.class) {
            if (!isLatestCapture(generation, CAPTURE_GENERATION.get())
                    || queuedItem != story.item) {
                return false;
            }
            pendingBindingRetry = new WeakReference<>(retry);
            pendingBindingGeneration = generation;
        }
        boolean started = retry.start();
        if (!started) {
            clearPendingBinding(generation);
        }
        return started;
    }

    private static boolean attemptDeferredBinding(
            long generation,
            CapturedStory story,
            View root,
            int headerMenuId) {
        View overflow = root.findViewById(headerMenuId);
        if (overflow == null) {
            return false;
        }
        View bound = bind(overflow, story);
        if (bound == null) {
            return false;
        }
        finishCapture(generation, story.item, bound);
        return true;
    }

    private static void deferredBindingStopped(
            long generation,
            Object item,
            boolean completed) {
        clearPendingBinding(generation);
        if (!completed) {
            finishCapture(generation, item, null);
        }
    }

    private static void cancelPendingBindingThrough(long generation) {
        final StorySeenBindingRetry retry;
        synchronized (StorySeenButton.class) {
            if (pendingBindingGeneration > generation) {
                return;
            }
            retry = pendingBindingRetry.get();
            pendingBindingRetry = new WeakReference<>(null);
            pendingBindingGeneration = -1L;
        }
        if (retry != null) {
            retry.cancel();
        }
    }

    private static synchronized void clearPendingBinding(long generation) {
        if (pendingBindingGeneration == generation) {
            pendingBindingRetry = new WeakReference<>(null);
            pendingBindingGeneration = -1L;
        }
    }

    static boolean isLatestCapture(long captureGeneration, long latestGeneration) {
        return captureGeneration == latestGeneration;
    }

    static boolean shouldScheduleCapture(
            Object item,
            Object queued,
            Object bound,
            boolean boundStillAttached,
            boolean headerBinding,
            boolean differentPendingRoot) {
        return item != null
                && (headerBinding || item != queued || differentPendingRoot)
                && (headerBinding || item != bound || !boundStillAttached);
    }

    private static synchronized void finishCapture(long generation, Object item, View anchor) {
        if (!isLatestCapture(generation, CAPTURE_GENERATION.get()) || queuedItem != item) {
            return;
        }
        queuedItem = null;
        queuedRoot = new WeakReference<>(null);
        transitionAnchor = new WeakReference<>(null);
        if (anchor != null) {
            boundItem = item;
            boundAnchor = new WeakReference<>(anchor);
        } else {
            boundItem = null;
            boundAnchor = new WeakReference<>(null);
        }
    }

    private static View bind(
            View overflow,
            CapturedStory story) {
        boolean anchorParent = overflow != null && overflow.getParent() instanceof ViewGroup;
        if (!anchorParent) {
            return null;
        }

        try {
            boolean anonymousStories = Pref.viewStoriesAnonymously();
            boolean overflowVisible = overflow.getVisibility() == View.VISIBLE;
            boolean available = StorySeenBridge.isAvailable(
                    story.session,
                    story.item,
                    story.reel,
                    story.source
            );
            boolean display = shouldDisplay(
                    anonymousStories,
                    overflowVisible,
                    available
            );
            if (!display) {
                boolean retryBinding = shouldRetryBinding(
                        anonymousStories,
                        overflowVisible,
                        available
                );
                StorySeenButtonView.setUnavailable(
                        overflow,
                        !retryBinding
                );
                return retryBinding ? null : overflow;
            }

            StorySeenKey storyKey = StorySeenBridge.storyKey(story.session, story.item);
            ImageView button = StorySeenButtonView.prepare(overflow);
            BindingState bindingState = new BindingState(story, button, storyKey);
            synchronized (BINDINGS) {
                BINDINGS.put(button, bindingState);
            }
            BINDING_UPDATES.bind(
                    button,
                    storyKey,
                    () -> updateBinding(bindingState, StorySeenBridge.SeenState.MARKED),
                    () -> updateBinding(bindingState, StorySeenBridge.SeenState.UNMARKED)
            );
            StorySeenBridge.SeenState seenState = StorySeenBridge.state(
                    story.session,
                    story.item
            );
            boolean enabled = StorySeenButtonView.showState(button, seenState);
            if (enabled) {
                button.setOnClickListener(
                        view -> markCurrentStory(bindingState));
            } else {
                button.setOnClickListener(null);
            }
            return overflow;
        } catch (Throwable throwable) {
            StorySeenButtonView.setUnavailable(overflow, true);
            Logger.printException(() -> "Failed to bind mark story as seen button", throwable);
            return null;
        }
    }

    static <T> T selectTransitionAnchor(T retainedAnchor, T newCandidate) {
        return retainedAnchor != null ? retainedAnchor : newCandidate;
    }

    static boolean shouldDisplay(boolean anonymousStories, boolean overflowVisible, boolean supportedStory) {
        return anonymousStories && overflowVisible && supportedStory;
    }

    static boolean isCurrentBinding(Object expected, Object current) {
        return expected != null && expected == current;
    }

    static boolean shouldRetryBinding(
            boolean anonymousStories,
            boolean overflowVisible,
            boolean supportedStory) {
        return anonymousStories && !overflowVisible && supportedStory;
    }

    static boolean shouldInvalidateBoundStory(Object selectedItem, Object previousItem) {
        return previousItem != null && selectedItem != previousItem;
    }

    private static View staleBoundAnchorFor(Object selectedItem, boolean headerBinding) {
        if (!headerBinding && !shouldInvalidateBoundStory(selectedItem, boundItem)) {
            return null;
        }
        return boundAnchor.get();
    }

    private static void markCurrentStory(BindingState bindingState) {
        ImageView button = bindingState.button.get();
        if (button == null || !isCurrentBinding(bindingState, currentBinding(button))) {
            return;
        }
        CapturedStory story = bindingState.story;
        button.setEnabled(false);
        StorySeenRequestScope.Listener listener = resultListener(
                () -> Utils.runOnMainThread(
                        () -> Utils.showToastShort(str("piko_story_seen_sent"))),
                () -> BINDING_UPDATES.fail(bindingState.storyKey)
        );
        if (StorySeenBridge.markSeen(
                story.session,
                story.item,
                story.reel,
                story.source,
                listener
        )) {
            StorySeenBridge.SeenState state = StorySeenBridge.state(story.session, story.item);
            StorySeenButtonView.showState(button, state);
            if (state == StorySeenBridge.SeenState.PENDING) {
                button.postDelayed(
                        () -> refreshExpiredPendingBinding(bindingState),
                        StorySeenRequestScope.PERMIT_TTL_MILLIS + 1L
                );
            }
            return;
        }

        StorySeenButtonView.showState(button, StorySeenBridge.SeenState.UNMARKED);
    }

    static void updateMarkedBindings(StorySeenKey storyKey) {
        BINDING_UPDATES.complete(storyKey);
    }

    static StorySeenRequestScope.Listener resultListener(
            Runnable notifyMarked,
            Runnable updateFailedBinding) {
        AtomicBoolean resolved = new AtomicBoolean();
        return new StorySeenRequestScope.Listener() {
            @Override
            public void onComplete() {
                if (!resolved.compareAndSet(false, true)) {
                    return;
                }
                notifyMarked.run();
            }

            @Override
            public void onFailure() {
                if (resolved.compareAndSet(false, true)) {
                    updateFailedBinding.run();
                }
            }
        };
    }

    private static BindingState currentBinding(ImageView button) {
        synchronized (BINDINGS) {
            return BINDINGS.get(button);
        }
    }

    private static void updateBinding(
            BindingState bindingState,
            StorySeenBridge.SeenState state) {
        ImageView button = bindingState.button.get();
        if (button == null) {
            return;
        }
        button.post(() -> {
            if (!isCurrentBinding(bindingState, currentBinding(button))) {
                return;
            }
            StorySeenBridge.SeenState currentState = state == StorySeenBridge.SeenState.UNMARKED
                    && StorySeenState.contains(bindingState.storyKey)
                    ? StorySeenBridge.SeenState.MARKED
                    : state;
            boolean enabled = StorySeenButtonView.showState(button, currentState);
            if (enabled) {
                button.setOnClickListener(view -> markCurrentStory(bindingState));
            } else {
                button.setOnClickListener(null);
            }
        });
    }

    private static void refreshExpiredPendingBinding(BindingState bindingState) {
        ImageView button = bindingState.button.get();
        if (button == null || !isCurrentBinding(bindingState, currentBinding(button))) {
            return;
        }
        CapturedStory story = bindingState.story;
        updateBinding(bindingState, StorySeenBridge.state(story.session, story.item));
    }
}
