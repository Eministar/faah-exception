package dev.eministar.fahsound.listeners;

import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class FaahBuildOutcomeTracker {
    private static final long PROJECT_TASK_SUPPRESSION_WINDOW_MS = 2000L;

    private final Set<Object> warnedBuildIds = ConcurrentHashMap.newKeySet();
    private final AtomicLong lastBuildViewFinishMillis = new AtomicLong(0L);

    public boolean markWarning(@NotNull Object buildId) {
        return warnedBuildIds.add(buildId);
    }

    public boolean consumeWarning(@NotNull Object buildId) {
        return warnedBuildIds.remove(buildId);
    }

    public void clearBuild(@NotNull Object buildId) {
        warnedBuildIds.remove(buildId);
    }

    public void markBuildViewFinished() {
        lastBuildViewFinishMillis.set(System.currentTimeMillis());
    }

    public boolean shouldSuppressProjectTaskResult() {
        return System.currentTimeMillis() - lastBuildViewFinishMillis.get() <= PROJECT_TASK_SUPPRESSION_WINDOW_MS;
    }

    public void clear() {
        warnedBuildIds.clear();
        lastBuildViewFinishMillis.set(0L);
    }
}
