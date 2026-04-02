package dev.eministar.fahsound.listeners;

import com.intellij.build.BuildProgressListener;
import com.intellij.build.BuildViewManager;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FailureResult;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.SuccessResult;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import dev.eministar.fahsound.sound.FaahSoundEvent;
import dev.eministar.fahsound.sound.FaahSoundService;
import org.jetbrains.annotations.NotNull;

public final class FaahBuildEventWatcher implements BuildProgressListener, Disposable {
    private final Project project;
    private final FaahBuildOutcomeTracker buildOutcomeTracker;

    private FaahBuildEventWatcher(@NotNull Project project, @NotNull FaahBuildOutcomeTracker buildOutcomeTracker) {
        this.project = project;
        this.buildOutcomeTracker = buildOutcomeTracker;
    }

    public static void install(@NotNull Project project, @NotNull FaahBuildOutcomeTracker buildOutcomeTracker) {
        FaahBuildEventWatcher watcher = new FaahBuildEventWatcher(project, buildOutcomeTracker);
        BuildViewManager buildViewManager = project.getService(BuildViewManager.class);
        buildViewManager.addListener(watcher, watcher);
        Disposer.register(project, watcher);
    }

    @Override
    public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
        if (event instanceof MessageEvent messageEvent && messageEvent.getKind() == MessageEvent.Kind.WARNING) {
            if (buildOutcomeTracker.markWarning(buildId)) {
                FaahSoundService.getInstance().playEvent(project, FaahSoundEvent.BUILD_WARNING, "Build warning");
            }
            return;
        }
        if (event instanceof FinishBuildEvent finishBuildEvent) {
            buildOutcomeTracker.markBuildViewFinished();
            if (finishBuildEvent.getResult() instanceof FailureResult) {
                buildOutcomeTracker.clearBuild(buildId);
                FaahSoundService.getInstance().playEvent(project, FaahSoundEvent.BUILD_FAILED, "Build");
                return;
            }
            if (finishBuildEvent.getResult() instanceof SuccessResult successResult) {
                boolean warningAlreadySeen = buildOutcomeTracker.consumeWarning(buildId);
                if (!warningAlreadySeen && !successResult.getWarnings().isEmpty()) {
                    FaahSoundService.getInstance().playEvent(project, FaahSoundEvent.BUILD_WARNING, "Build warning");
                    return;
                }
                if (!warningAlreadySeen) {
                    FaahSoundService.getInstance().playEvent(project, FaahSoundEvent.BUILD_SUCCESS, "Build");
                }
                return;
            }
            buildOutcomeTracker.clearBuild(buildId);
        }
    }

    @Override
    public void dispose() {
        buildOutcomeTracker.clear();
    }
}
