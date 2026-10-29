package com.intellij.openapi.project;

/** Isolates open-project protection from the user's running IDE in Git integration tests. */
public final class ProjectManager {
    public static Project[] projects = new Project[0];
    private static final ProjectManager INSTANCE = new ProjectManager();
    public static ProjectManager getInstance() { return INSTANCE; }
    public Project[] getOpenProjects() { return projects; }
}
