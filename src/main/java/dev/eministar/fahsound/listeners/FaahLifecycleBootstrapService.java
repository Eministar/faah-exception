package dev.eministar.fahsound.listeners;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

public final class FaahLifecycleBootstrapService {
    public FaahLifecycleBootstrapService() {
        FaahExternalSystemFailureWatcher.getInstance();
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            FaahProjectEventInstaller.install(project);
        }
    }
}
