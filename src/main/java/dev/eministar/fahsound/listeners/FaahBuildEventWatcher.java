package dev.eministar.fahsound.listeners;

import com.intellij.build.BuildProgressListener;
import com.intellij.build.BuildViewManager;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.SuccessResult;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import dev.eministar.fahsound.sound.FaahSoundEvent;
import dev.eministar.fahsound.sound.FaahSoundService;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FaahBuildEventWatcher implements BuildProgressListener, Disposable {
    private final Project project;
    private final Set<Object> warnedBuildIds = ConcurrentHashMap.newKeySet();

    private FaahBuildEventWatcher(@NotNull Project project) {
        this.project = project;
    }

    public static void install(@NotNull Project project) {
        FaahBuildEventWatcher watcher = new FaahBuildEventWatcher(project);
        BuildViewManager buildViewManager = project.getService(BuildViewManager.class);
        buildViewManager.addListener(watcher, watcher);
        Disposer.register(project, watcher);
    }

    @Override
    public void onEvent(@NotNull Object buildId, @NotNull BuildEvent event) {
        if (event instanceof MessageEvent messageEvent && messageEvent.getKind() == MessageEvent.Kind.WARNING) {
            if (warnedBuildIds.add(buildId)) {
                FaahSoundService.getInstance().playEvent(project, FaahSoundEvent.BUILD_WARNING, "Build warning");
            }
        }
        if (event instanceof FinishBuildEvent finishBuildEvent) {
            if (finishBuildEvent.getResult() instanceof SuccessResult successResult && !successResult.getWarnings().isEmpty()) {
                if (warnedBuildIds.add(buildId)) {
                    FaahSoundService.getInstance().playEvent(project, FaahSoundEvent.BUILD_WARNING, "Build warning");
                }
            }
            warnedBuildIds.remove(buildId);
        }
    }

    @Override
    public void dispose() {
        warnedBuildIds.clear();
    }
}
