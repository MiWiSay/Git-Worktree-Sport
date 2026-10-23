package com.miwi.gitworktree.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DateFormat;

/** Git 不记录登记创建时间时，使用实际工作树文件夹的文件系统创建时间。 */
final class WorktreeCreatedTime {
    private WorktreeCreatedTime() {}

    static String format(Path path, String unavailable) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            var timestamp = attributes.creationTime();
            if (timestamp == null || timestamp.toMillis() <= 0) timestamp = attributes.lastModifiedTime();
            if (timestamp == null || timestamp.toMillis() <= 0) return unavailable;
            return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(timestamp.toMillis());
        } catch (Exception ignored) {
            return unavailable;
        }
    }
}
