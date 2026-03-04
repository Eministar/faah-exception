package dev.eministar.fahsound.listeners;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.util.Alarm;
import dev.eministar.fahsound.sound.FaahSoundService;
import dev.eministar.fahsound.sound.FaahSoundEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.terminal.block.session.BlockTerminalSession;
import org.jetbrains.plugins.terminal.block.session.CommandFinishedEvent;
import org.jetbrains.plugins.terminal.block.session.ShellCommandListener;

import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayDeque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FaahTerminalFailureWatcher implements Disposable {
    private static final String TERMINAL_TOOL_WINDOW_ID = "Terminal";
    private static final int INITIAL_SCAN_DELAY_MS = 800;
    private static final int SCAN_INTERVAL_MS = 2000;
    private static final int MAX_COMPONENT_SCAN = 1200;

    private final Project project;
    private final Alarm alarm;
    private final Set<BlockTerminalSession> attachedSessions = ConcurrentHashMap.newKeySet();

    private FaahTerminalFailureWatcher(@NotNull Project project) {
        this.project = project;
        this.alarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, this);
        scheduleScan(INITIAL_SCAN_DELAY_MS);
    }

    public static void install(@NotNull Project project) {
        FaahTerminalFailureWatcher watcher = new FaahTerminalFailureWatcher(project);
        Disposer.register(project, watcher);
    }

    private void scheduleScan(int delayMs) {
        alarm.addRequest(() -> {
            if (project.isDisposed()) {
                return;
            }
            scanTerminalSessions();
            scheduleScan(SCAN_INTERVAL_MS);
        }, delayMs);
    }

    private void scanTerminalSessions() {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TERMINAL_TOOL_WINDOW_ID);
        if (toolWindow == null) {
            return;
        }
        ContentManager contentManager = toolWindow.getContentManager();
        for (Content content : contentManager.getContents()) {
            attachListenerIfPossible(content);
        }
    }

    private void attachListenerIfPossible(@NotNull Content content) {
        JComponent component = content.getComponent();
        if (component == null) {
            return;
        }
        BlockTerminalSession session = findSession(component);
        if (session == null) {
            return;
        }
        if (!attachedSessions.add(session)) {
            return;
        }
        session.addCommandListener(new ShellCommandListener() {
            @Override
            public void commandFinished(@NotNull CommandFinishedEvent event) {
                if (event.getExitCode() != 0) {
                    FaahSoundService.getInstance().playEvent(project, FaahSoundEvent.TERMINAL_FAILED, "Terminal");
                } else {
                    FaahSoundService.getInstance().playEvent(project, FaahSoundEvent.TERMINAL_SUCCESS, "Terminal");
                }
            }
        }, this);
    }

    private BlockTerminalSession findSession(@NotNull JComponent root) {
        ArrayDeque<Component> queue = new ArrayDeque<>();
        queue.add(root);
        int scanned = 0;
        while (!queue.isEmpty() && scanned < MAX_COMPONENT_SCAN) {
            Component current = queue.removeFirst();
            scanned++;
            if (current instanceof JComponent currentComponent) {
                DataContext dataContext = DataManager.getInstance().getDataContext(currentComponent);
                BlockTerminalSession session = BlockTerminalSession.Companion.getDATA_KEY().getData(dataContext);
                if (session != null) {
                    return session;
                }
            }
            if (current instanceof Container container) {
                for (Component child : container.getComponents()) {
                    queue.addLast(child);
                }
            }
        }
        return null;
    }

    @Override
    public void dispose() {
        alarm.cancelAllRequests();
        attachedSessions.clear();
    }
}
