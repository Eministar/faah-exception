package dev.eministar.fahsound.sound;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import dev.eministar.fahsound.settings.FaahSettingsService;
import dev.eministar.fahsound.ui.FaahImageOverlayService;
import dev.eministar.fahsound.visual.FaahVisualCatalog;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.JavaSoundAudioDevice;
import javazoom.jl.player.advanced.AdvancedPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service(Service.Level.APP)
public final class FaahSoundService implements Disposable {
    private static final Logger LOG = Logger.getInstance(FaahSoundService.class);
    private static final String NOTIFICATION_GROUP_ID = "faah.sound.notifications";

    private final ThreadPoolExecutor playbackExecutor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new PriorityBlockingQueue<>(),
            new NamedThreadFactory()
    );
    private final FaahEventDebouncer debouncer = new FaahEventDebouncer();
    private final AtomicLong playbackSequence = new AtomicLong();
    private final AtomicReference<RunningPlayback> currentPlayback = new AtomicReference<>();

    public static FaahSoundService getInstance() {
        return ApplicationManager.getApplication().getService(FaahSoundService.class);
    }

    public void playEvent(@Nullable Project project, @NotNull FaahSoundEvent event, @NotNull String source) {
        FaahSettingsService settings = FaahSettingsService.getInstance();
        if (!settings.isEnabled()) {
            return;
        }

        String selectedSoundSource = settings.getSoundSource(event);
        String selectedVisualSource = settings.getVisualSource(event);
        boolean shouldPlaySound = settings.getVolume() > 0 && !FaahSoundCatalog.isNone(selectedSoundSource);
        boolean shouldShowVisual = settings.isShowVisualOverlay() && !FaahVisualCatalog.isNone(selectedVisualSource);
        boolean shouldNotify = settings.isShowNotification();
        if (!shouldPlaySound && !shouldShowVisual && !shouldNotify) {
            return;
        }
        if (!debouncer.tryAcquire(event.getDebounceScopeId(), settings.getDebounceMs())) {
            return;
        }

        dispatchEvent(
                project,
                event,
                source,
                selectedSoundSource,
                selectedVisualSource,
                settings.getVolume(),
                settings.getMaxDurationMs(event),
                settings.getVisualDurationMs(event),
                shouldPlaySound,
                shouldShowVisual,
                shouldNotify
        );
    }

    public void previewEvent(@NotNull FaahSoundEvent event,
                             @NotNull String soundSourceId,
                             @NotNull String visualSourceId,
                             int soundDurationMs,
                             int visualDurationMs) {
        FaahSettingsService settings = FaahSettingsService.getInstance();
        boolean shouldPlaySound = settings.getVolume() > 0 && !FaahSoundCatalog.isNone(soundSourceId);
        boolean shouldShowVisual = !FaahVisualCatalog.isNone(visualSourceId);
        if (shouldPlaySound) {
            submitPlayback(event, soundSourceId, settings.getVolume(), soundDurationMs);
        }
        if (shouldShowVisual) {
            FaahImageOverlayService.getInstance().showVisual(null, event, visualSourceId, visualDurationMs);
        }
    }

    private void dispatchEvent(@Nullable Project project,
                               @NotNull FaahSoundEvent event,
                               @NotNull String source,
                               @NotNull String soundSourceId,
                               @NotNull String visualSourceId,
                               int volume,
                               int soundDurationMs,
                               int visualDurationMs,
                               boolean shouldPlaySound,
                               boolean shouldShowVisual,
                               boolean shouldNotify) {
        if (shouldNotify) {
            notifyEvent(project, event, source);
        }
        if (shouldShowVisual) {
            FaahImageOverlayService.getInstance().showVisual(project, event, visualSourceId, visualDurationMs);
        }
        if (shouldPlaySound) {
            submitPlayback(event, soundSourceId, volume, soundDurationMs);
        }
    }

    private void submitPlayback(@NotNull FaahSoundEvent event,
                                @NotNull String configuredSource,
                                int volume,
                                int maxDurationMs) {
        PlaybackTask task = new PlaybackTask(
                playbackSequence.incrementAndGet(),
                event,
                configuredSource,
                Math.max(0, volume),
                Math.max(0, maxDurationMs)
        );

        RunningPlayback running = currentPlayback.get();
        if (running != null && shouldPreempt(running.task(), task)) {
            running.stop();
        }
        discardQueuedTasks(task);

        try {
            playbackExecutor.execute(task);
        } catch (RuntimeException e) {
            LOG.warn("Unable to queue sound playback", e);
        }
    }

    private boolean shouldPreempt(@NotNull PlaybackTask runningTask, @NotNull PlaybackTask incomingTask) {
        if (incomingTask.priority() > runningTask.priority()) {
            return true;
        }
        return incomingTask.event().isFailureEvent() && incomingTask.priority() == runningTask.priority();
    }

    private void discardQueuedTasks(@NotNull PlaybackTask incomingTask) {
        playbackExecutor.getQueue().removeIf(candidate ->
                candidate instanceof PlaybackTask queuedTask && shouldDiscardQueuedTask(queuedTask, incomingTask)
        );
    }

    private boolean shouldDiscardQueuedTask(@NotNull PlaybackTask queuedTask, @NotNull PlaybackTask incomingTask) {
        if (queuedTask.priority() < incomingTask.priority()) {
            return true;
        }
        return incomingTask.event().isFailureEvent() && queuedTask.priority() == incomingTask.priority();
    }

    private void notifyEvent(@Nullable Project project, @NotNull FaahSoundEvent event, @NotNull String source) {
        try {
            NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID);
            Notification notification = group.createNotification(
                    "FAH " + event.getDisplayName(),
                    source,
                    notificationType(event)
            );
            addNotificationActions(project, notification, event);
            notification.notify(project);
        } catch (Throwable t) {
            LOG.warn("Unable to show FAH notification", t);
        }
    }

    @NotNull
    private NotificationType notificationType(@NotNull FaahSoundEvent event) {
        if (event.isFailureEvent()) {
            return NotificationType.ERROR;
        }
        if (event.isWarningEvent()) {
            return NotificationType.WARNING;
        }
        return NotificationType.INFORMATION;
    }

    private void addNotificationActions(@Nullable Project project,
                                        @NotNull Notification notification,
                                        @NotNull FaahSoundEvent event) {
        if (project == null || project.isDisposed()) {
            return;
        }
        if (event.isBuildRelatedEvent()) {
            notification.addAction(createToolWindowAction(project, "Open Build", "Build"));
        }
        if (event.isRunRelatedEvent()) {
            notification.addAction(createToolWindowAction(project, "Open Run", "Run"));
        }
        if (event.isFailureEvent() || event.isWarningEvent()) {
            notification.addAction(createToolWindowAction(project, "Open Problems", "Problems"));
        }
    }

    @NotNull
    private NotificationAction createToolWindowAction(@NotNull Project project,
                                                      @NotNull String actionText,
                                                      @NotNull String toolWindowId) {
        return new NotificationAction(actionText) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
                activateToolWindow(project, toolWindowId);
                notification.expire();
            }
        };
    }

    private void activateToolWindow(@NotNull Project project, @NotNull String toolWindowId) {
        try {
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId);
            if (toolWindow != null) {
                toolWindow.activate(null, true);
            }
        } catch (Throwable t) {
            LOG.debug("Unable to activate tool window " + toolWindowId, t);
        }
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

    private void playResource(@NotNull String resourceName,
                              byte[] bytes,
                              int volume,
                              int maxDurationMs,
                              @NotNull StopToken stopToken) throws Exception {
        String lower = resourceName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".wav")) {
            playWav(bytes, volume, maxDurationMs, stopToken);
            return;
        }
        if (lower.endsWith(".mp3")) {
            playMp3(bytes, volume, maxDurationMs, stopToken);
            return;
        }
        try {
            playMp3(bytes, volume, maxDurationMs, stopToken);
        } catch (Exception first) {
            playWav(bytes, volume, maxDurationMs, stopToken);
        }
    }

    private void playMp3(byte[] bytes,
                         int volume,
                         int maxDurationMs,
                         @NotNull StopToken stopToken) throws IOException, JavaLayerException {
        if (volume <= 0) {
            return;
        }
        try (BufferedInputStream input = new BufferedInputStream(new ByteArrayInputStream(bytes))) {
            AdvancedPlayer player = new AdvancedPlayer(input, new VolumeAwareMp3AudioDevice(volume));
            stopToken.attach(player::stop);
            if (stopToken.isStopRequested()) {
                player.close();
                return;
            }
            ScheduledFuture<?> stopFuture = scheduleStop(stopToken, maxDurationMs);
            try {
                player.play();
            } finally {
                if (stopFuture != null) {
                    stopFuture.cancel(false);
                }
                player.close();
            }
        }
    }

    private void playWav(byte[] bytes,
                         int volume,
                         int maxDurationMs,
                         @NotNull StopToken stopToken) throws Exception {
        if (volume <= 0) {
            return;
        }

        Clip clip = AudioSystem.getClip();
        try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(
                new BufferedInputStream(new ByteArrayInputStream(bytes)))) {
            clip.open(audioInputStream);
            applyVolume(clip, volume);
            stopToken.attach(clip::stop);
            if (stopToken.isStopRequested()) {
                return;
            }

            Object waitLock = new Object();
            LineListener listener = event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    synchronized (waitLock) {
                        waitLock.notifyAll();
                    }
                }
            };
            clip.addLineListener(listener);
            ScheduledFuture<?> stopFuture = scheduleStop(stopToken, maxDurationMs);
            try {
                clip.start();
                synchronized (waitLock) {
                    while (clip.isOpen() && clip.isRunning()) {
                        waitLock.wait(250L);
                    }
                }
            } finally {
                clip.removeLineListener(listener);
                if (stopFuture != null) {
                    stopFuture.cancel(false);
                }
            }
        } finally {
            clip.stop();
            clip.close();
        }
    }

    @Nullable
    private ScheduledFuture<?> scheduleStop(@NotNull StopToken stopToken, int maxDurationMs) {
        if (maxDurationMs <= 0) {
            return null;
        }
        return AppExecutorUtil.getAppScheduledExecutorService()
                .schedule(stopToken::requestStop, maxDurationMs, TimeUnit.MILLISECONDS);
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
        RunningPlayback running = currentPlayback.getAndSet(null);
        if (running != null) {
            running.stop();
        }
        playbackExecutor.shutdownNow();
        try {
            playbackExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private final class PlaybackTask implements Runnable, Comparable<PlaybackTask> {
        private final long sequence;
        private final FaahSoundEvent event;
        private final String sourceId;
        private final int volume;
        private final int maxDurationMs;

        private PlaybackTask(long sequence,
                             @NotNull FaahSoundEvent event,
                             @NotNull String sourceId,
                             int volume,
                             int maxDurationMs) {
            this.sequence = sequence;
            this.event = event;
            this.sourceId = sourceId;
            this.volume = volume;
            this.maxDurationMs = maxDurationMs;
        }

        @Override
        public void run() {
            StopToken stopToken = new StopToken();
            RunningPlayback runningPlayback = new RunningPlayback(this, stopToken);
            currentPlayback.set(runningPlayback);
            try {
                SoundResource resource = resolveResource(event, sourceId);
                if (resource == null || stopToken.isStopRequested()) {
                    return;
                }
                playResource(resource.fileName(), resource.bytes(), volume, maxDurationMs, stopToken);
            } catch (Throwable t) {
                LOG.warn("Unable to play sound for event " + event.getId(), t);
            } finally {
                currentPlayback.compareAndSet(runningPlayback, null);
            }
        }

        @Override
        public int compareTo(@NotNull PlaybackTask other) {
            int priorityCompare = Integer.compare(other.priority(), priority());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Long.compare(sequence, other.sequence);
        }

        private int priority() {
            return event.getPlaybackPriority();
        }

        @NotNull
        private FaahSoundEvent event() {
            return event;
        }
    }

    private record RunningPlayback(@NotNull PlaybackTask task, @NotNull StopToken stopToken) {
        private void stop() {
            stopToken.requestStop();
        }
    }

    private static final class StopToken {
        private final AtomicBoolean stopRequested = new AtomicBoolean();
        private final AtomicReference<Runnable> stopAction = new AtomicReference<>();

        private void attach(@NotNull Runnable action) {
            if (stopAction.compareAndSet(null, action) && stopRequested.get()) {
                action.run();
            }
        }

        private void requestStop() {
            stopRequested.set(true);
            Runnable action = stopAction.get();
            if (action != null) {
                action.run();
            }
        }

        private boolean isStopRequested() {
            return stopRequested.get();
        }
    }

    private record SoundResource(@NotNull String fileName, byte[] bytes) {
    }

    private static final class VolumeAwareMp3AudioDevice extends JavaSoundAudioDevice {
        private static final Field SOURCE_FIELD = resolveSourceField();

        private final int volume;

        private VolumeAwareMp3AudioDevice(int volume) {
            this.volume = Math.max(0, Math.min(100, volume));
        }

        @Override
        protected void createSource() throws JavaLayerException {
            super.createSource();
            applyVolume();
        }

        private void applyVolume() {
            if (SOURCE_FIELD == null) {
                return;
            }
            try {
                Object source = SOURCE_FIELD.get(this);
                if (source instanceof SourceDataLine line && line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl control = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    float min = control.getMinimum();
                    float max = control.getMaximum();
                    float value = min + (max - min) * (volume / 100.0f);
                    control.setValue(Math.max(min, Math.min(max, value)));
                }
            } catch (IllegalAccessException e) {
                LOG.debug("Unable to set MP3 volume", e);
            }
        }

        @Nullable
        private static Field resolveSourceField() {
            try {
                Field field = JavaSoundAudioDevice.class.getDeclaredField("source");
                field.setAccessible(true);
                return field;
            } catch (ReflectiveOperationException e) {
                LOG.debug("Unable to access MP3 source line", e);
                return null;
            }
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(@NotNull Runnable runnable) {
            Thread thread = new Thread(runnable, "FAH-Sound-Playback");
            thread.setDaemon(true);
            return thread;
        }
    }
}
