package com.miwi.gitworktree.ui;

import com.miwi.gitworktree.model.WorktreeEntry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 单仓库内按可见名称分组；不同仓库始终由上层独立管理。 */
final class WorktreeGroups {
    private WorktreeGroups() {}

    static List<Group> group(List<WorktreeEntry> entries) {
        Map<String, List<WorktreeEntry>> byName = new LinkedHashMap<>();
        for (WorktreeEntry entry : entries) {
            byName.computeIfAbsent(shortLocation(entry), ignored -> new ArrayList<>()).add(entry);
        }
        return byName.entrySet().stream().map(item -> new Group(item.getKey(), List.copyOf(item.getValue()))).toList();
    }

    static String label(WorktreeEntry entry, boolean grouped) {
        String name = shortLocation(entry);
        return grouped && !entry.head().isBlank() ? name + "  [" + entry.shortHead() + "]" : name;
    }

    static String countLabel(int count) {
        return Integer.toString(count);
    }

    static String shortLocation(WorktreeEntry entry) {
        Path path = Path.of(entry.path()).toAbsolutePath().normalize();
        Path repository = Path.of(entry.repositoryRoot()).toAbsolutePath().normalize();
        String folder = path.getFileName() == null ? entry.path() : path.getFileName().toString();
        String branch = entry.branch();
        if (branch.isBlank() || branch.startsWith("(")) return folder;
        if (entry.main()) return branch;
        Path parent = path.getParent();
        String parentName = parent == null || parent.getFileName() == null ? "" : parent.getFileName().toString();
        if (parentName.isBlank()) return branch;
        // 仓库标题已提供项目名；只省略重复的目录前缀，不截断含斜杠的分支名。
        if (parent.equals(repository.getParent()) || parent.equals(repository)
                || repository.getFileName() != null && parent.getFileName().equals(repository.getFileName())) return branch;
        return parentName + "/" + branch;
    }

    record Group(String name, List<WorktreeEntry> entries) {}
}
