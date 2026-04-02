package dev.eministar.fahsound.listeners;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaahBuildOutcomeTrackerTest {
    @Test
    void warningLifecycleCanBeMarkedConsumedAndCleared() {
        FaahBuildOutcomeTracker tracker = new FaahBuildOutcomeTracker();

        assertTrue(tracker.markWarning("build-1"));
        assertFalse(tracker.markWarning("build-1"));
        assertTrue(tracker.consumeWarning("build-1"));
        assertFalse(tracker.consumeWarning("build-1"));

        tracker.markWarning("build-2");
        tracker.clearBuild("build-2");
        assertFalse(tracker.consumeWarning("build-2"));
    }

    @Test
    void buildViewFinishSuppressesFallbackProjectTaskNotifications() {
        FaahBuildOutcomeTracker tracker = new FaahBuildOutcomeTracker();

        assertFalse(tracker.shouldSuppressProjectTaskResult());
        tracker.markBuildViewFinished();
        assertTrue(tracker.shouldSuppressProjectTaskResult());

        tracker.clear();
        assertFalse(tracker.shouldSuppressProjectTaskResult());
    }
}
