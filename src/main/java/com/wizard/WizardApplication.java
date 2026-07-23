package com.wizard;

import com.wizard.appimage.FirstRunManager;
import com.wizard.logging.LoggerService;
import com.wizard.settings.SettingsManager;
import com.wizard.ui.DialogFactory;
import com.wizard.ui.ThemeManager;
import com.wizard.ui.WizardController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

/**
 * JavaFX application entry point. On first launch from a mounted AppImage,
 * transparently integrates the Wizard itself into the system (menu entry, icon,
 * permanent install location) before presenting the installer UI.
 */
public final class WizardApplication extends Application {

    private final LoggerService logger = LoggerService.getInstance();

    @Override
    public void start(Stage primaryStage) {
        performSelfIntegrationIfNeeded();

        WizardController controller = new WizardController(primaryStage);
        BorderPane root = controller.buildRoot();

        Scene scene = new Scene(root, 760, 560);
        new ThemeManager().applyTheme(scene);

        primaryStage.setTitle("Application Wizard");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(680);
        primaryStage.setMinHeight(500);
        loadWindowIcon(primaryStage);
        primaryStage.show();
    }

    private void performSelfIntegrationIfNeeded() {
        try {
            SettingsManager settingsManager = new SettingsManager();
            FirstRunManager firstRunManager = new FirstRunManager(settingsManager);
            FirstRunManager.FirstRunResult result = firstRunManager.performFirstRunIntegrationIfNeeded();

            if (result.attempted()) {
                if (result.success()) {
                    logger.success(result.message());
                    DialogFactory.showInfo("Setup Complete", result.message());
                } else {
                    logger.error(result.message());
                    DialogFactory.showWarning("Self-Integration Incomplete",
                            result.message() + "\n\nYou can continue using the Wizard normally; "
                                    + "this step will be retried automatically on the next launch.");
                }
            }
        } catch (Exception e) {
            logger.error("First-run check failed: " + e.getMessage());
        }
    }

    private void loadWindowIcon(Stage stage) {
        try {
            var stream = getClass().getResourceAsStream("/icons/wizard-icon.png");
            if (stream != null) {
                stage.getIcons().add(new Image(stream));
            }
        } catch (Exception ignored) {
            // Window icon is cosmetic only.
        }
    }
}
