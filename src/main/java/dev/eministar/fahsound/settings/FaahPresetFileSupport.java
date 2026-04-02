package dev.eministar.fahsound.settings;

import dev.eministar.fahsound.sound.FaahSoundEvent;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class FaahPresetFileSupport {
    private static final String FORMAT_VERSION_KEY = "formatVersion";
    private static final String FORMAT_VERSION = "1";

    private FaahPresetFileSupport() {
    }

    public static void exportPreset(@NotNull Path file, @NotNull FaahSettingsService.StateData state) throws IOException {
        Properties properties = new Properties();
        properties.setProperty(FORMAT_VERSION_KEY, FORMAT_VERSION);
        properties.setProperty("enabled", Boolean.toString(state.enabled));
        properties.setProperty("volume", Integer.toString(state.volume));
        properties.setProperty("debounceMs", Integer.toString(state.debounceMs));
        properties.setProperty("showNotification", Boolean.toString(state.showNotification));
        properties.setProperty("showVisualOverlay", Boolean.toString(state.showFailureImage));

        for (FaahSoundEvent event : FaahSoundEvent.orderedValues()) {
            String eventId = event.getId();
            String soundSource = state.soundByEvent.get(eventId);
            if (soundSource != null) {
                properties.setProperty("sound." + eventId, soundSource);
            }
            String visualSource = state.visualByEvent.get(eventId);
            if (visualSource != null) {
                properties.setProperty("visual." + eventId, visualSource);
            }
            Integer soundDuration = state.maxDurationMsByEvent.get(eventId);
            if (soundDuration != null) {
                properties.setProperty("duration.sound." + eventId, Integer.toString(Math.max(0, soundDuration)));
            }
            Integer visualDuration = state.visualDurationMsByEvent.get(eventId);
            if (visualDuration != null) {
                properties.setProperty("duration.visual." + eventId, Integer.toString(Math.max(0, visualDuration)));
            }
        }

        try (OutputStream outputStream = Files.newOutputStream(file)) {
            properties.storeToXML(outputStream, "FAH Failure Sound preset");
        }
    }

    @NotNull
    public static FaahSettingsService.StateData importPreset(@NotNull Path file) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(file)) {
            properties.loadFromXML(inputStream);
        }

        FaahSettingsService.StateData state = new FaahSettingsService.StateData();
        state.enabled = parseBoolean(properties, "enabled", state.enabled);
        state.volume = parseInt(properties, "volume", state.volume);
        state.debounceMs = parseInt(properties, "debounceMs", state.debounceMs);
        state.showNotification = parseBoolean(properties, "showNotification", state.showNotification);
        state.showFailureImage = parseBoolean(properties, "showVisualOverlay", state.showFailureImage);

        for (FaahSoundEvent event : FaahSoundEvent.orderedValues()) {
            String eventId = event.getId();
            String soundSource = properties.getProperty("sound." + eventId);
            if (soundSource != null) {
                state.soundByEvent.put(eventId, soundSource);
            }
            String visualSource = properties.getProperty("visual." + eventId);
            if (visualSource != null) {
                state.visualByEvent.put(eventId, visualSource);
            }
            state.maxDurationMsByEvent.put(eventId, parseInt(properties, "duration.sound." + eventId, 0));
            state.visualDurationMsByEvent.put(eventId, parseInt(properties, "duration.visual." + eventId, 0));
        }
        return state;
    }

    private static boolean parseBoolean(@NotNull Properties properties, @NotNull String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }

    private static int parseInt(@NotNull Properties properties, @NotNull String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
