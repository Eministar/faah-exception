package dev.eministar.fahsound.listeners;

import com.intellij.openapi.project.Project;
import com.intellij.task.ProjectTaskListener;
import com.intellij.task.ProjectTaskManager;
import dev.eministar.fahsound.sound.FaahSoundService;
import dev.eministar.fahsound.sound.FaahSoundEvent;
import org.jetbrains.annotations.NotNull;

public final class FaahProjectTaskFailureListener implements ProjectTaskListener {
    private final Project project;
    private final FaahBuildOutcomeTracker buildOutcomeTracker;

    public FaahProjectTaskFailureListener(@NotNull Project project, @NotNull FaahBuildOutcomeTracker buildOutcomeTracker) {
        this.project = project;
        this.buildOutcomeTracker = buildOutcomeTracker;
    }

    @Override
    public void finished(@NotNull ProjectTaskManager.Result result) {
        if (buildOutcomeTracker.shouldSuppressProjectTaskResult()) {
            return;
        }
        if (result.hasErrors()) {
            FaahSoundService.getInstance().playEvent(project, FaahSoundEvent.BUILD_FAILED, "Build");
            return;
        }
        if (!result.isAborted()) {
            FaahSoundService.getInstance().playEvent(project, FaahSoundEvent.BUILD_SUCCESS, "Build");
        }
    }
}
