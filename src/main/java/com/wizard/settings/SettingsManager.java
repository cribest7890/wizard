package com.wizard.settings;

import com.wizard.logging.LoggerService;
import com.wizard.system.LinuxSystemDetector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Persists Wizard application settings (including the AppImage "first run" flag)
 * to {@code ~/.config/wizard-app/settings.properties}, following the XDG Base
 * Directory Specification.
 */
public final class SettingsManager {

    private static final String FIRST_RUN_DONE_KEY = "first_run_done";
    private static final String APP_INSTALL_PATH_KEY = "app_install_path";

    private final LoggerService logger = LoggerService.getInstance();
    private final Path settingsFile;
    private final Properties properties = new Properties();

    public SettingsManager() {
        Path configDir = LinuxSystemDetector.getInstance().xdgConfigHome().resolve("wizard-app");
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            logger.warn("Could not create config directory: " + e.getMessage());
        }
        this.settingsFile = configDir.resolve("settings.properties");
        load();
    }

    private void load() {
        if (!Files.isRegularFile(settingsFile)) {
            return;
        }
        try (InputStream in = Files.newInputStream(settingsFile)) {
            properties.load(in);
        } catch (IOException e) {
            logger.warn("Could not load settings file: " + e.getMessage());
        }
    }

    public void save() {
        try (OutputStream out = Files.newOutputStream(settingsFile)) {
            properties.store(out, "Application Wizard settings");
        } catch (IOException e) {
            logger.warn("Could not save settings file: " + e.getMessage());
        }
    }

    public boolean isFirstRunDone() {
        return Boolean.parseBoolean(properties.getProperty(FIRST_RUN_DONE_KEY, "false"));
    }

    public void markFirstRunDone(String installedAppImagePath) {
        properties.setProperty(FIRST_RUN_DONE_KEY, "true");
        if (installedAppImagePath != null) {
            properties.setProperty(APP_INSTALL_PATH_KEY, installedAppImagePath);
        }
        save();
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
    }
}
