package dev.eministar.fahsound.listeners;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

public final class FaahStartupActivity implements ProjectActivity {
    @Override
    public Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        FaahProjectEventInstaller.install(project);
        return Unit.INSTANCE;
    }
}
