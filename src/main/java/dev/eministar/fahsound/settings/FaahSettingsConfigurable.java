package dev.eministar.fahsound.settings;

import com.intellij.ide.actions.RevealFileAction;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBScrollPane;
import dev.eministar.fahsound.sound.FaahSoundCatalog;
import dev.eministar.fahsound.sound.FaahSoundEvent;
import dev.eministar.fahsound.sound.FaahSoundService;
import dev.eministar.fahsound.visual.FaahVisualCatalog;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.Scrollable;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.ChangeEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class FaahSettingsConfigurable implements SearchableConfigurable {
    private static final int MAX_DURATION_MS = 300000;
    private static final String DIALOG_TITLE = "FAH Failure Sound";

    private JComponent rootComponent;
    private SettingsScrollPanel contentPanel;
    private JCheckBox enabledCheckBox;
    private JSlider volumeSlider;
    private JLabel volumeValueLabel;
    private JSpinner debounceSpinner;
    private JCheckBox notificationCheckBox;
    private JCheckBox visualOverlayCheckBox;
    private JLabel customSoundFolderPathLabel;
    private JLabel customMediaFolderPathLabel;
    private JButton openSoundFolderButton;
    private JButton refreshSoundsButton;
    private JButton openMediaFolderButton;
    private JButton refreshMediaButton;
    private JButton importPresetButton;
    private JButton exportPresetButton;
    private final Map<FaahSoundEvent, JComboBox<FaahSoundCatalog.SoundSourceOption>> soundSelectors = new EnumMap<>(FaahSoundEvent.class);
    private final Map<FaahSoundEvent, JComboBox<FaahVisualCatalog.VisualSourceOption>> visualSelectors = new EnumMap<>(FaahSoundEvent.class);
    private final Map<FaahSoundEvent, JSpinner> soundDurationSpinners = new EnumMap<>(FaahSoundEvent.class);
    private final Map<FaahSoundEvent, JSpinner> visualDurationSpinners = new EnumMap<>(FaahSoundEvent.class);

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
        if (rootComponent == null) {
            initializeUi();
        }
        reset();
        return rootComponent;
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
        if (visualOverlayCheckBox.isSelected() != settings.isShowVisualOverlay()) {
            return true;
        }
        for (FaahSoundEvent soundEvent : FaahSoundEvent.orderedValues()) {
            if (!selectedSoundSourceId(soundEvent).equals(settings.getSoundSource(soundEvent))) {
                return true;
            }
            if (!selectedVisualSourceId(soundEvent).equals(settings.getVisualSource(soundEvent))) {
                return true;
            }
            if (selectedSoundDurationMs(soundEvent) != settings.getMaxDurationMs(soundEvent)) {
                return true;
            }
            if (selectedVisualDurationMs(soundEvent) != settings.getVisualDurationMs(soundEvent)) {
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
        settings.setShowVisualOverlay(visualOverlayCheckBox.isSelected());
        for (FaahSoundEvent soundEvent : FaahSoundEvent.orderedValues()) {
            settings.setSoundSource(soundEvent, selectedSoundSourceId(soundEvent));
            settings.setVisualSource(soundEvent, selectedVisualSourceId(soundEvent));
            settings.setMaxDurationMs(soundEvent, selectedSoundDurationMs(soundEvent));
            settings.setVisualDurationMs(soundEvent, selectedVisualDurationMs(soundEvent));
        }
    }

    @Override
    public void reset() {
        applyStateToUi(copyState(FaahSettingsService.getInstance().getState()));
    }

    @Override
    public void disposeUIResources() {
        rootComponent = null;
        contentPanel = null;
        enabledCheckBox = null;
        volumeSlider = null;
        volumeValueLabel = null;
        debounceSpinner = null;
        notificationCheckBox = null;
        visualOverlayCheckBox = null;
        customSoundFolderPathLabel = null;
        customMediaFolderPathLabel = null;
        openSoundFolderButton = null;
        refreshSoundsButton = null;
        openMediaFolderButton = null;
        refreshMediaButton = null;
        importPresetButton = null;
        exportPresetButton = null;
        soundSelectors.clear();
        visualSelectors.clear();
        soundDurationSpinners.clear();
        visualDurationSpinners.clear();
    }

    private void initializeUi() {
        contentPanel = new SettingsScrollPanel();
        contentPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        enabledCheckBox = new JCheckBox("Enable sounds");
        volumeSlider = new JSlider(0, 100, 100);
        volumeValueLabel = new JLabel("100");
        debounceSpinner = new JSpinner(new SpinnerNumberModel(2000, 0, 60000, 100));
        notificationCheckBox = new JCheckBox("Show notifications");
        visualOverlayCheckBox = new JCheckBox("Show image / GIF overlays");
        customSoundFolderPathLabel = new JLabel();
        customMediaFolderPathLabel = new JLabel();
        openSoundFolderButton = new JButton("Open sounds");
        refreshSoundsButton = new JButton("Refresh sounds");
        openMediaFolderButton = new JButton("Open media");
        refreshMediaButton = new JButton("Refresh media");
        importPresetButton = new JButton("Import preset");
        exportPresetButton = new JButton("Export preset");

        volumeSlider.addChangeListener(this::onVolumeChanged);
        openSoundFolderButton.addActionListener(event -> openSoundFolder());
        refreshSoundsButton.addActionListener(event -> reloadOptions(
                currentSoundSelections(),
                currentVisualSelections(),
                currentSoundDurations(),
                currentVisualDurations()
        ));
        openMediaFolderButton.addActionListener(event -> openMediaFolder());
        refreshMediaButton.addActionListener(event -> reloadOptions(
                currentSoundSelections(),
                currentVisualSelections(),
                currentSoundDurations(),
                currentVisualDurations()
        ));
        importPresetButton.addActionListener(event -> importPreset());
        exportPresetButton.addActionListener(event -> exportPreset());

        int row = 0;
        contentPanel.add(createGeneralPanel(), constraints(row++));
        contentPanel.add(createFoldersPanel(), constraints(row++));
        contentPanel.add(createPresetPanel(), constraints(row++));
        for (FaahSoundEvent soundEvent : FaahSoundEvent.orderedValues()) {
            contentPanel.add(createEventPanel(soundEvent), constraints(row++));
        }

        GridBagConstraints filler = constraints(row);
        filler.weighty = 1;
        filler.fill = GridBagConstraints.BOTH;
        contentPanel.add(new JPanel(), filler);

        JBScrollPane scrollPane = new JBScrollPane(
                contentPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        rootComponent = scrollPane;
    }

    @NotNull
    private JPanel createGeneralPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("General"),
                BorderFactory.createEmptyBorder(6, 8, 8, 8)
        ));

        GridBagConstraints c = baseConstraints();
        c.gridwidth = 2;
        c.insets = new Insets(0, 0, 8, 0);
        panel.add(enabledCheckBox, c);

        c.gridy++;
        c.gridwidth = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 8, 8);
        panel.add(new JLabel("Volume"), c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 8, 0);
        JPanel volumePanel = new JPanel(new GridBagLayout());
        GridBagConstraints volumeConstraints = baseConstraints();
        volumeConstraints.insets = new Insets(0, 0, 0, 8);
        volumeConstraints.weightx = 1;
        volumePanel.add(volumeSlider, volumeConstraints);
        volumeConstraints.gridx = 1;
        volumeConstraints.insets = new Insets(0, 0, 0, 0);
        volumeConstraints.weightx = 0;
        volumePanel.add(volumeValueLabel, volumeConstraints);
        panel.add(volumePanel, c);

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 8, 8);
        panel.add(new JLabel("Debounce (ms)"), c);

        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 8, 0);
        panel.add(debounceSpinner, c);

        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        c.insets = new Insets(0, 0, 8, 0);
        panel.add(notificationCheckBox, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        panel.add(visualOverlayCheckBox, c);
        return panel;
    }

    @NotNull
    private JPanel createFoldersPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Custom Files"),
                BorderFactory.createEmptyBorder(6, 8, 8, 8)
        ));

        GridBagConstraints c = baseConstraints();
        c.weightx = 0;
        c.insets = new Insets(0, 0, 8, 8);
        panel.add(new JLabel("Sounds"), c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(customSoundFolderPathLabel, c);

        JPanel soundButtonsPanel = new JPanel();
        soundButtonsPanel.add(openSoundFolderButton);
        soundButtonsPanel.add(refreshSoundsButton);
        c.gridx = 2;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 8, 0);
        panel.add(soundButtonsPanel, c);

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 8);
        panel.add(new JLabel("Media"), c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(customMediaFolderPathLabel, c);

        JPanel mediaButtonsPanel = new JPanel();
        mediaButtonsPanel.add(openMediaFolderButton);
        mediaButtonsPanel.add(refreshMediaButton);
        c.gridx = 2;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 0);
        panel.add(mediaButtonsPanel, c);
        return panel;
    }

    @NotNull
    private JPanel createPresetPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Presets"),
                BorderFactory.createEmptyBorder(6, 8, 8, 8)
        ));

        GridBagConstraints c = baseConstraints();
        c.insets = new Insets(0, 0, 8, 8);
        panel.add(importPresetButton, c);

        c.gridx = 1;
        c.insets = new Insets(0, 0, 8, 0);
        panel.add(exportPresetButton, c);

        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        panel.add(new JLabel("Import/Export saves all event sounds, media, durations, and global options as XML."), c);
        return panel;
    }

    @NotNull
    private JPanel createEventPanel(@NotNull FaahSoundEvent soundEvent) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(soundEvent.getDisplayName()),
                BorderFactory.createEmptyBorder(6, 8, 8, 8)
        ));

        JComboBox<FaahSoundCatalog.SoundSourceOption> soundSelector = createSoundSelector();
        soundSelectors.put(soundEvent, soundSelector);
        JComboBox<FaahVisualCatalog.VisualSourceOption> visualSelector = createVisualSelector();
        visualSelectors.put(soundEvent, visualSelector);
        JSpinner soundDurationSpinner = new JSpinner(new SpinnerNumberModel(0, 0, MAX_DURATION_MS, 100));
        soundDurationSpinners.put(soundEvent, soundDurationSpinner);
        JSpinner visualDurationSpinner = new JSpinner(new SpinnerNumberModel(0, 0, MAX_DURATION_MS, 100));
        visualDurationSpinners.put(soundEvent, visualDurationSpinner);
        JButton testButton = new JButton("Test");
        testButton.addActionListener(actionEvent -> testSelectedEvent(soundEvent));

        GridBagConstraints c = baseConstraints();
        c.weightx = 0;
        c.insets = new Insets(0, 0, 8, 8);
        panel.add(new JLabel("Sound"), c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 8, 0);
        panel.add(soundSelector, c);

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 8, 8);
        panel.add(new JLabel("Sound ms (0=full)"), c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 8, 0);
        panel.add(soundDurationSpinner, c);

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 8, 8);
        panel.add(new JLabel("Image / GIF"), c);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 8, 0);
        panel.add(visualSelector, c);

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 8);
        panel.add(new JLabel("Overlay ms (0=auto)"), c);

        JPanel footerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints footerConstraints = baseConstraints();
        footerConstraints.weightx = 1;
        footerConstraints.fill = GridBagConstraints.HORIZONTAL;
        footerConstraints.insets = new Insets(0, 0, 0, 8);
        footerPanel.add(visualDurationSpinner, footerConstraints);
        footerConstraints.gridx = 1;
        footerConstraints.weightx = 0;
        footerConstraints.fill = GridBagConstraints.NONE;
        footerConstraints.insets = new Insets(0, 0, 0, 0);
        footerPanel.add(testButton, footerConstraints);

        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 0);
        panel.add(footerPanel, c);
        return panel;
    }

    private void applyStateToUi(@NotNull FaahSettingsService.StateData state) {
        FaahSettingsService normalizer = new FaahSettingsService();
        normalizer.loadState(copyState(state));

        enabledCheckBox.setSelected(normalizer.isEnabled());
        volumeSlider.setValue(normalizer.getVolume());
        volumeValueLabel.setText(Integer.toString(normalizer.getVolume()));
        debounceSpinner.setValue(normalizer.getDebounceMs());
        notificationCheckBox.setSelected(normalizer.isShowNotification());
        visualOverlayCheckBox.setSelected(normalizer.isShowVisualOverlay());
        updateFolderLabels();

        Map<FaahSoundEvent, String> soundSelections = new EnumMap<>(FaahSoundEvent.class);
        Map<FaahSoundEvent, String> visualSelections = new EnumMap<>(FaahSoundEvent.class);
        Map<FaahSoundEvent, Integer> soundDurations = new EnumMap<>(FaahSoundEvent.class);
        Map<FaahSoundEvent, Integer> visualDurations = new EnumMap<>(FaahSoundEvent.class);
        for (FaahSoundEvent soundEvent : FaahSoundEvent.orderedValues()) {
            soundSelections.put(soundEvent, normalizer.getSoundSource(soundEvent));
            visualSelections.put(soundEvent, normalizer.getVisualSource(soundEvent));
            soundDurations.put(soundEvent, normalizer.getMaxDurationMs(soundEvent));
            visualDurations.put(soundEvent, normalizer.getVisualDurationMs(soundEvent));
        }
        reloadOptions(soundSelections, visualSelections, soundDurations, visualDurations);
    }

    @NotNull
    private FaahSettingsService.StateData copyState(@NotNull FaahSettingsService.StateData source) {
        FaahSettingsService.StateData copy = new FaahSettingsService.StateData();
        copy.enabled = source.enabled;
        copy.volume = source.volume;
        copy.debounceMs = source.debounceMs;
        copy.soundFileName = source.soundFileName;
        copy.showNotification = source.showNotification;
        copy.showFailureImage = source.showFailureImage;
        if (source.soundByEvent != null) {
            copy.soundByEvent.putAll(source.soundByEvent);
        }
        if (source.visualByEvent != null) {
            copy.visualByEvent.putAll(source.visualByEvent);
        }
        if (source.maxDurationMsByEvent != null) {
            copy.maxDurationMsByEvent.putAll(source.maxDurationMsByEvent);
        }
        if (source.visualDurationMsByEvent != null) {
            copy.visualDurationMsByEvent.putAll(source.visualDurationMsByEvent);
        }
        return copy;
    }

    private void updateFolderLabels() {
        String soundPath = FaahSoundCatalog.getCustomFolderPath().toString();
        customSoundFolderPathLabel.setText(soundPath);
        customSoundFolderPathLabel.setToolTipText(soundPath);

        String mediaPath = FaahVisualCatalog.getCustomFolderPath().toString();
        customMediaFolderPathLabel.setText(mediaPath);
        customMediaFolderPathLabel.setToolTipText(mediaPath);
    }

    private void onVolumeChanged(ChangeEvent event) {
        if (volumeValueLabel != null && volumeSlider != null) {
            volumeValueLabel.setText(Integer.toString(volumeSlider.getValue()));
        }
    }

    private void testSelectedEvent(@NotNull FaahSoundEvent soundEvent) {
        try {
            FaahSoundService.getInstance().previewEvent(
                    soundEvent,
                    selectedSoundSourceId(soundEvent),
                    selectedVisualSourceId(soundEvent),
                    selectedSoundDurationMs(soundEvent),
                    selectedVisualDurationMs(soundEvent)
            );
        } catch (Throwable t) {
            Messages.showErrorDialog("Unable to preview event: " + t.getMessage(), DIALOG_TITLE);
        }
    }

    private void openSoundFolder() {
        try {
            Path folder = FaahSoundCatalog.ensureCustomFolderExists();
            RevealFileAction.openDirectory(folder);
            updateFolderLabels();
            reloadOptions(currentSoundSelections(), currentVisualSelections(), currentSoundDurations(), currentVisualDurations());
        } catch (IOException e) {
            Messages.showErrorDialog("Unable to open custom sound folder: " + e.getMessage(), DIALOG_TITLE);
        }
    }

    private void openMediaFolder() {
        try {
            Path folder = FaahVisualCatalog.ensureCustomFolderExists();
            RevealFileAction.openDirectory(folder);
            updateFolderLabels();
            reloadOptions(currentSoundSelections(), currentVisualSelections(), currentSoundDurations(), currentVisualDurations());
        } catch (IOException e) {
            Messages.showErrorDialog("Unable to open custom media folder: " + e.getMessage(), DIALOG_TITLE);
        }
    }

    private void importPreset() {
        JFileChooser chooser = createPresetChooser();
        if (chooser.showOpenDialog(rootComponent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            FaahSettingsService.StateData importedState = FaahPresetFileSupport.importPreset(chooser.getSelectedFile().toPath());
            applyStateToUi(importedState);
            Messages.showInfoMessage("Preset loaded into the form. Click Apply to save it.", DIALOG_TITLE);
        } catch (IOException e) {
            Messages.showErrorDialog("Unable to import preset: " + e.getMessage(), DIALOG_TITLE);
        }
    }

    private void exportPreset() {
        JFileChooser chooser = createPresetChooser();
        chooser.setSelectedFile(Path.of("faah-preset.xml").toFile());
        if (chooser.showSaveDialog(rootComponent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();
        if (!file.getFileName().toString().toLowerCase().endsWith(".xml")) {
            file = file.resolveSibling(file.getFileName() + ".xml");
        }
        try {
            FaahPresetFileSupport.exportPreset(file, snapshotState());
        } catch (IOException e) {
            Messages.showErrorDialog("Unable to export preset: " + e.getMessage(), DIALOG_TITLE);
        }
    }

    @NotNull
    private JFileChooser createPresetChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("FAH preset");
        chooser.setFileFilter(new FileNameExtensionFilter("FAH preset (*.xml)", "xml"));
        chooser.setAcceptAllFileFilterUsed(false);
        return chooser;
    }

    @NotNull
    private FaahSettingsService.StateData snapshotState() {
        FaahSettingsService.StateData state = new FaahSettingsService.StateData();
        state.enabled = enabledCheckBox.isSelected();
        state.volume = volumeSlider.getValue();
        state.debounceMs = ((Number) debounceSpinner.getValue()).intValue();
        state.showNotification = notificationCheckBox.isSelected();
        state.showFailureImage = visualOverlayCheckBox.isSelected();

        for (FaahSoundEvent soundEvent : FaahSoundEvent.orderedValues()) {
            state.soundByEvent.put(soundEvent.getId(), selectedSoundSourceId(soundEvent));
            state.visualByEvent.put(soundEvent.getId(), selectedVisualSourceId(soundEvent));
            state.maxDurationMsByEvent.put(soundEvent.getId(), selectedSoundDurationMs(soundEvent));
            state.visualDurationMsByEvent.put(soundEvent.getId(), selectedVisualDurationMs(soundEvent));
        }
        return state;
    }

    private void reloadOptions(@NotNull Map<FaahSoundEvent, String> preferredSoundSelections,
                               @NotNull Map<FaahSoundEvent, String> preferredVisualSelections,
                               @NotNull Map<FaahSoundEvent, Integer> preferredSoundDurations,
                               @NotNull Map<FaahSoundEvent, Integer> preferredVisualDurations) {
        List<FaahSoundCatalog.SoundSourceOption> soundOptions = FaahSoundCatalog.listAvailableSources();
        FaahSoundCatalog.SoundSourceOption[] soundOptionArray = soundOptions.toArray(new FaahSoundCatalog.SoundSourceOption[0]);
        List<FaahVisualCatalog.VisualSourceOption> visualOptions = FaahVisualCatalog.listAvailableSources();
        FaahVisualCatalog.VisualSourceOption[] visualOptionArray = visualOptions.toArray(new FaahVisualCatalog.VisualSourceOption[0]);

        for (FaahSoundEvent soundEvent : FaahSoundEvent.orderedValues()) {
            JComboBox<FaahSoundCatalog.SoundSourceOption> soundComboBox = soundSelectors.get(soundEvent);
            if (soundComboBox != null) {
                soundComboBox.setModel(new DefaultComboBoxModel<>(soundOptionArray));
                selectSoundBySourceId(soundComboBox, preferredSoundSelections.get(soundEvent), soundEvent);
            }

            JComboBox<FaahVisualCatalog.VisualSourceOption> visualComboBox = visualSelectors.get(soundEvent);
            if (visualComboBox != null) {
                visualComboBox.setModel(new DefaultComboBoxModel<>(visualOptionArray));
                selectVisualBySourceId(visualComboBox, preferredVisualSelections.get(soundEvent), soundEvent);
            }

            JSpinner soundDurationSpinner = soundDurationSpinners.get(soundEvent);
            if (soundDurationSpinner != null) {
                soundDurationSpinner.setValue(Math.max(0, preferredSoundDurations.getOrDefault(soundEvent, 0)));
            }

            JSpinner visualDurationSpinner = visualDurationSpinners.get(soundEvent);
            if (visualDurationSpinner != null) {
                visualDurationSpinner.setValue(Math.max(0, preferredVisualDurations.getOrDefault(soundEvent, 0)));
            }
        }
    }

    @NotNull
    private Map<FaahSoundEvent, String> currentSoundSelections() {
        Map<FaahSoundEvent, String> selections = new EnumMap<>(FaahSoundEvent.class);
        for (FaahSoundEvent soundEvent : FaahSoundEvent.orderedValues()) {
            selections.put(soundEvent, selectedSoundSourceId(soundEvent));
        }
        return selections;
    }

    @NotNull
    private Map<FaahSoundEvent, String> currentVisualSelections() {
        Map<FaahSoundEvent, String> selections = new EnumMap<>(FaahSoundEvent.class);
        for (FaahSoundEvent soundEvent : FaahSoundEvent.orderedValues()) {
            selections.put(soundEvent, selectedVisualSourceId(soundEvent));
        }
        return selections;
    }

    @NotNull
    private Map<FaahSoundEvent, Integer> currentSoundDurations() {
        Map<FaahSoundEvent, Integer> durations = new EnumMap<>(FaahSoundEvent.class);
        for (FaahSoundEvent soundEvent : FaahSoundEvent.orderedValues()) {
            durations.put(soundEvent, selectedSoundDurationMs(soundEvent));
        }
        return durations;
    }

    @NotNull
    private Map<FaahSoundEvent, Integer> currentVisualDurations() {
        Map<FaahSoundEvent, Integer> durations = new EnumMap<>(FaahSoundEvent.class);
        for (FaahSoundEvent soundEvent : FaahSoundEvent.orderedValues()) {
            durations.put(soundEvent, selectedVisualDurationMs(soundEvent));
        }
        return durations;
    }

    @NotNull
    private String selectedSoundSourceId(@NotNull FaahSoundEvent soundEvent) {
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

    @NotNull
    private String selectedVisualSourceId(@NotNull FaahSoundEvent soundEvent) {
        JComboBox<FaahVisualCatalog.VisualSourceOption> comboBox = visualSelectors.get(soundEvent);
        if (comboBox == null) {
            return FaahVisualCatalog.normalizeSourceId(soundEvent.getDefaultVisualSourceId());
        }
        Object selected = comboBox.getSelectedItem();
        if (selected instanceof FaahVisualCatalog.VisualSourceOption option) {
            return FaahVisualCatalog.normalizeSourceId(option.sourceId());
        }
        return FaahVisualCatalog.normalizeSourceId(soundEvent.getDefaultVisualSourceId());
    }

    private int selectedSoundDurationMs(@NotNull FaahSoundEvent soundEvent) {
        return selectedDuration(soundDurationSpinners.get(soundEvent));
    }

    private int selectedVisualDurationMs(@NotNull FaahSoundEvent soundEvent) {
        return selectedDuration(visualDurationSpinners.get(soundEvent));
    }

    private int selectedDuration(@Nullable JSpinner spinner) {
        if (spinner == null) {
            return 0;
        }
        Object value = spinner.getValue();
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        return 0;
    }

    private void selectSoundBySourceId(@NotNull JComboBox<FaahSoundCatalog.SoundSourceOption> comboBox,
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

    private void selectVisualBySourceId(@NotNull JComboBox<FaahVisualCatalog.VisualSourceOption> comboBox,
                                        @Nullable String sourceId,
                                        @NotNull FaahSoundEvent soundEvent) {
        String normalized = FaahVisualCatalog.normalizeSourceId(sourceId);
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            FaahVisualCatalog.VisualSourceOption item = comboBox.getItemAt(i);
            if (item.sourceId().equals(normalized)) {
                comboBox.setSelectedIndex(i);
                return;
            }
        }
        String defaultSource = FaahVisualCatalog.normalizeSourceId(soundEvent.getDefaultVisualSourceId());
        for (int i = 0; i < comboBox.getItemCount(); i++) {
            FaahVisualCatalog.VisualSourceOption item = comboBox.getItemAt(i);
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
    private JComboBox<FaahVisualCatalog.VisualSourceOption> createVisualSelector() {
        JComboBox<FaahVisualCatalog.VisualSourceOption> comboBox = new JComboBox<>();
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list,
                                                          Object value,
                                                          int index,
                                                          boolean isSelected,
                                                          boolean cellHasFocus) {
                Object displayValue = value;
                if (value instanceof FaahVisualCatalog.VisualSourceOption option) {
                    displayValue = option.displayName();
                }
                return super.getListCellRendererComponent(list, displayValue, index, isSelected, cellHasFocus);
            }
        });
        return comboBox;
    }

    @NotNull
    private GridBagConstraints constraints(int row) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(0, 0, 10, 0);
        return c;
    }

    @NotNull
    private GridBagConstraints baseConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 0, 0);
        return c;
    }

    private static final class SettingsScrollPanel extends JPanel implements Scrollable {
        private SettingsScrollPanel() {
            super(new GridBagLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(visibleRect.height - 24, 24);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
