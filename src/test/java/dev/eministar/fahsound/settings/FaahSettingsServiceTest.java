package dev.eministar.fahsound.settings;

import dev.eministar.fahsound.sound.FaahSoundCatalog;
import dev.eministar.fahsound.sound.FaahSoundEvent;
import dev.eministar.fahsound.visual.FaahVisualCatalog;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaahSettingsServiceTest {
    @Test
    void loadStateMigratesLegacySoundAndVisualDurations() {
        FaahSettingsService service = new FaahSettingsService();
        FaahSettingsService.StateData state = new FaahSettingsService.StateData();
        state.soundFileName = "error.mp3";
        state.showFailureImage = false;
        state.maxDurationMsByEvent.put(FaahSoundEvent.BUILD_FAILED.getId(), 1500);

        service.loadState(state);

        assertEquals(FaahSoundCatalog.bundled("error.mp3"), service.getSoundSource(FaahSoundEvent.BUILD_SUCCESS));
        assertEquals(1500, service.getVisualDurationMs(FaahSoundEvent.BUILD_FAILED));
        assertEquals(FaahVisualCatalog.SOURCE_NONE, service.getVisualSource(FaahSoundEvent.BUILD_FAILED));
    }

    @Test
    void loadStateClampsInvalidValuesAndFillsMissingEvents() {
        FaahSettingsService service = new FaahSettingsService();
        FaahSettingsService.StateData state = new FaahSettingsService.StateData();
        state.volume = 150;
        state.debounceMs = -50;
        state.soundByEvent.put(FaahSoundEvent.RUN_FAILED.getId(), "error.mp3");
        state.maxDurationMsByEvent.put(FaahSoundEvent.RUN_FAILED.getId(), -700);
        state.visualDurationMsByEvent.put(FaahSoundEvent.RUN_FAILED.getId(), -10);

        service.loadState(state);

        assertEquals(100, service.getVolume());
        assertEquals(0, service.getDebounceMs());
        assertEquals(FaahSoundCatalog.bundled("error.mp3"), service.getSoundSource(FaahSoundEvent.RUN_FAILED));
        assertEquals(0, service.getMaxDurationMs(FaahSoundEvent.RUN_FAILED));
        assertEquals(0, service.getVisualDurationMs(FaahSoundEvent.RUN_FAILED));
        assertTrue(service.getState().soundByEvent.containsKey(FaahSoundEvent.BUILD_FAILED.getId()));
    }
}
