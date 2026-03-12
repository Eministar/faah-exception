package dev.eministar.fahsound.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.concurrency.AppExecutorUtil;
import dev.eministar.fahsound.sound.FaahSoundEvent;
import dev.eministar.fahsound.visual.FaahVisualCatalog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Image;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service(Service.Level.APP)
public final class FaahImageOverlayService implements Disposable {
    private static final Logger LOG = Logger.getInstance(FaahImageOverlayService.class);
    private static final int DEFAULT_DISPLAY_MS = 2200;
    private static final int MIN_DISPLAY_MS = 1200;
    private static final int MAX_DISPLAY_MS = 6000;
    private static final int MAX_IMAGE_WIDTH = 520;
    private static final int MAX_IMAGE_HEIGHT = 360;

    private final Object lock = new Object();
    private final AtomicLong requestSequence = new AtomicLong();
    private @Nullable JWindow currentWindow;
    private @Nullable ScheduledFuture<?> hideFuture;

    public static FaahImageOverlayService getInstance() {
        return ApplicationManager.getApplication().getService(FaahImageOverlayService.class);
    }

    public void showVisual(@Nullable Project project,
                           @NotNull FaahSoundEvent event,
                           @Nullable String configuredSource,
                           int suggestedDurationMs) {
        if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
            return;
        }
        int displayMs = normalizeDisplayDuration(suggestedDurationMs);
        long requestId = requestSequence.incrementAndGet();
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                VisualResource resource = resolveResource(event, configuredSource);
                if (resource == null) {
                    return;
                }
                ImageIcon icon = createIcon(resource.fileName(), resource.bytes());
                if (icon == null) {
                    return;
                }
                ApplicationManager.getApplication().invokeLater(() -> showWindow(project, icon, displayMs, requestId));
            } catch (IOException e) {
                LOG.warn("Unable to load overlay media", e);
            }
        });
    }

    private void showWindow(@Nullable Project project, @NotNull ImageIcon icon, int displayMs, long requestId) {
        if (requestId != requestSequence.get()) {
            return;
        }

        disposeCurrentWindow();

        Window owner = resolveOwner(project);
        JWindow window = owner == null ? new JWindow() : new JWindow(owner);
        window.setFocusableWindowState(false);
        window.setAlwaysOnTop(false);
        window.setBackground(new Color(0, 0, 0, 0));
        window.setContentPane(createContent(icon));
        window.pack();
        positionWindow(window, owner);
        window.setVisible(true);

        ScheduledFuture<?> future = AppExecutorUtil.getAppScheduledExecutorService().schedule(
                () -> SwingUtilities.invokeLater(() -> hideWindow(window)),
                displayMs,
                TimeUnit.MILLISECONDS
        );

        synchronized (lock) {
            currentWindow = window;
            hideFuture = future;
        }
    }

    private void hideWindow(@NotNull JWindow window) {
        synchronized (lock) {
            if (currentWindow == window) {
                disposeCurrentWindow();
                return;
            }
        }
        if (window.isDisplayable()) {
            window.dispose();
        }
    }

    private void disposeCurrentWindow() {
        ScheduledFuture<?> future;
        JWindow window;
        synchronized (lock) {
            future = hideFuture;
            hideFuture = null;
            window = currentWindow;
            currentWindow = null;
        }
        if (future != null) {
            future.cancel(false);
        }
        if (window != null && window.isDisplayable()) {
            window.setVisible(false);
            window.dispose();
        }
    }

    @NotNull
    private JComponent createContent(@NotNull ImageIcon icon) {
        JLabel label = new JLabel(icon);
        label.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(true);
        panel.setBackground(new Color(24, 24, 24));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 180), 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    @Nullable
    private Window resolveOwner(@Nullable Project project) {
        if (project != null) {
            Window suggested = WindowManager.getInstance().suggestParentWindow(project);
            if (suggested != null) {
                return suggested;
            }
        }
        Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (activeWindow != null) {
            return activeWindow;
        }
        for (Window window : Window.getWindows()) {
            if (window.isShowing()) {
                return window;
            }
        }
        return null;
    }

    private void positionWindow(@NotNull JWindow window, @Nullable Window owner) {
        if (owner == null) {
            window.setLocationRelativeTo(null);
            return;
        }
        int x = owner.getX() + Math.max(0, (owner.getWidth() - window.getWidth()) / 2);
        int y = owner.getY() + Math.max(0, (owner.getHeight() - window.getHeight()) / 2);
        window.setLocation(x, y);
    }

    @Nullable
    private VisualResource resolveResource(@NotNull FaahSoundEvent event, @Nullable String configuredSource) throws IOException {
        for (String candidateSource : FaahVisualCatalog.candidateSourceIds(event, configuredSource)) {
            VisualResource resource = loadSource(candidateSource);
            if (resource != null) {
                return resource;
            }
        }
        return null;
    }

    @Nullable
    private VisualResource loadSource(@NotNull String sourceId) throws IOException {
        String normalizedSource = FaahVisualCatalog.normalizeSourceId(sourceId);
        if (FaahVisualCatalog.isNone(normalizedSource)) {
            return null;
        }
        String fileName = FaahVisualCatalog.extractFileName(normalizedSource);
        if (fileName.isBlank()) {
            return null;
        }
        if (FaahVisualCatalog.isCustom(normalizedSource)) {
            Path customFile = FaahVisualCatalog.getCustomFolderPath().resolve(fileName);
            if (!Files.isRegularFile(customFile)) {
                return null;
            }
            return new VisualResource(fileName, Files.readAllBytes(customFile));
        }
        try (InputStream inputStream = FaahImageOverlayService.class.getClassLoader().getResourceAsStream(fileName)) {
            if (inputStream == null) {
                return null;
            }
            return new VisualResource(fileName, inputStream.readAllBytes());
        }
    }

    @Nullable
    private ImageIcon createIcon(@NotNull String fileName, byte[] bytes) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".gif")) {
            return scaleIconIfNeeded(new ImageIcon(bytes));
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                LOG.warn("Unable to decode overlay media resource: " + fileName);
                return null;
            }
            return scaleIconIfNeeded(new ImageIcon(image));
        } catch (IOException e) {
            LOG.warn("Unable to load overlay media resource", e);
            return null;
        }
    }

    @NotNull
    private ImageIcon scaleIconIfNeeded(@NotNull ImageIcon icon) {
        int width = icon.getIconWidth();
        int height = icon.getIconHeight();
        if (width <= MAX_IMAGE_WIDTH && height <= MAX_IMAGE_HEIGHT) {
            return icon;
        }
        if (width <= 0 || height <= 0) {
            return icon;
        }
        double scale = Math.min((double) MAX_IMAGE_WIDTH / width, (double) MAX_IMAGE_HEIGHT / height);
        int scaledWidth = Math.max(1, (int) Math.round(width * scale));
        int scaledHeight = Math.max(1, (int) Math.round(height * scale));
        Image scaled = icon.getImage().getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private int normalizeDisplayDuration(int suggestedDurationMs) {
        if (suggestedDurationMs <= 0) {
            return DEFAULT_DISPLAY_MS;
        }
        return Math.max(MIN_DISPLAY_MS, Math.min(MAX_DISPLAY_MS, suggestedDurationMs));
    }

    @Override
    public void dispose() {
        if (SwingUtilities.isEventDispatchThread()) {
            disposeCurrentWindow();
            return;
        }
        SwingUtilities.invokeLater(this::disposeCurrentWindow);
    }

    private record VisualResource(@NotNull String fileName, byte[] bytes) {
    }
}
