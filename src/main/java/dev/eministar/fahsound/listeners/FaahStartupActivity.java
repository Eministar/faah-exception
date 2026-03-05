package dev.eministar.fahsound.listeners;

import com.intellij.execution.ExecutionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.task.ProjectTaskListener;
import com.intellij.util.messages.MessageBusConnection;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

public final class FaahStartupActivity implements ProjectActivity {
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        FaahExternalSystemFailureWatcher.getInstance();
        FaahBuildEventWatcher.install(project);
        MessageBusConnection connection = project.getMessageBus().connect(project);
        connection.subscribe(ProjectTaskListener.TOPIC, new FaahProjectTaskFailureListener(project));
        connection.subscribe(ExecutionManager.EXECUTION_TOPIC, new FaahExecutionFailureListener(project));
        return Unit.INSTANCE;
    }
}
