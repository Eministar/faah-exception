package dev.eministar.fahsound.settings;

import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import dev.eministar.fahsound.sound.FaahSoundCatalog;
import dev.eministar.fahsound.sound.FaahSoundEvent;
import dev.eministar.fahsound.sound.FaahSoundService;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class FaahSettingsConfigurable implements SearchableConfigurable {
    private static final int MAX_DURATION_MS = 300000;

    private JPanel rootPanel;
    private JCheckBox enabledCheckBox;
    private JSlider volumeSlider;
    private JLabel volumeValueLabel;
    private JSpinner debounceSpinner;
    private JCheckBox notificationCheckBox;
    private JLabel customFolderPathLabel;
    private JButton openFolderButton;
    private JButton refreshSoundsButton;
    private final Map<FaahSoundEvent, JComboBox<FaahSoundCatalog.SoundSourceOption>> soundSelectors = new EnumMap<>(FaahSoundEvent.class);
    private final Map<FaahSoundEvent, JSpinner> durationSpinners = new EnumMap<>(FaahSoundEvent.class);

    @Override
    public @NotNull String getId() {
        return "dev.eministar.fahsound.settings";
    }

    @Override
    public @Nls @NotNull String getDisplayName() {
        return "FAH Failure Sound";
    }

    @Override
    public @Nullable JComponent createComponent() {
        if (rootPanel == null) {
            rootPanel = new JPanel(new GridBagLayout());
            enabledCheckBox = new JCheckBox("Enable sounds");
            volumeSlider = new JSlider(0, 100, 100);
            volumeValueLabel = new JLabel("100");
            debounceSpinner = new JSpinner(new SpinnerNumberModel(2000, 0, 60000, 100));
            notificationCheckBox = new JCheckBox("Show notification");
            customFolderPathLabel = new JLabel();
            openFolderButton = new JButton("Open folder");
            refreshSoundsButton = new JButton("Refresh sounds");

            volumeSlider.addChangeListener(this::onVolumeChanged);
            openFolderButton.addActionListener(event -> openCustomFolder());
            refreshSoundsButton.addActionListener(event -> reloadSoundOptions(currentSelections(), currentDurations()));

            GridBagConstraints c = baseConstraints();
            c.gridwidth = 4;
            rootPanel.add(enabledCheckBox, c);

            c.gridy++;
            c.gridwidth = 1;
            c.weightx = 0;
            c.insets = new Insets(0, 0, 8, 8);
            rootPanel.add(new JLabel("Volume"), c);

            c.gridx = 1;
            c.weightx = 1;
            c.gridwidth = 2;
            c.insets = new Insets(0, 0, 8, 8);
            rootPanel.add(volumeSlider, c);

            c.gridx = 3;
            c.gridwidth = 1;
            c.weightx = 0;
            c.insets = new Insets(0, 0, 8, 0);
            rootPanel.add(volumeValueLabel, c);

            c.gridx = 0;
            c.gridy++;
            c.weightx = 0;
            c.gridwidth = 1;
            c.insets = new Insets(0, 0, 8, 8);
            rootPanel.add(new JLabel("Debounce (ms)"), c);

            c.gridx = 1;
            c.gridwidth = 3;
            c.weightx = 1;
            c.insets = new Insets(0, 0, 8, 0);
            rootPanel.add(debounceSpinner, c);

            c.gridx = 0;
            c.gridy++;
            c.gridwidth = 4;
            c.weightx = 1;
            c.insets = new Insets(0, 0, 8, 0);
            rootPanel.add(notificationCheckBox, c);

            c.gridy++;
            c.gridwidth = 1;
            c.weightx = 0;
            c.insets = new Insets(0, 0, 8, 8);
            rootPanel.add(new JLabel("Custom sounds folder"), c);

            c.gridx = 1;
            c.gridwidth = 2;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(0, 0, 8, 8);
            rootPanel.add(customFolderPathLabel, c);

            JPanel folderButtonsPanel = new JPanel();
            folderButtonsPanel.add(openFolderButton);
            folderButtonsPanel.add(refreshSoundsButton);
            c.gridx = 3;
            c.gridwidth = 1;
            c.weightx = 0;
            c.insets = new Insets(0, 0, 8, 0);
            rootPanel.add(folderButtonsPanel, c);

            c.gridx = 0;
            c.gridy++;
            c.gridwidth = 1;
            c.weightx = 0;
            c.insets = new Insets(0, 0, 6, 8);
            rootPanel.add(new JLabel("Event"), c);

            c.gridx = 1;
            c.weightx = 1;
            c.insets = new Insets(0, 0, 6, 8);
            rootPanel.add(new JLabel("Sound"), c);

            c.gridx = 2;
            c.weightx = 0;
            c.insets = new Insets(0, 0, 6, 8);
            rootPanel.add(new JLabel("Max ms (0=full)"), c);

            c.gridx = 3;
            c.weightx = 0;
            c.insets = new Insets(0, 0, 6, 0);
            rootPanel.add(new JLabel("Test"), c);

            for (FaahSoundEvent soundEvent : FaahSoundEvent.orderedValues()) {
                c.gridx = 0;
                c.gridy++;
                c.gridwidth = 1;
                c.weightx = 0;
                c.fill = GridBagConstraints.NONE;
                c.anchor = GridBagConstraints.WEST;
                c.insets = new Insets(0, 0, 8, 8);
                rootPanel.add(new JLabel(soundEvent.getDisplayName()), c);

                JComboBox<FaahSoundCatalog.SoundSourceOption> comboBox = createSoundSelector();
                soundSelectors.put(soundEvent, comboBox);
                c.gridx = 1;
                c.weightx = 1;
                c.fill = GridBagConstraints.HORIZONTAL;
                c.insets = new Insets(0, 0, 8, 8);
                rootPanel.add(comboBox, c);

                JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(0, 0, MAX_DURATION_MS, 100));
                durationSpinners.put(soundEvent, durationSpinner);
                c.gridx = 2;
                c.weightx = 0;
                c.fill = GridBagConstraints.HORIZONTAL;
                c.insets = new Insets(0, 0, 8, 8);
                rootPanel.add(durationSpinner, c);

                JButton testButton = new JButton("Test");
                testButton.addActionListener(actionEvent -> testSelectedSound(soundEvent));
                c.gridx = 3;
                c.weightx = 0;
                c.fill = GridBagConstraints.NONE;
                c.insets = new Insets(0, 0, 8, 0);
                rootPanel.add(testButton, c);
            }

            c.gridx = 0;
            c.gridy++;
            c.gridwidth = 4;
            c.weightx = 1;
            c.weighty = 1;
            c.fill = GridBagConstraints.BOTH;
            c.insets = new Insets(0, 0, 0, 0);
            rootPanel.add(new JPanel(), c);
        }
        reset();
        return rootPanel;
    }

    @Override
    public boolean isModified() {
        FaahSettingsService settings = FaahSettingsService.getInstance();
        if (enabledCheckBox.isSelected() != settings.isEnabled()) {
            return true;
        }
        if (volumeSlider.getValue() != settings.getVolume()) {
            return true;
        }
        if (((Number) debounceSpinner.getValue()).intValue() != settings.getDebounceMs()) {
            return true;
        }
        if (notificationCheckBox.isSelected() != settings.isShowNotification()) {
            return true;
        }
        for (FaahSoundEvent soundEvent : FaahSoundEvent.orderedValues()) {
            if (!selectedSourceId(soundEvent).equals(settings.getSoundSource(soundEvent))) {
                return true;
            }
            if (selectedDurationMs(soundEvent) != settings.getMaxDurationMs(soundEvent)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void apply() throws ConfigurationException {
        FaahSettingsService settings = FaahSettingsService.getInstance();
        settings.setEnabled(enabledCheckBox.isSelected());
        settings.setVolume(volumeSlider.getValue());
        settings.setDebounceMs(((Number) debounceSpinner.getValue()).intValue());
        settings.setShowNotification(notificationCheckBox.isSelected());
        for (FaahSoundEvent soundEvent : FaahSoundEvent.orderedValues()) {
            settings.setSoundSource(soundEvent, selectedSourceId(soundEvent));
            settings.setMaxDurationMs(soundEvent, selectedDurationMs(soundEvent));
        }
    }

    @Override
    public void reset() {
        FaahSettingsService settings = FaahSettingsService.getInstance();
        enabledCheckBox.setSelected(settings.isEnabled());
        volumeSlider.setValue(settings.getVolume());
        volumeValueLabel.setText(Integer.toString(settings.getVolume()));
        debounceSpinner.setValue(settings.getDebounceMs());
        notificationCheckBox.setSelected(settings.isShowNotification());
        String customPath = FaahSoundCatalog.getCustomFolderPath().toString();
        customFolderPathLabel.setText(customPath);
        customFolderPathLabel.setToolTipText(customPath);

        Map<FaahSoundEvent, String> selections = new EnumMap<>(FaahSoundEvent.class);
        Map<FaahSoundEvent, Integer> durations = new EnumMap<>(FaahSoundEvent.class);
        for (FaahSoundEvent soundEvent : FaahSoundEvent.orderedValues()) {
            selections.put(soundEvent, settings.getSoundSource(soundEvent));
            durations.put(soundEvent, settings.getMaxDurationMs(soundEvent));
        }
        reloadSoundOptions(selections, durations);
    }

    @Override
    public void disposeUIResources() {
        rootPanel = null;
        enabledCheckBox = null;
        volumeSlider = null;
        volumeValueLabel = null;
        debounceSpinner = null;
        notificationCheckBox = null;
        customFolderPathLabel = null;
        openFolderButton = null;
        refreshSoundsButton = null;
        soundSelectors.clear();
        durationSpinners.clear();
    }

    private void onVolumeChanged(ChangeEvent event) {
        if (volumeValueLabel != null && volumeSlider != null) {
            volumeValueLabel.setText(Integer.toString(volumeSlider.getValue()));
        }
    }

    private void testSelectedSound(@NotNull FaahSoundEvent soundEvent) {
        try {
            FaahSoundService.getInstance().playTestSound(
                    selectedSourceId(soundEvent),
                    selectedDurationMs(soundEvent)
            );
        } catch (Throwable t) {
            Messages.showErrorDialog("Unable to play test sound: " + t.getMessage(), "FAH Failure Sound");
        }
    }

    private void openCustomFolder() {
        try {
            Path folder = FaahSoundCatalog.ensureCustomFolderExists();
            String folderString = folder.toString();
            customFolderPathLabel.setText(folderString);
            customFolderPathLabel.setToolTipText(folderString);
            RevealFileAction.openDirectory(folder);
            reloadSoundOptions(currentSelections(), currentDurations());
        } catch (IOException e) {
            Messages.showErrorDialog("Unable to open custom sound folder: " + e.getMessage(), "FAH Failure Sound");
        }
    }

    private void reloadSoundOptions(@NotNull Map<FaahSoundEvent, String> preferredSelections,
                                    @NotNull Map<FaahSoundEvent, Integer> preferredDurations) {
        List<FaahSoundCatalog.SoundSourceOption> options = FaahSoundCatalog.listAvailableSources();
        FaahSoundCatalog.SoundSourceOption[] optionArray = options.toArray(new FaahSoundCatalog.SoundSourceOption[0]);
        for (FaahSoundEvent soundEvent : FaahSoundEvent.orderedValues()) {
            JComboBox<FaahSoundCatalog.SoundSourceOption> comboBox = soundSelectors.get(soundEvent);
            if (comboBox != null) {
                comboBox.setModel(new DefaultComboBoxModel<>(optionArray));
                selectBySourceId(comboBox, preferredSelections.get(soundEvent), soundEvent);
            }
            JSpinner durationSpinner = durationSpinners.get(soundEvent);
            if (durationSpinner != null) {
                int duration = preferredDurations.getOrDefault(soundEvent, 0);
                durationSpinner.setValue(Math.max(0, duration));
            }
        }
    }

    @NotNull
    private Map<FaahSoundEvent, String> currentSelections() {
        Map<FaahSoundEvent, String> selections = new EnumMap<>(FaahSoundEvent.class);
        for (FaahSoundEvent soundEvent : FaahSoundEvent.orderedValues()) {
            selections.put(soundEvent, selectedSourceId(soundEvent));
        }
        return selections;
    }

    @NotNull
    private Map<FaahSoundEvent, Integer> currentDurations() {
        Map<FaahSoundEvent, Integer> durations = new EnumMap<>(FaahSoundEvent.class);
        for (FaahSoundEvent soundEvent : FaahSoundEvent.orderedValues()) {
            durations.put(soundEvent, selectedDurationMs(soundEvent));
        }
        return durations;
    }

    @NotNull
    private String selectedSourceId(@NotNull FaahSoundEvent soundEvent) {
        JComboBox<FaahSoundCatalog.SoundSourceOption> comboBox = soundSelectors.get(soundEvent);
        if (comboBox == null) {
            return FaahSoundCatalog.normalizeSourceId(soundEvent.getDefaultSourceId());
        }
        Object selected = comboBox.getSelectedItem();
        if (selected instanceof FaahSoundCatalog.SoundSourceOption option) {
            return FaahSoundCatalog.normalizeSourceId(option.sourceId());
        }
        return FaahSoundCatalog.normalizeSourceId(soundEvent.getDefaultSourceId());
    }

    private int selectedDurationMs(@NotNull FaahSoundEvent soundEvent) {
        JSpinner spinner = durationSpinners.get(soundEvent);
        if (spinner == null) {
            return 0;
        }
        Object value = spinner.getValue();
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        return 0;
    }

    private void selectBySourceId(@NotNull JComboBox<FaahSoundCatalog.SoundSourceOption> comboBox,
                                  @Nullable String sourceId,
                                  @NotNull FaahSoundEvent soundEvent) {
        String normalized = FaahSoundCatalog.normalizeSourceId(sourceId);
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            FaahSoundCatalog.SoundSourceOption item = comboBox.getItemAt(i);
            if (item.sourceId().equals(normalized)) {
                comboBox.setSelectedIndex(i);
                return;
            }
        }
        String defaultSource = FaahSoundCatalog.normalizeSourceId(soundEvent.getDefaultSourceId());
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            FaahSoundCatalog.SoundSourceOption item = comboBox.getItemAt(i);
            if (item.sourceId().equals(defaultSource)) {
                comboBox.setSelectedIndex(i);
                return;
            }
        }
        if (comboBox.getItemCount() > 0) {
            comboBox.setSelectedIndex(0);
        }
    }

    @NotNull
    private JComboBox<FaahSoundCatalog.SoundSourceOption> createSoundSelector() {
        JComboBox<FaahSoundCatalog.SoundSourceOption> comboBox = new JComboBox<>();
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list,
                                                          Object value,
                                                          int index,
                                                          boolean isSelected,
                                                          boolean cellHasFocus) {
                Object displayValue = value;
                if (value instanceof FaahSoundCatalog.SoundSourceOption option) {
                    displayValue = option.displayName();
                }
                return super.getListCellRendererComponent(list, displayValue, index, isSelected, cellHasFocus);
            }
        });
        return comboBox;
    }

    @NotNull
    private GridBagConstraints baseConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 8, 0);
        return c;
    }
}
