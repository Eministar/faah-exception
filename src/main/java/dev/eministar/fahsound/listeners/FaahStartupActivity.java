package dev.eministar.fahsound.listeners;

import com.intellij.execution.ExecutionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.task.ProjectTaskListener;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

public final class FaahStartupActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
        FaahExternalSystemFailureWatcher.getInstance();
        FaahBuildEventWatcher.install(project);
        FaahTerminalFailureWatcher.install(project);
        MessageBusConnection connection = project.getMessageBus().connect(project);
        connection.subscribe(ProjectTaskListener.TOPIC, new FaahProjectTaskFailureListener(project));
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new FaahExecutionFailureListener(project));
    }
}
