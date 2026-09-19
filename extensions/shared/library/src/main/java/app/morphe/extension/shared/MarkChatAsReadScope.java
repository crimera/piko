/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.shared;

public final class MarkChatAsReadScope {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    private MarkChatAsReadScope() {
    }

    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    public static void run(Operation operation) throws Exception {
        ACTIVE.set(true);
        try {
            operation.run();
        } finally {
            ACTIVE.remove();
        }
    }

    @FunctionalInterface
    public interface Operation {
        void run() throws Exception;
    }
}
