package dev.eministar.fahsound.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import dev.eministar.fahsound.sound.FaahSoundCatalog;
import dev.eministar.fahsound.sound.FaahSoundEvent;
import dev.eministar.fahsound.visual.FaahVisualCatalog;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

@Service(Service.Level.APP)
@State(name = "FaahSettingsService", storages = @Storage("faah-sound.xml"))
public final class FaahSettingsService implements PersistentStateComponent<FaahSettingsService.StateData> {
    public static final String DEFAULT_SOUND_FILE = "error.mp3";
    private StateData state = new StateData();

    public static final class StateData {
        public boolean enabled = true;
        public int volume = 100;
        public int debounceMs = 2000;
        public String soundFileName = DEFAULT_SOUND_FILE;
        public Map<String, String> soundByEvent = new LinkedHashMap<>();
        public Map<String, String> visualByEvent = new LinkedHashMap<>();
        public Map<String, Integer> maxDurationMsByEvent = new LinkedHashMap<>();
        public boolean showNotification = true;
        public boolean showFailureImage = true;
    }

    public static FaahSettingsService getInstance() {
        return ApplicationManager.getApplication().getService(FaahSettingsService.class);
    }

    @Override
    public StateData getState() {
        normalize(state);
        return state;
    }

    @Override
    public void loadState(@NotNull StateData loadedState) {
        state = loadedState;
        normalize(state);
    }

    public boolean isEnabled() {
        return state.enabled;
    }

    public void setEnabled(boolean enabled) {
        state.enabled = enabled;
    }

    public int getVolume() {
        return state.volume;
    }

    public void setVolume(int volume) {
        state.volume = clamp(volume, 0, 100);
    }

    public int getDebounceMs() {
        return state.debounceMs;
    }

    public void setDebounceMs(int debounceMs) {
        state.debounceMs = Math.max(0, debounceMs);
    }

    @NotNull
    public String getSoundSource(@NotNull FaahSoundEvent event) {
        normalize(state);
        return state.soundByEvent.get(event.getId());
    }

    public void setSoundSource(@NotNull FaahSoundEvent event, @NotNull String sourceId) {
        normalize(state);
        state.soundByEvent.put(event.getId(), FaahSoundCatalog.normalizeSourceId(sourceId));
    }

    @NotNull
    public String getVisualSource(@NotNull FaahSoundEvent event) {
        normalize(state);
        return state.visualByEvent.get(event.getId());
    }

    public void setVisualSource(@NotNull FaahSoundEvent event, @NotNull String sourceId) {
        normalize(state);
        state.visualByEvent.put(event.getId(), FaahVisualCatalog.normalizeSourceId(sourceId));
    }

    public int getMaxDurationMs(@NotNull FaahSoundEvent event) {
        normalize(state);
        Integer value = state.maxDurationMsByEvent.get(event.getId());
        return value == null ? 0 : Math.max(0, value);
    }

    public void setMaxDurationMs(@NotNull FaahSoundEvent event, int maxDurationMs) {
        normalize(state);
        state.maxDurationMsByEvent.put(event.getId(), Math.max(0, maxDurationMs));
    }

    public boolean isShowNotification() {
        return state.showNotification;
    }

    public void setShowNotification(boolean showNotification) {
        state.showNotification = showNotification;
    }

    public boolean isShowVisualOverlay() {
        return state.showFailureImage;
    }

    public void setShowVisualOverlay(boolean showFailureImage) {
        state.showFailureImage = showFailureImage;
    }

    private static void normalize(@NotNull StateData value) {
        value.volume = clamp(value.volume, 0, 100);
        value.debounceMs = Math.max(0, value.debounceMs);
        if (value.soundByEvent == null) {
            value.soundByEvent = new LinkedHashMap<>();
        }
        if (value.visualByEvent == null) {
            value.visualByEvent = new LinkedHashMap<>();
        }
        if (value.maxDurationMsByEvent == null) {
            value.maxDurationMsByEvent = new LinkedHashMap<>();
        }
        Map<String, String> normalizedMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : value.soundByEvent.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank()) {
                normalizedMap.put(entry.getKey(), FaahSoundCatalog.normalizeSourceId(entry.getValue()));
            }
        }
        value.soundByEvent = normalizedMap;
        Map<String, String> normalizedVisualMap = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : value.visualByEvent.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank()) {
                normalizedVisualMap.put(entry.getKey(), FaahVisualCatalog.normalizeSourceId(entry.getValue()));
            }
        }
        value.visualByEvent = normalizedVisualMap;
        Map<String, Integer> normalizedDurationMap = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : value.maxDurationMsByEvent.entrySet()) {
            if (entry.getKey() != null && !entry.getKey().isBlank()) {
                int duration = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
                normalizedDurationMap.put(entry.getKey(), duration);
            }
        }
        value.maxDurationMsByEvent = normalizedDurationMap;
        String legacySound = value.soundFileName == null || value.soundFileName.trim().isEmpty()
                ? DEFAULT_SOUND_FILE
                : value.soundFileName.trim();
        if (value.soundByEvent.isEmpty()) {
            String migratedLegacy = FaahSoundCatalog.normalizeSourceId(legacySound);
            for (FaahSoundEvent event : FaahSoundEvent.orderedValues()) {
                value.soundByEvent.put(event.getId(), migratedLegacy);
            }
        }
        if (value.visualByEvent.isEmpty()) {
            for (FaahSoundEvent event : FaahSoundEvent.orderedValues()) {
                String defaultVisualSource = FaahVisualCatalog.normalizeSourceId(event.getDefaultVisualSourceId());
                if (!value.showFailureImage && !FaahVisualCatalog.isNone(defaultVisualSource)) {
                    defaultVisualSource = FaahVisualCatalog.SOURCE_NONE;
                }
                value.visualByEvent.put(event.getId(), defaultVisualSource);
            }
        }
        for (FaahSoundEvent event : FaahSoundEvent.orderedValues()) {
            value.soundByEvent.putIfAbsent(event.getId(), FaahSoundCatalog.normalizeSourceId(event.getDefaultSourceId()));
            value.visualByEvent.putIfAbsent(event.getId(), FaahVisualCatalog.normalizeSourceId(event.getDefaultVisualSourceId()));
            value.maxDurationMsByEvent.putIfAbsent(event.getId(), 0);
        }
        String buildFailedSource = value.soundByEvent.get(FaahSoundEvent.BUILD_FAILED.getId());
        String extracted = FaahSoundCatalog.extractFileName(buildFailedSource);
        if (extracted.isBlank()) {
            extracted = DEFAULT_SOUND_FILE;
        }
        value.soundFileName = extracted;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
