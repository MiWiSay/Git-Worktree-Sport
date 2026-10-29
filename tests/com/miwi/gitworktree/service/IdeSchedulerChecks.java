package com.miwi.gitworktree.service;

import com.intellij.util.concurrency.AppExecutorUtil;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs the production timer against real IDE libraries, not a permissive JDK scheduler substitute. */
public final class IdeSchedulerChecks {
    public static void main(String[] args) throws Exception {
        AtomicInteger updates = new AtomicInteger();
        AtomicInteger elapsed = new AtomicInteger();
        CountDownLatch repeated = new CountDownLatch(3);
        var feedback = new OperationWaitFeedback(AppExecutorUtil.getAppScheduledExecutorService(),
                System::nanoTime, seconds -> {
                    elapsed.set((int) seconds);
                    updates.incrementAndGet();
                    repeated.countDown();
                });
        try {
            if (!repeated.await(15, TimeUnit.SECONDS)) throw new AssertionError("IDE timer did not tick repeatedly");
            if (elapsed.get() < 2) throw new AssertionError("Elapsed duration is not based on the real clock");
        } finally {
            feedback.close();
        }
        int atClose = updates.get();
        Thread.sleep(1250);
        if (updates.get() != atClose) throw new AssertionError("IDE timer updated after close");
        feedback.close();
        System.out.println("PASS: " + args[0] + "; real shared IDE scheduler starts, repeats and stops cleanly");
        // This is a standalone test JVM. Never shut down the shared IDE executor from plugin code.
        System.exit(0);
    }
}
