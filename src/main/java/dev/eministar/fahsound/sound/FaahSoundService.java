package dev.eministar.fahsound.sound;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import dev.eministar.fahsound.settings.FaahSettingsService;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.advanced.AdvancedPlayer;
import javazoom.jl.player.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service(Service.Level.APP)
public final class FaahSoundService implements Disposable {
    private static final Logger LOG = Logger.getInstance(FaahSoundService.class);
    private static final String NOTIFICATION_GROUP_ID = "faah.sound.notifications";
    private final ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("FAH-Sound-Playback", 1);
    private final AtomicLong lastPlaybackMillis = new AtomicLong(0L);

    public static FaahSoundService getInstance() {
        return ApplicationManager.getApplication().getService(FaahSoundService.class);
    }

    public void playEvent(@Nullable Project project, @NotNull FaahSoundEvent event, @NotNull String source) {
        FaahSettingsService settings = FaahSettingsService.getInstance();
        if (!settings.isEnabled()) {
            return;
        }
        if (!acquireDebounce(settings.getDebounceMs())) {
            return;
        }
        String selectedSource = settings.getSoundSource(event);
        if (FaahSoundCatalog.isNone(selectedSource)) {
            return;
        }
        int maxDurationMs = settings.getMaxDurationMs(event);
        playAsync(event, selectedSource, settings.getVolume(), maxDurationMs);
        if (settings.isShowNotification()) {
            notifyEvent(project, event, source);
        }
    }

    public void playTestSound(@NotNull String sourceId, int maxDurationMs) {
        FaahSettingsService settings = FaahSettingsService.getInstance();
        if (FaahSoundCatalog.isNone(sourceId)) {
            return;
        }
        playAsync(FaahSoundEvent.BUILD_FAILED, sourceId, settings.getVolume(), Math.max(0, maxDurationMs));
    }

    private boolean acquireDebounce(int debounceMs) {
        long now = System.currentTimeMillis();
        while (true) {
            long previous = lastPlaybackMillis.get();
            if (now - previous < debounceMs) {
                return false;
            }
            if (lastPlaybackMillis.compareAndSet(previous, now)) {
                return true;
            }
        }
    }

    private void notifyEvent(@Nullable Project project, @NotNull FaahSoundEvent event, @NotNull String source) {
        try {
            NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID);
            NotificationType type = event.isSuccessEvent() ? NotificationType.INFORMATION : NotificationType.WARNING;
            var notification = group.createNotification(
                    "FAH " + event.getDisplayName(),
                    source,
                    type
            );
            notification.notify(project);
        } catch (Throwable t) {
            LOG.warn("Unable to show FAH notification", t);
        }
    }

    private void playAsync(@NotNull FaahSoundEvent event, @NotNull String configuredSource, int volume, int maxDurationMs) {
        executor.execute(() -> {
            try {
                SoundResource resource = resolveResource(event, configuredSource);
                if (resource == null) {
                    return;
                }
                playResource(resource.fileName(), resource.bytes(), volume, maxDurationMs);
            } catch (Throwable t) {
                LOG.warn("Unable to play failure sound", t);
            }
        });
    }

    @Nullable
    private SoundResource resolveResource(@NotNull FaahSoundEvent event, @NotNull String configuredSource) throws IOException {
        for (String candidateSource : FaahSoundCatalog.candidateSourceIds(event, configuredSource)) {
            SoundResource resource = loadSource(candidateSource);
            if (resource != null) {
                return resource;
            }
        }
        return null;
    }

    @Nullable
    private SoundResource loadSource(@NotNull String sourceId) throws IOException {
        String normalizedSource = FaahSoundCatalog.normalizeSourceId(sourceId);
        if (FaahSoundCatalog.isNone(normalizedSource)) {
            return null;
        }
        String fileName = FaahSoundCatalog.extractFileName(normalizedSource);
        if (fileName.isBlank()) {
            return null;
        }
        if (FaahSoundCatalog.isCustom(normalizedSource)) {
            Path customFile = FaahSoundCatalog.getCustomFolderPath().resolve(fileName);
            if (!Files.isRegularFile(customFile)) {
                return null;
            }
            return new SoundResource(fileName, Files.readAllBytes(customFile));
        }
        try (InputStream inputStream = FaahSoundService.class.getClassLoader().getResourceAsStream(fileName)) {
            if (inputStream == null) {
                return null;
            }
            return new SoundResource(fileName, inputStream.readAllBytes());
        }
    }

    private void playResource(@NotNull String resourceName, byte[] bytes, int volume, int maxDurationMs) throws Exception {
        String lower = resourceName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".wav")) {
            playWav(bytes, volume, maxDurationMs);
            return;
        }
        if (lower.endsWith(".mp3")) {
            playMp3(bytes, volume, maxDurationMs);
            return;
        }
        try {
            playMp3(bytes, volume, maxDurationMs);
        } catch (Exception first) {
            playWav(bytes, volume, maxDurationMs);
        }
    }

    private void playMp3(byte[] bytes, int volume, int maxDurationMs) throws IOException, JavaLayerException {
        if (volume <= 0) {
            return;
        }
        try (BufferedInputStream input = new BufferedInputStream(new ByteArrayInputStream(bytes))) {
            if (maxDurationMs <= 0) {
                new Player(input).play();
                return;
            }
            AdvancedPlayer advancedPlayer = new AdvancedPlayer(input);
            ScheduledFuture<?> stopFuture = AppExecutorUtil.getAppScheduledExecutorService()
                    .schedule(advancedPlayer::stop, maxDurationMs, TimeUnit.MILLISECONDS);
            try {
                advancedPlayer.play();
            } finally {
                stopFuture.cancel(false);
                advancedPlayer.close();
            }
        }
    }

    private void playWav(byte[] bytes, int volume, int maxDurationMs) throws Exception {
        if (volume <= 0) {
            return;
        }
        Clip clip = AudioSystem.getClip();
        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new BufferedInputStream(new ByteArrayInputStream(bytes)))) {
            clip.open(audioInputStream);
            applyVolume(clip, volume);
            long fullLengthMs = Math.max(1000L, clip.getMicrosecondLength() / 1000L + 500L);
            long waitMillis = maxDurationMs > 0 ? Math.min(fullLengthMs, maxDurationMs) : fullLengthMs;
            LineListener listener = event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    synchronized (clip) {
                        clip.notifyAll();
                    }
                }
            };
            clip.addLineListener(listener);
            clip.start();
            synchronized (clip) {
                clip.wait(waitMillis);
            }
            clip.removeLineListener(listener);
        } finally {
            clip.stop();
            clip.close();
        }
    }

    private void applyVolume(@NotNull Clip clip, int volume) {
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float min = control.getMinimum();
                float max = control.getMaximum();
                float value = min + (max - min) * (volume / 100.0f);
                control.setValue(Math.max(min, Math.min(max, value)));
            }
        } catch (Throwable t) {
            LOG.debug("Unable to set WAV volume", t);
        }
    }

    @Override
    public void dispose() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record SoundResource(String fileName, byte[] bytes) {
    }
}
