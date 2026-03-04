package dev.eministar.fahsound.listeners;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import dev.eministar.fahsound.sound.FaahSoundService;
import dev.eministar.fahsound.sound.FaahSoundEvent;
import org.jetbrains.annotations.NotNull;

public final class FaahExecutionFailureListener implements ExecutionListener {
    private final Project project;

    public FaahExecutionFailureListener(@NotNull Project project) {
        this.project = project;
    }

    @Override
    public void processTerminated(@NotNull String executorId,
                                  @NotNull ExecutionEnvironment env,
                                  @NotNull ProcessHandler handler,
                                  int exitCode) {
        if (exitCode != 0) {
            FaahSoundService.getInstance().playEvent(project, FaahSoundEvent.RUN_FAILED, "Run/Debug");
        } else {
            FaahSoundService.getInstance().playEvent(project, FaahSoundEvent.RUN_SUCCESS, "Run/Debug");
        }
    }
}
