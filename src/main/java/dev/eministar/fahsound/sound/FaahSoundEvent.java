package dev.eministar.fahsound.sound;

import org.jetbrains.annotations.NotNull;

public enum FaahSoundEvent {
    BUILD_FAILED("build_failed", "Build failed", FaahSoundCatalog.bundled("error.mp3")),
    BUILD_WARNING("build_warning", "Build warning", FaahSoundCatalog.bundled("error.mp3")),
    BUILD_SUCCESS("build_success", "Build succeeded", FaahSoundCatalog.bundled("heavenly.mp3")),
    RUN_FAILED("run_failed", "Run failed", FaahSoundCatalog.bundled("error.mp3")),
    RUN_SUCCESS("run_success", "Run succeeded", FaahSoundCatalog.SOURCE_NONE),
    GRADLE_FAILED("gradle_failed", "Gradle failed", FaahSoundCatalog.bundled("error.mp3")),
    MAVEN_FAILED("maven_failed", "Maven failed", FaahSoundCatalog.bundled("error.mp3")),
    TERMINAL_FAILED("terminal_failed", "Terminal command failed", FaahSoundCatalog.bundled("error.mp3")),
    TERMINAL_SUCCESS("terminal_success", "Terminal command succeeded", FaahSoundCatalog.SOURCE_NONE);

    private final String id;
    private final String displayName;
    private final String defaultSourceId;

    FaahSoundEvent(@NotNull String id, @NotNull String displayName, @NotNull String defaultSourceId) {
        this.id = id;
        this.displayName = displayName;
        this.defaultSourceId = defaultSourceId;
    }

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    @NotNull
    public String getDefaultSourceId() {
        return defaultSourceId;
    }

    public boolean isSuccessEvent() {
        return this == BUILD_SUCCESS || this == RUN_SUCCESS || this == TERMINAL_SUCCESS;
    }

    public boolean isWarningEvent() {
        return this == BUILD_WARNING;
    }

    @NotNull
    public static FaahSoundEvent[] orderedValues() {
        return values();
    }
}
