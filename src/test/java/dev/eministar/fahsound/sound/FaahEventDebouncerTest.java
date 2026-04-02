package dev.eministar.fahsound.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaahEventDebouncerTest {
    @Test
    void blocksRepeatedEventsWithinOneScope() {
        FaahEventDebouncer debouncer = new FaahEventDebouncer();

        assertTrue(debouncer.tryAcquire("build", 1000));
        assertFalse(debouncer.tryAcquire("build", 1000));
    }

    @Test
    void keepsDifferentScopesIndependent() {
        FaahEventDebouncer debouncer = new FaahEventDebouncer();

        assertTrue(debouncer.tryAcquire("build", 1000));
        assertTrue(debouncer.tryAcquire("run", 1000));
    }
}
