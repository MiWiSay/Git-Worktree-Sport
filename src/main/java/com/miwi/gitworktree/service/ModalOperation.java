package com.miwi.gitworktree.service;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

public final class ModalOperation {
    private ModalOperation() {}

    public static <T> T run(Project project, String title, String description, Callable<T> operation) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ProgressManager.getInstance().run(new Task.Modal(project, title, false) {
            @Override
            public void run(ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                indicator.setText(description);
                try {
                    result.set(operation.call());
                } catch (Throwable throwable) {
                    failure.set(throwable);
                }
            }
        });
        Throwable throwable = failure.get();
        if (throwable instanceof Exception exception) throw exception;
        if (throwable instanceof Error error) throw error;
        return result.get();
    }
}
