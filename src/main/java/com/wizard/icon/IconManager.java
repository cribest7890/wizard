package com.wizard.icon;

import com.wizard.logging.LoggerService;
import com.wizard.util.ValidationUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Resolves the final icon to associate with an installed application:
 * a locally copied custom image, a downloaded image, or the generic
 * freedesktop executable icon as a fallback.
 */
public final class IconManager {

    public static final String DEFAULT_SYSTEM_ICON = "application-x-executable";

    private final LoggerService logger = LoggerService.getInstance();
    private final ImageDownloader downloader = new ImageDownloader();

    public enum Mode { AUTO_SEARCH, LOCAL_OR_URL, DEFAULT }

    /**
     * Resolves the icon path to use, given the user's chosen mode and input.
     * Always returns a usable icon reference: either an absolute path to an image
     * file, or {@link #DEFAULT_SYSTEM_ICON} if nothing better could be resolved.
     */
    public String resolveIcon(Mode mode, String appName, String userInput, Path appDir) {
        try {
            Files.createDirectories(appDir);
        } catch (IOException e) {
            logger.warn("Could not create icon directory " + appDir + ": " + e.getMessage());
        }

        return switch (mode) {
            case AUTO_SEARCH -> {
                Path target = appDir.resolve("icon.png");
                if (downloader.searchAndDownload(appName, target)) {
                    yield target.toString();
                }
                logger.warn("Automatic icon search failed; using default system icon.");
                yield DEFAULT_SYSTEM_ICON;
            }
            case LOCAL_OR_URL -> resolveLocalOrUrl(userInput, appDir);
            case DEFAULT -> DEFAULT_SYSTEM_ICON;
        };
    }

    private String resolveLocalOrUrl(String userInput, Path appDir) {
        if (ValidationUtils.isBlank(userInput)) {
            return DEFAULT_SYSTEM_ICON;
        }
        String input = ValidationUtils.expandUserPath(userInput);

        if (ValidationUtils.isValidUrl(input)) {
            String ext = ValidationUtils.extensionOf(input);
            if (ext.isBlank()) ext = "png";
            Path target = appDir.resolve("icon_downloaded." + ext);
            if (downloader.downloadTo(input, target)) {
                return target.toString();
            }
            logger.warn("Could not download icon from provided URL; using default system icon.");
            return DEFAULT_SYSTEM_ICON;
        }

        Path localFile = java.nio.file.Paths.get(input);
        if (Files.isRegularFile(localFile)) {
            try {
                String ext = ValidationUtils.extensionOf(localFile.getFileName().toString());
                if (ext.isBlank()) ext = "png";
                Path target = appDir.resolve("icon_custom." + ext);
                Files.copy(localFile, target, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Custom icon registered: " + target);
                return target.toString();
            } catch (IOException e) {
                logger.warn("Could not copy custom icon: " + e.getMessage());
                return DEFAULT_SYSTEM_ICON;
            }
        }

        logger.warn("Icon input is neither a valid URL nor an existing file; using default system icon.");
        return DEFAULT_SYSTEM_ICON;
    }
}
