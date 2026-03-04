package dev.eministar.fahsound.listeners;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import dev.eministar.fahsound.sound.FaahSoundService;
import dev.eministar.fahsound.sound.FaahSoundEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.plugins.gradle.util.GradleConstants;

@Service(Service.Level.APP)
public final class FaahExternalSystemFailureWatcher implements Disposable {
    private final ExternalSystemProgressNotificationManager notificationManager;
    private final ExternalSystemTaskNotificationListener notificationListener = new ExternalSystemTaskNotificationListener() {
        @Override
        public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
            ProjectSystemId systemId = id.getProjectSystemId();
            if (GradleConstants.SYSTEM_ID.equals(systemId)) {
                FaahSoundService.getInstance().playEvent(null, FaahSoundEvent.GRADLE_FAILED, "Gradle");
            } else if (MavenUtil.SYSTEM_ID.equals(systemId)) {
                FaahSoundService.getInstance().playEvent(null, FaahSoundEvent.MAVEN_FAILED, "Maven");
            }
        }
    };

    public FaahExternalSystemFailureWatcher() {
        notificationManager = ApplicationManager.getApplication().getService(ExternalSystemProgressNotificationManager.class);
        notificationManager.addNotificationListener(notificationListener);
    }

    public static FaahExternalSystemFailureWatcher getInstance() {
        return ApplicationManager.getApplication().getService(FaahExternalSystemFailureWatcher.class);
    }

    @Override
    public void dispose() {
        notificationManager.removeNotificationListener(notificationListener);
    }
}
