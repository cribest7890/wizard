package com.wizard;

import com.wizard.desktop.DesktopEntryService;
import com.wizard.icon.IconManager;
import com.wizard.logging.LoggerService;
import com.wizard.model.InstallationRequest;
import com.wizard.model.InstallationResult;
import com.wizard.system.DependencyScanner;
import com.wizard.system.DistributionDetector;
import com.wizard.system.PackageManager;
import com.wizard.util.FileManager;
import com.wizard.util.ValidationUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the full installation workflow for a user-supplied application file
 * (.elf / generic binary, .AppImage, or a native distribution package), replicating
 * every step of the original bash installer while adapting each one to whichever
 * Linux distribution and package manager is actually present.
 */
public final class InstallerService {

    private final LoggerService logger = LoggerService.getInstance();
    private final FileManager fileManager = new FileManager();
    private final IconManager iconManager = new IconManager();
    private final DesktopEntryService desktopEntryService = new DesktopEntryService();
    private final DependencyScanner dependencyScanner = new DependencyScanner();
    private final DistributionDetector distributionDetector = new DistributionDetector();
    private final PackageManager packageManager = new PackageManager(distributionDetector);

    public DistributionDetector getDistributionDetector() {
        return distributionDetector;
    }

    public PackageManager getPackageManager() {
        return packageManager;
    }

    /** Runs the full install pipeline. Intended to be invoked off the JavaFX Application Thread. */
    public InstallationResult install(InstallationRequest request) {
        List<String> messages = new ArrayList<>();
        try {
            Path sourceFile = ValidationUtils.resolveExisting(request.getSourceFilePath());
            if (!Files.isRegularFile(sourceFile)) {
                return failure(messages, "The selected file does not exist: " + sourceFile);
            }

            String baseName = sourceFile.getFileName().toString();
            String extension = ValidationUtils.extensionOf(baseName);
            String appFolderName = ValidationUtils.sanitizeFileName(
                    ValidationUtils.baseNameWithoutExtension(baseName));

            Path appDir = fileManager.applicationDirectory(appFolderName);
            messages.add("Application data directory: " + appDir);

            String finalExecPath = switch (extension) {
                case "pkg" -> installNativePackage(sourceFile, messages);
                case "appimage" -> installAppImage(sourceFile, appDir, messages);
                default -> installGenericExecutable(sourceFile, appDir, request.isCopySupportFiles(), messages);
            };

            if (finalExecPath == null) {
                return failure(messages, "Installation could not be completed for " + baseName);
            }

            String iconRef = iconManager.resolveIcon(
                    request.getIconMode(), request.getAppName(), request.getIconUserInput(), appDir);
            messages.add("Icon resolved to: " + iconRef);

            var entryRequest = new DesktopEntryService.DesktopEntryRequest(
                    request.getAppName(),
                    request.getAppComment(),
                    finalExecPath,
                    iconRef,
                    ValidationUtils.isBlank(request.getAppCategory()) ? "Utility" : request.getAppCategory(),
                    false
            );
            String content = desktopEntryService.buildEntryContent(entryRequest);
            String slug = ValidationUtils.sanitizeFileName(appFolderName);

            Path menuEntry = desktopEntryService.installToApplicationsMenu(slug, content);
            messages.add("Menu entry created: " + menuEntry);

            String desktopShortcutPath = null;
            if (request.isCreateDesktopShortcut()) {
                Path shortcut = desktopEntryService.installToDesktop(slug, content);
                desktopShortcutPath = shortcut.toString();
                messages.add("Desktop shortcut created: " + shortcut);
            }

            logger.success("Installation completed successfully for " + request.getAppName());
            return new InstallationResult(true, menuEntry.toString(), desktopShortcutPath, messages);

        } catch (Exception e) {
            logger.error("Installation failed: " + e.getMessage());
            return failure(messages, "Unexpected error during installation: " + e.getMessage());
        }
    }

    private InstallationResult failure(List<String> messages, String reason) {
        messages.add("ERROR: " + reason);
        return new InstallationResult(false, null, null, messages);
    }

    private String installNativePackage(Path sourceFile, List<String> messages) {
        messages.add("Detected native distribution package (.pkg). Installing via " + packageManager.getPrimaryType() + "...");
        if (!packageManager.isAvailable()) {
            messages.add("No supported package manager detected; cannot install native package automatically.");
            return null;
        }
        PackageManager.CommandResult result = packageManager.installLocalPackageFile(sourceFile.toString());
        if (!result.isOk()) {
            messages.add("Package installation failed: " + result.getMessage());
            return null;
        }
        messages.add("Package installed successfully.");
        return ValidationUtils.baseNameWithoutExtension(sourceFile.getFileName().toString());
    }

    private String installAppImage(Path sourceFile, Path appDir, List<String> messages) throws IOException {
        messages.add("Detected AppImage file. Preparing runtime...");
        Path binDir = appDir.resolve("bin");
        Path finalExec = fileManager.copySingleFile(sourceFile, binDir);
        fileManager.makeExecutable(finalExec);

        // FUSE is required by most AppImages to mount themselves; check and offer to install it.
        boolean fuseLikelyPresent = PackageManager.isOnPath("fusermount") || PackageManager.isOnPath("fusermount3");
        if (!fuseLikelyPresent && packageManager.isAvailable()) {
            messages.add("FUSE does not appear to be installed; AppImages require it to run. Attempting installation...");
            List<String> fusePackages = switch (packageManager.getPrimaryType()) {
                case PACMAN -> List.of("fuse2");
                case APT, APT_GET -> List.of("libfuse2");
                case DNF -> List.of("fuse-libs");
                case ZYPPER -> List.of("libfuse2");
                default -> List.of();
            };
            if (!fusePackages.isEmpty()) {
                PackageManager.CommandResult fuseResult = packageManager.installPackages(fusePackages);
                messages.add(fuseResult.isOk() ? "FUSE installed successfully." : "Could not install FUSE automatically: " + fuseResult.getMessage());
            }
        }
        return finalExec.toString();
    }

    private String installGenericExecutable(Path sourceFile, Path appDir, boolean copySupportFiles, List<String> messages) throws IOException {
        messages.add("Treating file as a generic executable (.elf / binary)...");
        Path finalExec;
        if (copySupportFiles) {
            Path sourceDir = sourceFile.getParent();
            messages.add("Copying entire source directory alongside the executable...");
            fileManager.copyDirectoryRecursively(sourceDir, appDir);
            finalExec = appDir.resolve(sourceFile.getFileName());
        } else {
            Path binDir = appDir.resolve("bin");
            finalExec = fileManager.copySingleFile(sourceFile, binDir);
        }
        fileManager.makeExecutable(finalExec);

        List<String> missingLibs = dependencyScanner.findMissingLibraries(finalExec.toString());
        if (!missingLibs.isEmpty()) {
            messages.add("Missing shared libraries detected: " + String.join(", ", missingLibs));
            List<String> packagesToInstall = new ArrayList<>();
            for (String lib : missingLibs) {
                String pkg = packageManager.findPackageProviding(lib);
                if (pkg != null) {
                    packagesToInstall.add(pkg);
                }
            }
            if (!packagesToInstall.isEmpty() && packageManager.isAvailable()) {
                messages.add("Installing resolved packages: " + String.join(", ", packagesToInstall));
                PackageManager.CommandResult result = packageManager.installPackages(packagesToInstall);
                messages.add(result.isOk() ? "Dependencies installed successfully." : "Dependency installation failed: " + result.getMessage());
            } else {
                messages.add("Could not automatically map missing libraries to packages; manual resolution may be required.");
            }
        } else {
            messages.add("No missing dependencies detected.");
        }
        return finalExec.toString();
    }
}
