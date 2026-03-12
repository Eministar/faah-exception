package dev.eministar.fahsound.visual;

import com.intellij.openapi.application.PathManager;
import dev.eministar.fahsound.sound.FaahSoundEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class FaahVisualCatalog {
    public static final String SOURCE_NONE = "none";
    public static final String BUNDLED_PREFIX = "bundled:";
    public static final String CUSTOM_PREFIX = "custom:";
    private static final String CUSTOM_FOLDER_NAME = "faah-media";
    private static final List<String> KNOWN_BUNDLED_FILES = List.of(
            "cooked.jpg",
            "happy.gif"
    );

    private FaahVisualCatalog() {
    }

    @NotNull
    public static String bundled(@NotNull String fileName) {
        String normalized = normalizeFileName(fileName);
        return normalized.isEmpty() ? SOURCE_NONE : BUNDLED_PREFIX + normalized;
    }

    @NotNull
    public static String custom(@NotNull String fileName) {
        String normalized = normalizeFileName(fileName);
        return normalized.isEmpty() ? SOURCE_NONE : CUSTOM_PREFIX + normalized;
    }

    @NotNull
    public static String normalizeSourceId(@Nullable String sourceId) {
        if (sourceId == null) {
            return SOURCE_NONE;
        }
        String trimmed = sourceId.trim();
        if (trimmed.isEmpty() || SOURCE_NONE.equalsIgnoreCase(trimmed)) {
            return SOURCE_NONE;
        }
        if (trimmed.startsWith(BUNDLED_PREFIX)) {
            return bundled(trimmed.substring(BUNDLED_PREFIX.length()));
        }
        if (trimmed.startsWith(CUSTOM_PREFIX)) {
            return custom(trimmed.substring(CUSTOM_PREFIX.length()));
        }
        return bundled(trimmed);
    }

    public static boolean isNone(@Nullable String sourceId) {
        return SOURCE_NONE.equals(normalizeSourceId(sourceId));
    }

    public static boolean isCustom(@Nullable String sourceId) {
        return normalizeSourceId(sourceId).startsWith(CUSTOM_PREFIX);
    }

    @NotNull
    public static String extractFileName(@Nullable String sourceId) {
        String normalized = normalizeSourceId(sourceId);
        if (normalized.startsWith(BUNDLED_PREFIX)) {
            return normalized.substring(BUNDLED_PREFIX.length());
        }
        if (normalized.startsWith(CUSTOM_PREFIX)) {
            return normalized.substring(CUSTOM_PREFIX.length());
        }
        return "";
    }

    @NotNull
    public static Path getCustomFolderPath() {
        return Path.of(PathManager.getConfigPath(), CUSTOM_FOLDER_NAME);
    }

    @NotNull
    public static Path ensureCustomFolderExists() throws IOException {
        Path folder = getCustomFolderPath();
        Files.createDirectories(folder);
        return folder;
    }

    @NotNull
    public static List<VisualSourceOption> listAvailableSources() {
        Map<String, VisualSourceOption> options = new LinkedHashMap<>();
        options.put(SOURCE_NONE, new VisualSourceOption(SOURCE_NONE, "No image / GIF"));
        for (String bundledFile : KNOWN_BUNDLED_FILES) {
            String sourceId = bundled(bundledFile);
            if (hasBundledFile(bundledFile)) {
                options.put(sourceId, new VisualSourceOption(sourceId, "Bundled / " + bundledFile));
            }
        }
        Path folder = getCustomFolderPath();
        if (Files.isDirectory(folder)) {
            try (var stream = Files.list(folder)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> isSupportedVisual(path.getFileName().toString()))
                        .sorted()
                        .toList();
                for (Path file : files) {
                    String fileName = file.getFileName().toString();
                    String sourceId = custom(fileName);
                    options.put(sourceId, new VisualSourceOption(sourceId, "Custom / " + fileName));
                }
            } catch (IOException ignored) {
            }
        }
        return new ArrayList<>(options.values());
    }

    @NotNull
    public static List<String> candidateSourceIds(@NotNull FaahSoundEvent event, @Nullable String preferredSourceId) {
        Set<String> ordered = new LinkedHashSet<>();
        String preferred = normalizeSourceId(preferredSourceId);
        if (!isNone(preferred)) {
            ordered.add(preferred);
        }
        String eventDefault = normalizeSourceId(event.getDefaultVisualSourceId());
        if (!isNone(eventDefault)) {
            ordered.add(eventDefault);
            for (String bundledFile : KNOWN_BUNDLED_FILES) {
                String bundledSource = bundled(bundledFile);
                if (!isNone(bundledSource)) {
                    ordered.add(bundledSource);
                }
            }
        }
        return new ArrayList<>(ordered);
    }

    @NotNull
    private static String normalizeFileName(@Nullable String fileName) {
        if (fileName == null) {
            return "";
        }
        String normalized = fileName.trim().replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        return normalized.trim();
    }

    private static boolean hasBundledFile(@NotNull String fileName) {
        try (var stream = FaahVisualCatalog.class.getClassLoader().getResourceAsStream(fileName)) {
            return stream != null;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean isSupportedVisual(@NotNull String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif");
    }

    public record VisualSourceOption(@NotNull String sourceId, @NotNull String displayName) {
        @Override
        public String toString() {
            return displayName;
        }
    }
}
