package com.miwi.gitworktree.service;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/** Updates elapsed time without scanning files or pretending to know Git's completion percentage. */
public final class OperationWaitFeedback implements AutoCloseable {
    private final LongSupplier clock;
    private final LongConsumer update;
    private final long startedAt;
    private final ScheduledFuture<?> ticker;
    private boolean closed;

    public OperationWaitFeedback(ScheduledExecutorService scheduler, LongSupplier clock, LongConsumer update) {
        this.clock = clock;
        this.update = update;
        startedAt = clock.getAsLong();
        update.accept(0);
        ticker = scheduler.scheduleAtFixedRate(this::tick, 1, 1, TimeUnit.SECONDS);
    }

    private synchronized void tick() {
        if (!closed) update.accept(Math.max(0, TimeUnit.NANOSECONDS.toSeconds(clock.getAsLong() - startedAt)));
    }

    @Override
    public synchronized void close() {
        // Serialize completion with the callback so queued ticks cannot update a closed progress dialog.
        closed = true;
        ticker.cancel(false);
    }

    public static String duration(long seconds) {
        long nonNegative = Math.max(0, seconds);
        long hours = nonNegative / 3600;
        long minutes = nonNegative / 60 % 60;
        long remainder = nonNegative % 60;
        String tail = twoDigits(minutes) + ":" + twoDigits(remainder);
        return hours == 0 ? tail : hours + ":" + tail;
    }

    private static String twoDigits(long value) {
        return value < 10 ? "0" + value : Long.toString(value);
    }
}
