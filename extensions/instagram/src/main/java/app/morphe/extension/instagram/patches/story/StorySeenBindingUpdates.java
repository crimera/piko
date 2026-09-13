/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.story;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class StorySeenBindingUpdates {
    private final Map<Object, BindingUpdate> bindings = new WeakHashMap<>();

    synchronized void bind(
            Object owner,
            StorySeenKey storyKey,
            Runnable markedUpdate,
            Runnable failedUpdate) {
        if (owner == null || storyKey == null) {
            bindings.remove(owner);
            return;
        }
        bindings.put(owner, new BindingUpdate(storyKey, markedUpdate, failedUpdate));
    }

    void complete(StorySeenKey storyKey) {
        update(storyKey, true);
    }

    void fail(StorySeenKey storyKey) {
        update(storyKey, false);
    }

    private void update(StorySeenKey storyKey, boolean completed) {
        if (storyKey == null) {
            return;
        }
        List<Runnable> callbacks = new ArrayList<>();
        synchronized (this) {
            for (BindingUpdate binding : bindings.values()) {
                if (storyKey.equals(binding.storyKey)) {
                    callbacks.add(completed ? binding.markedUpdate : binding.failedUpdate);
                }
            }
        }
        for (Runnable callback : callbacks) {
            try {
                callback.run();
            } catch (Throwable ignored) {
                // A failed header update must not prevent other headers from updating.
            }
        }
    }

    private static final class BindingUpdate {
        private final StorySeenKey storyKey;
        private final Runnable markedUpdate;
        private final Runnable failedUpdate;

        private BindingUpdate(
                StorySeenKey storyKey,
                Runnable markedUpdate,
                Runnable failedUpdate) {
            this.storyKey = storyKey;
            this.markedUpdate = markedUpdate;
            this.failedUpdate = failedUpdate;
        }
    }
}
