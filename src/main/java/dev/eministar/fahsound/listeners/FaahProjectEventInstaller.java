package dev.eministar.fahsound.listeners;

import com.intellij.execution.ExecutionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.task.ProjectTaskListener;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public final class FaahProjectEventInstaller {
    private static final Key<Boolean> INSTALLED_KEY = Key.create("dev.eministar.fahsound.listeners.installed");

    private FaahProjectEventInstaller() {
    }

    public static void install(@NotNull Project project) {
        if (project.isDisposed()) {
            return;
        }
        synchronized (project) {
            if (Boolean.TRUE.equals(project.getUserData(INSTALLED_KEY))) {
                return;
            }
            project.putUserData(INSTALLED_KEY, Boolean.TRUE);
        }
        FaahBuildEventWatcher.install(project);
        MessageBusConnection connection = project.getMessageBus().connect(project);
        connection.subscribe(ProjectTaskListener.TOPIC, new FaahProjectTaskFailureListener(project));
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new FaahExecutionFailureListener(project));
    }
}
