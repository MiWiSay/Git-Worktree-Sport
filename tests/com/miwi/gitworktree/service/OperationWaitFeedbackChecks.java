package com.miwi.gitworktree.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Deterministic clock/scheduler checks; no real deletion or wall-clock waits. */
public final class OperationWaitFeedbackChecks {
    private static int assertions;

    public static void main(String[] args) {
        equal(OperationWaitFeedback.duration(-1), "00:00");
        equal(OperationWaitFeedback.duration(0), "00:00");
        equal(OperationWaitFeedback.duration(59), "00:59");
        equal(OperationWaitFeedback.duration(60), "01:00");
        equal(OperationWaitFeedback.duration(3599), "59:59");
        equal(OperationWaitFeedback.duration(3600), "1:00:00");
        equal(OperationWaitFeedback.duration(90061), "25:01:01");
        var clock = new AtomicLong(10_000);
        List<Long> updates = new ArrayList<>();
        try (var scheduler = new ManualScheduler()) {
            var feedback = new OperationWaitFeedback(scheduler, clock::get, updates::add);
            equal(updates, List.of(0L));
            equal(scheduler.period, 1L);
            equal(scheduler.unit, TimeUnit.SECONDS);
            clock.addAndGet(TimeUnit.SECONDS.toNanos(29));
            scheduler.callback.run();
            equal(updates.getLast(), 29L);
            clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
            scheduler.callback.run();
            equal(updates.getLast(), 30L);
            feedback.close();
            equal(scheduler.future.isCancelled(), true);
            scheduler.callback.run();
            equal(updates, List.of(0L, 29L, 30L));
            feedback.close();
            equal(scheduler.future.isCancelled(), true);
        }
        try (var scheduler = new ManualScheduler()) {
            try (var feedback = new OperationWaitFeedback(scheduler, clock::get, updates::add)) {
                throw new IllegalStateException("Simulated Git failure");
            } catch (IllegalStateException expected) {
                equal(scheduler.future.isCancelled(), true);
            }
        }
        System.out.println("PASS: " + assertions + " operation wait feedback assertions");
    }

    private static void equal(Object actual, Object expected) {
        assertions++;
        if (!java.util.Objects.equals(actual, expected)) {
            throw new AssertionError("expected=" + expected + "; actual=" + actual);
        }
    }

    private static final class ManualScheduler extends ScheduledThreadPoolExecutor {
        private Runnable callback;
        private ScheduledFuture<?> future;
        private long period;
        private TimeUnit unit;

        private ManualScheduler() { super(1); }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay,
                                                      long period, TimeUnit unit) {
            equal(initialDelay, 1L);
            this.callback = command;
            this.period = period;
            this.unit = unit;
            // Keep the future real (including cancellation), but drive ticks explicitly.
            future = super.scheduleAtFixedRate(command, 1, 1, TimeUnit.DAYS);
            return future;
        }
    }
}
