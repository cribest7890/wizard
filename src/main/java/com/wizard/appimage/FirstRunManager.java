package com.wizard.appimage;

import com.wizard.desktop.DesktopEntryService;
import com.wizard.logging.LoggerService;
import com.wizard.settings.SettingsManager;
import com.wizard.system.LinuxSystemDetector;
import com.wizard.util.FileManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Handles the "first run from AppImage" flow for the Wizard application itself:
 * <ol>
 *   <li>detect that we are running from a mounted AppImage;</li>
 *   <li>copy the AppImage and its icon to a permanent location;</li>
 *   <li>create the {@code .desktop} menu entry, registering the app in the system menu;</li>
 *   <li>persist the "first run done" flag;</li>
 *   <li>only once every previous step has verifiably succeeded, remove the original
 *       AppImage file (which may sit in e.g. {@code ~/Downloads}).</li>
 * </ol>
 * If any step fails, the original AppImage is never deleted and the flag is never set,
 * so the integration will simply be retried on the next launch.
 */
public final class FirstRunManager {

    public static final String APP_SLUG = "application-wizard";
    public static final String APP_DISPLAY_NAME = "Application Wizard";

    private final LoggerService logger = LoggerService.getInstance();
    private final AppImageService appImageService = new AppImageService();
    private final SettingsManager settingsManager;
    private final DesktopEntryService desktopEntryService = new DesktopEntryService();
    private final FileManager fileManager = new FileManager();

    public record FirstRunResult(boolean attempted, boolean success, String message) {
        static FirstRunResult skipped(String reason) { return new FirstRunResult(false, true, reason); }
        static FirstRunResult ok(String message) { return new FirstRunResult(true, true, message); }
        static FirstRunResult failed(String message) { return new FirstRunResult(true, false, message); }
    }

    public FirstRunManager(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    public FirstRunResult performFirstRunIntegrationIfNeeded() {
        if (settingsManager.isFirstRunDone()) {
            return FirstRunResult.skipped("First-run integration already completed previously.");
        }
        if (!appImageService.isRunningFromAppImage()) {
            return FirstRunResult.skipped("Not running from an AppImage; skipping self-integration.");
        }

        Path originalAppImage = appImageService.getAppImagePath();
        Path appDir = appImageService.getAppDir();
        if (originalAppImage == null || appDir == null) {
            return FirstRunResult.failed("Could not determine AppImage location from the environment.");
        }

        try {
            Path installDir = LinuxSystemDetector.getInstance().xdgDataHome().resolve("wizard-app");
            Files.createDirectories(installDir);

            // 1. Copy the AppImage itself to a permanent, stable location.
            Path permanentAppImage = installDir.resolve("ApplicationWizard.AppImage");
            if (!originalAppImage.equals(permanentAppImage)) {
                Files.copy(originalAppImage, permanentAppImage, StandardCopyOption.REPLACE_EXISTING);
                permanentAppImage.toFile().setExecutable(true, false);
            }
            if (!Files.isExecutable(permanentAppImage)) {
                return FirstRunResult.failed("Failed to install a runnable copy of the AppImage.");
            }

            // 2. Copy the bundled icon resource next to it, if present in the AppDir.
            Path iconSource = appDir.resolve("wizard-app.png");
            Path iconTarget = installDir.resolve("wizard-app.png");
            String iconRef = "application-x-executable";
            if (Files.isRegularFile(iconSource)) {
                Files.copy(iconSource, iconTarget, StandardCopyOption.REPLACE_EXISTING);
                iconRef = iconTarget.toString();
            } else {
                logger.warn("Bundled icon not found in AppDir at " + iconSource + "; using default system icon.");
            }

            // 3. Create the .desktop menu entry.
            var request = new DesktopEntryService.DesktopEntryRequest(
                    APP_DISPLAY_NAME,
                    "Linux desktop installer wizard for third-party applications",
                    permanentAppImage.toString(),
                    iconRef,
                    "Utility;System;",
                    false
            );
            String content = desktopEntryService.buildEntryContent(request);
            Path desktopFile = desktopEntryService.installToApplicationsMenu(APP_SLUG, content);

            // 4. Verify everything required actually exists before touching the original file.
            boolean desktopOk = Files.isRegularFile(desktopFile);
            boolean execOk = Files.isExecutable(permanentAppImage);
            if (!desktopOk || !execOk) {
                return FirstRunResult.failed("Post-installation verification failed "
                        + "(desktop entry present: " + desktopOk + ", executable present: " + execOk + "). "
                        + "The original AppImage has been kept.");
            }

            // 5. Mark first run as complete and only now remove the original AppImage,
            //    but only if it differs from the permanent copy we just verified.
            settingsManager.markFirstRunDone(permanentAppImage.toString());
            if (!originalAppImage.equals(permanentAppImage)) {
                fileManager.deleteIfExists(originalAppImage);
            }

            return FirstRunResult.ok("Application Wizard has been installed and registered in your application menu.");
        } catch (IOException e) {
            logger.error("First-run integration failed: " + e.getMessage());
            return FirstRunResult.failed("First-run integration failed: " + e.getMessage()
                    + ". The original AppImage has not been removed.");
        }
    }
}
