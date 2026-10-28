package com.miwi.gitworktree.service;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.miwi.gitworktree.ui.I18n;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

public final class ModalOperation {
    private ModalOperation() {}

    public static <T> T run(Project project, String title, String description, Callable<T> operation) throws Exception {
        return run(project, title, description, null, operation);
    }

    public static <T> T runWithWaitingFeedback(Project project, String title, String description,
                                               String waitingDescription, Callable<T> operation) throws Exception {
        return run(project, title, description, waitingDescription, operation);
    }

    private static <T> T run(Project project, String title, String description,
                             String waitingDescription, Callable<T> operation) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        // Git removal is not transactional: cancellation can leave partially deleted files or metadata.
        ProgressManager.getInstance().run(new Task.Modal(project, title, false) {
            @Override
            public void run(ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText(description);
                OperationWaitFeedback waiting = null;
                try {
                    if (waitingDescription != null) {
                        waiting = new OperationWaitFeedback(AppExecutorUtil.getAppScheduledExecutorService(),
                                System::nanoTime, seconds -> {
                            indicator.setText2(I18n.format("progress.elapsed", OperationWaitFeedback.duration(seconds)));
                            if (seconds >= 30) indicator.setText(waitingDescription);
                        });
                    }
                    result.set(operation.call());
                } catch (Throwable throwable) {
                    failure.set(throwable);
                } finally {
                    if (waiting != null) waiting.close();
                }
            }
        });
        Throwable throwable = failure.get();
        if (throwable instanceof Exception exception) throw exception;
        if (throwable instanceof Error error) throw error;
        return result.get();
    }
}
