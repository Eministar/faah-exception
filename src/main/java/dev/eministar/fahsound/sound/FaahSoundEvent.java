package dev.eministar.fahsound.sound;

import dev.eministar.fahsound.visual.FaahVisualCatalog;
import org.jetbrains.annotations.NotNull;

public enum FaahSoundEvent {
    BUILD_FAILED("build_failed", "Build failed", FaahSoundCatalog.bundled("error.mp3"), FaahVisualCatalog.bundled("cooked.jpg")),
    BUILD_WARNING("build_warning", "Build warning", FaahSoundCatalog.bundled("error.mp3"), FaahVisualCatalog.SOURCE_NONE),
    BUILD_SUCCESS("build_success", "Build succeeded", FaahSoundCatalog.bundled("succeed.mp3"), FaahVisualCatalog.SOURCE_NONE),
    RUN_FAILED("run_failed", "Run failed", FaahSoundCatalog.bundled("error.mp3"), FaahVisualCatalog.bundled("cooked.jpg")),
    RUN_SUCCESS("run_success", "Run succeeded", FaahSoundCatalog.SOURCE_NONE, FaahVisualCatalog.SOURCE_NONE),
    GRADLE_FAILED("gradle_failed", "Gradle failed", FaahSoundCatalog.bundled("error.mp3"), FaahVisualCatalog.bundled("cooked.jpg")),
    MAVEN_FAILED("maven_failed", "Maven failed", FaahSoundCatalog.bundled("error.mp3"), FaahVisualCatalog.bundled("cooked.jpg"));

    private final String id;
    private final String displayName;
    private final String defaultSourceId;
    private final String defaultVisualSourceId;

    FaahSoundEvent(@NotNull String id,
                   @NotNull String displayName,
                   @NotNull String defaultSourceId,
                   @NotNull String defaultVisualSourceId) {
        this.id = id;
        this.displayName = displayName;
        this.defaultSourceId = defaultSourceId;
        this.defaultVisualSourceId = defaultVisualSourceId;
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

    @NotNull
    public String getDefaultVisualSourceId() {
        return defaultVisualSourceId;
    }

    @NotNull
    public String getDebounceScopeId() {
        return id;
    }

    public int getPlaybackPriority() {
        return switch (this) {
            case BUILD_FAILED, RUN_FAILED, GRADLE_FAILED, MAVEN_FAILED -> 300;
            case BUILD_WARNING -> 200;
            case BUILD_SUCCESS, RUN_SUCCESS -> 100;
        };
    }

    public boolean isSuccessEvent() {
        return this == BUILD_SUCCESS || this == RUN_SUCCESS;
    }

    public boolean isWarningEvent() {
        return this == BUILD_WARNING;
    }

    public boolean isFailureEvent() {
        return this == BUILD_FAILED
                || this == RUN_FAILED
                || this == GRADLE_FAILED
                || this == MAVEN_FAILED;
    }

    public boolean isBuildRelatedEvent() {
        return this == BUILD_FAILED
                || this == BUILD_WARNING
                || this == BUILD_SUCCESS
                || this == GRADLE_FAILED
                || this == MAVEN_FAILED;
    }

    public boolean isRunRelatedEvent() {
        return this == RUN_FAILED || this == RUN_SUCCESS;
    }

    @NotNull
    public static FaahSoundEvent[] orderedValues() {
        return values();
    }
}
