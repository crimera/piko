/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.twitter.settings;

final class SettingsSearchSession {
    private boolean active;
    private String query = "";
    private long generation;

    long enter() {
        active = true;
        query = "";
        generation++;
        return generation;
    }

    ExitAction exit(boolean inputWillNotifyThroughTextWatcher) {
        reset();
        return inputWillNotifyThroughTextWatcher
                ? ExitAction.WAIT_FOR_TEXT_WATCHER
                : ExitAction.NOTIFY_DIRECTLY;
    }

    void reset() {
        active = false;
        query = "";
    }

    void updateQuery(CharSequence value) {
        query = value == null ? "" : value.toString();
    }

    State snapshot() {
        return new State(active, query);
    }

    void restore(State state) {
        reset();
        generation++;
        if (state == null) {
            return;
        }
        active = state.active;
        query = state.query;
    }

    boolean isActive() {
        return active;
    }

    boolean isCurrent(long expectedGeneration) {
        return active && generation == expectedGeneration;
    }

    String query() {
        return query;
    }

    static final class State {
        private final boolean active;
        private final String query;

        State(boolean active, String query) {
            this.active = active;
            this.query = query == null ? "" : query;
        }
    }

    enum ExitAction {
        WAIT_FOR_TEXT_WATCHER,
        NOTIFY_DIRECTLY
    }
}
