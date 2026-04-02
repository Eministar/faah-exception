package dev.eministar.fahsound.settings;

import dev.eministar.fahsound.sound.FaahSoundCatalog;
import dev.eministar.fahsound.sound.FaahSoundEvent;
import dev.eministar.fahsound.visual.FaahVisualCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FaahPresetFileSupportTest {
    @TempDir
    Path tempDir;

    @Test
    void exportsAndImportsRoundTrip() throws IOException {
        FaahSettingsService.StateData state = new FaahSettingsService.StateData();
        state.enabled = false;
        state.volume = 77;
        state.debounceMs = 321;
        state.showNotification = false;
        state.showFailureImage = true;
        state.soundByEvent.put(FaahSoundEvent.BUILD_FAILED.getId(), FaahSoundCatalog.custom("boom.wav"));
        state.visualByEvent.put(FaahSoundEvent.RUN_SUCCESS.getId(), FaahVisualCatalog.custom("happy.gif"));
        state.maxDurationMsByEvent.put(FaahSoundEvent.BUILD_FAILED.getId(), 1400);
        state.visualDurationMsByEvent.put(FaahSoundEvent.RUN_SUCCESS.getId(), 2300);

        Path file = tempDir.resolve("preset.xml");
        FaahPresetFileSupport.exportPreset(file, state);

        FaahSettingsService.StateData imported = FaahPresetFileSupport.importPreset(file);

        assertFalse(imported.enabled);
        assertEquals(77, imported.volume);
        assertEquals(321, imported.debounceMs);
        assertFalse(imported.showNotification);
        assertEquals(FaahSoundCatalog.custom("boom.wav"), imported.soundByEvent.get(FaahSoundEvent.BUILD_FAILED.getId()));
        assertEquals(FaahVisualCatalog.custom("happy.gif"), imported.visualByEvent.get(FaahSoundEvent.RUN_SUCCESS.getId()));
        assertEquals(1400, imported.maxDurationMsByEvent.get(FaahSoundEvent.BUILD_FAILED.getId()));
        assertEquals(2300, imported.visualDurationMsByEvent.get(FaahSoundEvent.RUN_SUCCESS.getId()));
    }
}
