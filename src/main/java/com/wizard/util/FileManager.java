package com.wizard.util;

import com.wizard.logging.LoggerService;
import com.wizard.system.LinuxSystemDetector;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;

/**
 * Handles filesystem operations needed during installation: copying single files,
 * recursively copying whole directories (equivalent of {@code rsync -av}), and
 * setting up the per-application storage directory under
 * {@code ~/.local/share/wizard/<app-name>/}.
 */
public final class FileManager {

    private final LoggerService logger = LoggerService.getInstance();

    /** Base storage directory for all applications installed through the wizard. */
    public Path wizardDataRoot() {
        return LinuxSystemDetector.getInstance().xdgDataHome().resolve("wizard");
    }

    public Path applicationDirectory(String appFolderName) {
        Path dir = wizardDataRoot().resolve(appFolderName);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            logger.error("Could not create application directory " + dir + ": " + e.getMessage());
        }
        return dir;
    }

    public Path copySingleFile(Path source, Path destinationDir) throws IOException {
        Files.createDirectories(destinationDir);
        Path target = destinationDir.resolve(source.getFileName());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Copied " + source + " -> " + target);
        return target;
    }

    /** Recursively copies an entire directory tree, equivalent to {@code rsync -av <src>/ <dst>/}. */
    public void copyDirectoryRecursively(Path source, Path destination) throws IOException {
        Files.createDirectories(destination);
        try (var stream = Files.walk(source)) {
            stream.forEach(path -> {
                try {
                    Path relative = source.relativize(path);
                    Path target = destination.resolve(relative);
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOExceptionWrapper(e);
                }
            });
        } catch (UncheckedIOExceptionWrapper wrapper) {
            throw wrapper.cause;
        }
        logger.info("Copied directory " + source + " -> " + destination);
    }

    public void makeExecutable(Path file) {
        boolean ok = file.toFile().setExecutable(true, false);
        if (!ok) {
            logger.warn("Could not mark file as executable: " + file);
        } else {
            logger.info("Set executable permission on " + file);
        }
    }

    /** Deletes a file only if it exists; used for the "remove original AppImage after successful install" step. */
    public boolean deleteIfExists(Path file) {
        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                logger.info("Deleted: " + file);
            }
            return deleted;
        } catch (IOException e) {
            logger.warn("Could not delete " + file + ": " + e.getMessage());
            return false;
        }
    }

    public boolean exists(Path path) {
        return Files.exists(path);
    }

    private static final class UncheckedIOExceptionWrapper extends RuntimeException {
        final IOException cause;
        UncheckedIOExceptionWrapper(IOException cause) { this.cause = cause; }
    }
}
