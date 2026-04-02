package dev.eministar.fahsound.sound;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class FaahEventDebouncer {
    private final Map<String, AtomicLong> lastPlaybackMillisByScope = new ConcurrentHashMap<>();

    public boolean tryAcquire(@NotNull String scopeId, int debounceMs) {
        if (debounceMs <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        AtomicLong lastPlayback = lastPlaybackMillisByScope.computeIfAbsent(scopeId, ignored -> new AtomicLong(0L));
        while (true) {
            long previous = lastPlayback.get();
            if (now - previous < debounceMs) {
                return false;
            }
            if (lastPlayback.compareAndSet(previous, now)) {
                return true;
            }
        }
    }
}
