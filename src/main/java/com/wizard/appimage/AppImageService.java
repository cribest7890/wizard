package com.wizard.appimage;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Detects whether the application is currently running from within a mounted
 * AppImage. The AppImage runtime exports {@code APPIMAGE} (absolute path to the
 * original .AppImage file) and {@code APPDIR} (the mount point of the squashfs
 * image) as environment variables for every process it launches.
 */
public final class AppImageService {

    public boolean isRunningFromAppImage() {
        return System.getenv("APPIMAGE") != null && System.getenv("APPDIR") != null;
    }

    /** Absolute path of the original .AppImage file, or {@code null} if not running from one. */
    public Path getAppImagePath() {
        String appImage = System.getenv("APPIMAGE");
        return appImage == null ? null : Paths.get(appImage);
    }

    /** Mount directory of the running AppImage (read-only squashfs contents), or {@code null}. */
    public Path getAppDir() {
        String appDir = System.getenv("APPDIR");
        return appDir == null ? null : Paths.get(appDir);
    }
}
