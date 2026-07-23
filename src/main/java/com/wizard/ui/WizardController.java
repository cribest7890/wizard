package com.wizard.ui;

import com.wizard.InstallerService;
import com.wizard.icon.IconManager;
import com.wizard.logging.LoggerService;
import com.wizard.model.InstallationRequest;
import com.wizard.model.InstallationResult;
import com.wizard.system.DistributionDetector;
import com.wizard.system.LinuxSystemDetector;
import com.wizard.system.PackageManager;
import com.wizard.util.ValidationUtils;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Drives the multi-step installer wizard: Welcome/File selection, Application metadata,
 * Icon configuration, Integration options, Installation progress, and Finish.
 * Supports Next / Back / Cancel / Finish navigation with a progress indicator,
 * drag & drop, file/image selection, and inline status and error reporting.
 */
public final class WizardController {

    private final Stage stage;
    private final InstallerService installerService = new InstallerService();
    private final InstallationRequest request = new InstallationRequest();
    private final LoggerService logger = LoggerService.getInstance();

    private final List<String> stepTitles = List.of(
            "Select File", "Application Details", "Icon", "Integration Options", "Install", "Finish");

    private int currentStep = 0;
    private final BorderPane root = new BorderPane();
    private final StackPane contentHost = new StackPane();
    private final Label stepIndicator = new Label();
    private final ProgressBar overallProgress = new ProgressBar(0);
    private final Button backButton = new Button("Back");
    private final Button primaryButton = new Button("Next");
    private final Button cancelButton = new Button("Cancel");

    private TextArea logArea;
    private ImageView iconPreview;
    private ToggleGroup iconToggleGroup;
    private TextField iconInputField;
    private CheckBox copySupportFilesCheck;
    private CheckBox desktopShortcutCheck;
    private InstallationResult lastResult;

    public WizardController(Stage stage) {
        this.stage = stage;
    }

    public BorderPane buildRoot() {
        root.setTop(buildHeader());
        root.setCenter(contentHost);
        root.setBottom(buildFooter());
        showStep(0);
        return root;
    }

    // ----------------------------------------------------------------- header/footer

    private Node buildHeader() {
        VBox header = new VBox(6);
        header.getStyleClass().add("wizard-header");
        Label title = new Label("Application Wizard");
        title.getStyleClass().add("wizard-title");
        Label subtitle = new Label("Install and integrate applications on any Linux desktop");
        subtitle.getStyleClass().add("wizard-subtitle");
        stepIndicator.getStyleClass().add("step-indicator");
        header.getChildren().addAll(title, subtitle, stepIndicator);
        return header;
    }

    private Node buildFooter() {
        HBox footer = new HBox(10);
        footer.getStyleClass().add("wizard-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);

        overallProgress.setPrefWidth(200);
        HBox.setHgrow(overallProgress, Priority.NEVER);
        cancelButton.getStyleClass().add("button-danger");
        backButton.getStyleClass().add("button-secondary");
        primaryButton.getStyleClass().add("button-primary");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        cancelButton.setOnAction(e -> onCancel());
        backButton.setOnAction(e -> onBack());
        primaryButton.setOnAction(e -> onPrimary());

        footer.getChildren().addAll(overallProgress, spacer, cancelButton, backButton, primaryButton);
        return footer;
    }

    private void updateNavState() {
        stepIndicator.setText("Step " + (currentStep + 1) + " of " + stepTitles.size() + ": " + stepTitles.get(currentStep));
        overallProgress.setProgress((currentStep + 1) / (double) stepTitles.size());
        backButton.setDisable(currentStep == 0 || currentStep == 4);
        cancelButton.setDisable(currentStep == 5);

        primaryButton.setText(switch (currentStep) {
            case 3 -> "Install";
            case 4 -> "Please wait...";
            case 5 -> "Finish";
            default -> "Next";
        });
        primaryButton.setDisable(currentStep == 4);
    }

    // ----------------------------------------------------------------- navigation

    private void onCancel() {
        boolean confirmed = DialogFactory.showConfirmation("Cancel Installation",
                "Are you sure you want to cancel? Any progress on this installation will be lost.");
        if (confirmed) {
            Platform.exit();
        }
    }

    private void onBack() {
        if (currentStep > 0) {
            showStep(currentStep - 1);
        }
    }

    private void onPrimary() {
        if (!validateCurrentStep()) {
            return;
        }
        if (currentStep == 3) {
            showStep(4);
            runInstallation();
        } else if (currentStep == 5) {
            Platform.exit();
        } else {
            showStep(currentStep + 1);
        }
    }

    private boolean validateCurrentStep() {
        switch (currentStep) {
            case 0 -> {
                if (ValidationUtils.isBlank(request.getSourceFilePath())
                        || !new File(ValidationUtils.expandUserPath(request.getSourceFilePath())).isFile()) {
                    DialogFactory.showError("Missing File", "Please select a valid file (.elf, .AppImage, or .pkg) before continuing.");
                    return false;
                }
                return true;
            }
            case 1 -> {
                if (ValidationUtils.isBlank(request.getAppName())) {
                    DialogFactory.showError("Missing Information", "Please enter the application name before continuing.");
                    return false;
                }
                return true;
            }
            default -> {
                return true;
            }
        }
    }

    private void showStep(int index) {
        currentStep = index;
        contentHost.getChildren().setAll(buildStep(index));
        updateNavState();
    }

    private Node buildStep(int index) {
        return switch (index) {
            case 0 -> buildFileSelectionStep();
            case 1 -> buildMetadataStep();
            case 2 -> buildIconStep();
            case 3 -> buildIntegrationOptionsStep();
            case 4 -> buildInstallProgressStep();
            case 5 -> buildFinishStep();
            default -> new Label("Unknown step");
        };
    }

    // ----------------------------------------------------------------- step 0: file selection

    private Node buildFileSelectionStep() {
        VBox box = new VBox(16);
        box.getStyleClass().add("wizard-body");

        Label title = new Label("Choose the file to install");
        title.getStyleClass().add("section-title");
        Label hint = new Label("Supported types: executable binaries (.elf or no extension), AppImage (.AppImage), or native packages (.pkg).");
        hint.getStyleClass().add("hint-text");
        hint.setWrapText(true);

        VBox dropZone = new VBox(10);
        dropZone.getStyleClass().add("drop-zone");
        dropZone.setAlignment(Pos.CENTER);
        dropZone.setPrefHeight(160);
        Label dropLabel = new Label("Drag & drop a file here");
        Label pathLabel = new Label(ValidationUtils.isBlank(request.getSourceFilePath()) ? "No file selected" : request.getSourceFilePath());
        pathLabel.getStyleClass().add("hint-text");
        pathLabel.setWrapText(true);

        Button browseButton = new Button("Browse...");
        browseButton.getStyleClass().add("button-secondary");
        browseButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select application file");
            File selected = chooser.showOpenDialog(stage);
            if (selected != null) {
                request.setSourceFilePath(selected.getAbsolutePath());
                pathLabel.setText(selected.getAbsolutePath());
                prefillAppName(selected);
            }
        });

        dropZone.getChildren().addAll(dropLabel, browseButton, pathLabel);
        dropZone.setOnDragOver(event -> {
            if (event.getGestureSource() != dropZone && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                if (!dropZone.getStyleClass().contains("drop-zone-active")) {
                    dropZone.getStyleClass().add("drop-zone-active");
                }
            }
            event.consume();
        });
        dropZone.setOnDragExited(event -> dropZone.getStyleClass().remove("drop-zone-active"));
        dropZone.setOnDragDropped(event -> {
            var db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                File file = db.getFiles().get(0);
                request.setSourceFilePath(file.getAbsolutePath());
                pathLabel.setText(file.getAbsolutePath());
                prefillAppName(file);
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        box.getChildren().addAll(title, hint, dropZone);
        return box;
    }

    private void prefillAppName(File file) {
        if (ValidationUtils.isBlank(request.getAppName())) {
            String baseName = ValidationUtils.baseNameWithoutExtension(file.getName());
            request.setAppName(baseName);
        }
    }

    // ----------------------------------------------------------------- step 1: metadata

    private Node buildMetadataStep() {
        VBox box = new VBox(16);
        box.getStyleClass().add("wizard-body");

        Label title = new Label("Application details");
        title.getStyleClass().add("section-title");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);

        TextField nameField = new TextField(request.getAppName());
        nameField.textProperty().addListener((obs, o, n) -> request.setAppName(n));

        TextField commentField = new TextField(request.getAppComment());
        commentField.setPromptText("Short description shown in the application menu");
        commentField.textProperty().addListener((obs, o, n) -> request.setAppComment(n));

        ComboBox<String> categoryBox = new ComboBox<>();
        categoryBox.getItems().addAll("Utility", "Development", "Game", "Graphics", "Network",
                "Office", "AudioVideo", "System", "Education");
        categoryBox.setValue(request.getAppCategory());
        categoryBox.valueProperty().addListener((obs, o, n) -> request.setAppCategory(n));

        grid.addRow(0, new Label("Name:"), nameField);
        grid.addRow(1, new Label("Comment:"), commentField);
        grid.addRow(2, new Label("Category:"), categoryBox);
        GridPane.setHgrow(nameField, Priority.ALWAYS);
        GridPane.setHgrow(commentField, Priority.ALWAYS);

        box.getChildren().addAll(title, grid);
        return box;
    }

    // ----------------------------------------------------------------- step 2: icon

    private Node buildIconStep() {
        VBox box = new VBox(16);
        box.getStyleClass().add("wizard-body");

        Label title = new Label("Application icon");
        title.getStyleClass().add("section-title");

        iconToggleGroup = new ToggleGroup();
        RadioButton autoSearch = new RadioButton("Search and download automatically (based on the application name)");
        RadioButton localOrUrl = new RadioButton("Use a local image file or a web URL");
        RadioButton useDefault = new RadioButton("Use the default system icon");
        autoSearch.setToggleGroup(iconToggleGroup);
        localOrUrl.setToggleGroup(iconToggleGroup);
        useDefault.setToggleGroup(iconToggleGroup);

        switch (request.getIconMode()) {
            case AUTO_SEARCH -> autoSearch.setSelected(true);
            case LOCAL_OR_URL -> localOrUrl.setSelected(true);
            default -> useDefault.setSelected(true);
        }

        iconInputField = new TextField(request.getIconUserInput());
        iconInputField.setPromptText("Image URL (https://...) or local file path");
        iconInputField.textProperty().addListener((obs, o, n) -> request.setIconUserInput(n));
        iconInputField.disableProperty().bind(localOrUrl.selectedProperty().not());

        Button chooseImageButton = new Button("Choose image...");
        chooseImageButton.getStyleClass().add("button-secondary");
        chooseImageButton.disableProperty().bind(localOrUrl.selectedProperty().not());
        chooseImageButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select icon image");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.svg"));
            File selected = chooser.showOpenDialog(stage);
            if (selected != null) {
                iconInputField.setText(selected.getAbsolutePath());
                updateIconPreview(selected.getAbsolutePath());
            }
        });

        HBox iconInputRow = new HBox(10, iconInputField, chooseImageButton);
        HBox.setHgrow(iconInputField, Priority.ALWAYS);

        iconPreview = new ImageView();
        iconPreview.setFitWidth(64);
        iconPreview.setFitHeight(64);
        iconPreview.setPreserveRatio(true);

        VBox dropZone = new VBox(8, new Label("Drag & drop an image here"), iconPreview);
        dropZone.getStyleClass().add("drop-zone");
        dropZone.setAlignment(Pos.CENTER);
        dropZone.setPrefHeight(110);
        dropZone.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            event.consume();
        });
        dropZone.setOnDragDropped(event -> {
            var db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles() && !db.getFiles().isEmpty()) {
                File file = db.getFiles().get(0);
                iconInputField.setText(file.getAbsolutePath());
                localOrUrl.setSelected(true);
                updateIconPreview(file.getAbsolutePath());
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        autoSearch.setOnAction(e -> request.setIconMode(IconManager.Mode.AUTO_SEARCH));
        localOrUrl.setOnAction(e -> request.setIconMode(IconManager.Mode.LOCAL_OR_URL));
        useDefault.setOnAction(e -> request.setIconMode(IconManager.Mode.DEFAULT));

        VBox options = new VBox(10, autoSearch, localOrUrl, iconInputRow, useDefault);
        box.getChildren().addAll(title, options, dropZone);
        return box;
    }

    private void updateIconPreview(String path) {
        try {
            iconPreview.setImage(new Image(new File(path).toURI().toString(), 64, 64, true, true));
        } catch (Exception ignored) {
            // Preview is a convenience feature only; ignore unsupported formats (e.g. raw SVG).
        }
    }

    // ----------------------------------------------------------------- step 3: integration options

    private Node buildIntegrationOptionsStep() {
        VBox box = new VBox(16);
        box.getStyleClass().add("wizard-body");

        Label title = new Label("System detection & integration options");
        title.getStyleClass().add("section-title");

        DistributionDetector distro = installerService.getDistributionDetector();
        PackageManager pm = installerService.getPackageManager();
        LinuxSystemDetector.DesktopEnvironment de = LinuxSystemDetector.getInstance().detectDesktopEnvironment();

        GridPane info = new GridPane();
        info.setHgap(12);
        info.setVgap(8);
        info.addRow(0, new Label("Distribution:"), new Label(distro.getPrettyName()));
        info.addRow(1, new Label("Package manager:"), new Label(pm.isAvailable() ? pm.getPrimaryType().toString() : "None detected"));
        info.addRow(2, new Label("Desktop environment:"), new Label(de.toString()));

        if (!pm.isAvailable()) {
            Label warning = new Label("No supported package manager (pacman, apt, dnf, zypper) was found. "
                    + "Dependency installation will be skipped where required, but the file itself can still be installed.");
            warning.getStyleClass().add("hint-text");
            warning.setWrapText(true);
            info.add(warning, 0, 3, 2, 1);
        }

        copySupportFilesCheck = new CheckBox("Copy the entire source folder (for executables that need accompanying files)");
        copySupportFilesCheck.setSelected(request.isCopySupportFiles());
        copySupportFilesCheck.selectedProperty().addListener((obs, o, n) -> request.setCopySupportFiles(n));

        desktopShortcutCheck = new CheckBox("Also create a shortcut on the Desktop");
        desktopShortcutCheck.setSelected(request.isCreateDesktopShortcut());
        desktopShortcutCheck.selectedProperty().addListener((obs, o, n) -> request.setCreateDesktopShortcut(n));

        box.getChildren().addAll(title, info, new Separator(), copySupportFilesCheck, desktopShortcutCheck);
        return box;
    }

    // ----------------------------------------------------------------- step 4: install progress

    private Node buildInstallProgressStep() {
        VBox box = new VBox(16);
        box.getStyleClass().add("wizard-body");

        Label title = new Label("Installing...");
        title.getStyleClass().add("section-title");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(36, 36);

        logArea = new TextArea();
        logArea.getStyleClass().add("log-area");
        logArea.setEditable(false);
        logArea.setWrapText(true);
        VBox.setVgrow(logArea, Priority.ALWAYS);

        box.getChildren().addAll(title, spinner, logArea);
        return box;
    }

    private void runInstallation() {
        java.util.function.Consumer<String> uiLogListener = line -> Platform.runLater(() -> {
            if (logArea != null) {
                logArea.appendText(line + System.lineSeparator());
            }
        });
        logger.addListener(uiLogListener);

        Task<InstallationResult> task = new Task<>() {
            @Override
            protected InstallationResult call() {
                return installerService.install(request);
            }
        };
        task.setOnSucceeded(e -> {
            logger.removeListener(uiLogListener);
            lastResult = task.getValue();
            showStep(5);
        });
        task.setOnFailed(e -> {
            logger.removeListener(uiLogListener);
            Throwable ex = task.getException();
            DialogFactory.showErrorWithDetails("Installation Failed",
                    "An unexpected error occurred during installation.",
                    ex == null ? "Unknown error" : String.valueOf(ex));
            lastResult = new InstallationResult(false, null, null,
                    List.of("Unexpected error: " + (ex == null ? "unknown" : ex.getMessage())));
            showStep(5);
        });
        Thread thread = new Thread(task, "wizard-install-task");
        thread.setDaemon(true);
        thread.start();
    }

    // ----------------------------------------------------------------- step 5: finish

    private Node buildFinishStep() {
        VBox box = new VBox(16);
        box.getStyleClass().add("wizard-body");

        boolean success = lastResult != null && lastResult.isSuccess();
        Label title = new Label(success ? "Installation completed successfully!" : "Installation finished with errors");
        title.getStyleClass().add("section-title");

        TextArea summary = new TextArea();
        summary.setEditable(false);
        summary.setWrapText(true);
        summary.getStyleClass().add("log-area");
        VBox.setVgrow(summary, Priority.ALWAYS);
        if (lastResult != null) {
            summary.setText(String.join(System.lineSeparator(), lastResult.getMessages()));
        }

        box.getChildren().addAll(title, summary);
        return box;
    }
}
