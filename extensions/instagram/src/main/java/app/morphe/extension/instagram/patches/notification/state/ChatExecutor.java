package app.morphe.extension.instagram.patches.notification.state;

import java.util.ArrayDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Owns the one thread that may touch a {@link ChatReducer} (R2) and gives callers a bounded wait
 * (R3: fail open). Pure Java.
 *
 * <p>Writers use {@link #post}; they never block. {@code onNotify} uses {@link #call} or
 * {@link #snapshot}, which wait at most {@code timeoutMs} and return null on timeout or error, so
 * the caller can return Instagram's own notification unchanged. Failures are counted; after
 * {@link #MAX_FAILURES} within {@link #FAILURE_WINDOW_MS} the executor reports {@link #isDisabled}
 * for the rest of the process and the caller should fall back to the old behaviour.
 */
public final class ChatExecutor {
    public static final int MAX_FAILURES = 5;
    public static final long FAILURE_WINDOW_MS = 10 * 60_000L;

    private final ChatReducer reducer;
    private final ExecutorService thread;
    private final LongSupplier clock;
    private final ArrayDeque<Long> failures = new ArrayDeque<>();
    private volatile boolean disabled;
    private volatile String lastFailure = "";

    public ChatExecutor(ChatReducer reducer, LongSupplier clock) {
        this.reducer = reducer;
        this.clock = clock;
        this.thread = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "piko-chat-state");
            t.setDaemon(true);
            return t;
        });
    }

    /** Work done on the owning thread. */
    public interface Task<T> {
        T run(ChatReducer reducer) throws Exception;
    }

    /** Fire and forget. Exceptions are counted as failures. */
    public void post(final Task<?> task) {
        if (disabled) return;
        thread.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    task.run(reducer);
                } catch (Throwable t) {
                    lastFailure = String.valueOf(t);
                    recordFailure();
                } finally {
                    flush();
                }
            }
        });
    }

    /** Runs on the owning thread, waits up to timeoutMs, returns null on timeout, error or when disabled. */
    public <T> T call(final Task<T> task, long timeoutMs) {
        if (disabled) return null;
        Future<T> f = thread.submit(new Callable<T>() {
            @Override
            public T call() throws Exception {
                try {
                    return task.run(reducer);
                } finally {
                    flush();
                }
            }
        });
        try {
            return f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            f.cancel(false);
            if (t instanceof InterruptedException) Thread.currentThread().interrupt();
            lastFailure = t instanceof java.util.concurrent.ExecutionException && t.getCause() != null
                    ? String.valueOf(t.getCause()) : t.getClass().getSimpleName();
            recordFailure();
            return null;
        }
    }

    /** Current plan for a chat without changing anything; null when unknown, slow or failed. */
    public RenderPlan snapshot(final ChatKey key, long timeoutMs) {
        return call(new Task<RenderPlan>() {
            @Override
            public RenderPlan run(ChatReducer r) {
                return r.peek(key);
            }
        }, timeoutMs);
    }

    private void flush() {
        try {
            reducer.flush();
        } catch (Throwable t) {
            lastFailure = String.valueOf(t);
        }
    }

    /** Why the last call or post failed, for the log. */
    public String lastFailure() {
        return lastFailure;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void shutdown() {
        thread.shutdownNow();
    }

    private synchronized void recordFailure() {
        long now = clock.getAsLong();
        failures.addLast(now);
        while (!failures.isEmpty() && now - failures.peekFirst() > FAILURE_WINDOW_MS) failures.pollFirst();
        if (failures.size() >= MAX_FAILURES) disabled = true;
    }
}
