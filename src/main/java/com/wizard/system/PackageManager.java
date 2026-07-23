package com.wizard.system;

import com.wizard.logging.LoggerService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Abstracts over the various Linux package managers so callers do not need to
 * know which distribution they are running on. Privilege escalation for
 * install operations uses {@code pkexec} (PolicyKit), the freedesktop-standard
 * graphical replacement for {@code sudo} that is available on essentially
 * every modern desktop distribution.
 */
public final class PackageManager {

    public enum Type { PACMAN, YAY, APT, APT_GET, DNF, ZYPPER, NONE }

    private final LoggerService logger = LoggerService.getInstance();
    private final Type primaryType;
    private final boolean yayAvailable;

    public PackageManager(DistributionDetector distro) {
        this.yayAvailable = isOnPath("yay");
        this.primaryType = detectPrimary(distro);
    }

    private Type detectPrimary(DistributionDetector distro) {
        if (distro.isArchFamily() && isOnPath("pacman")) return Type.PACMAN;
        if (distro.isDebianFamily()) {
            if (isOnPath("apt")) return Type.APT;
            if (isOnPath("apt-get")) return Type.APT_GET;
        }
        if (distro.isFedoraFamily() && isOnPath("dnf")) return Type.DNF;
        if (distro.isOpenSuseFamily() && isOnPath("zypper")) return Type.ZYPPER;

        // Fall back to whatever binary is actually present, regardless of the
        // detected distribution family (covers freedesktop-compatible derivatives).
        if (isOnPath("pacman")) return Type.PACMAN;
        if (isOnPath("apt")) return Type.APT;
        if (isOnPath("apt-get")) return Type.APT_GET;
        if (isOnPath("dnf")) return Type.DNF;
        if (isOnPath("zypper")) return Type.ZYPPER;
        return Type.NONE;
    }

    public Type getPrimaryType() {
        return primaryType;
    }

    public boolean isAvailable() {
        return primaryType != Type.NONE;
    }

    public boolean isYayAvailable() {
        return yayAvailable;
    }

    public static boolean isOnPath(String binary) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String dir : path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (new java.io.File(dir, binary).canExecute()) {
                return true;
            }
        }
        return false;
    }

    /** Installs a local package file (e.g. Arch {@code .pkg.tar.zst}). */
    public CommandResult installLocalPackageFile(String filePath) {
        return switch (primaryType) {
            case PACMAN -> runPrivileged(List.of("pacman", "-U", "--needed", "--noconfirm", filePath));
            case APT -> runPrivileged(List.of("apt", "install", "-y", filePath));
            case APT_GET -> runPrivileged(List.of("apt-get", "install", "-y", filePath));
            case DNF -> runPrivileged(List.of("dnf", "install", "-y", filePath));
            case ZYPPER -> runPrivileged(List.of("zypper", "--non-interactive", "install", filePath));
            default -> CommandResult.unavailable("No supported package manager was detected on this system.");
        };
    }

    /** Installs one or more named repository packages (e.g. resolved missing library dependencies). */
    public CommandResult installPackages(List<String> packageNames) {
        if (packageNames == null || packageNames.isEmpty()) {
            return CommandResult.success("");
        }
        List<String> cmd = new ArrayList<>();
        switch (primaryType) {
            case PACMAN -> {
                if (yayAvailable) {
                    cmd.add("yay");
                    cmd.add("-S");
                    cmd.add("--needed");
                    cmd.add("--noconfirm");
                } else {
                    cmd.add("pacman");
                    cmd.add("-S");
                    cmd.add("--needed");
                    cmd.add("--noconfirm");
                }
                cmd.addAll(packageNames);
                // yay should not run as root; only wrap with pkexec for plain pacman.
                return yayAvailable ? runUnprivileged(cmd) : runPrivileged(cmd);
            }
            case APT -> {
                cmd.add("apt"); cmd.add("install"); cmd.add("-y"); cmd.addAll(packageNames);
                return runPrivileged(cmd);
            }
            case APT_GET -> {
                cmd.add("apt-get"); cmd.add("install"); cmd.add("-y"); cmd.addAll(packageNames);
                return runPrivileged(cmd);
            }
            case DNF -> {
                cmd.add("dnf"); cmd.add("install"); cmd.add("-y"); cmd.addAll(packageNames);
                return runPrivileged(cmd);
            }
            case ZYPPER -> {
                cmd.add("zypper"); cmd.add("--non-interactive"); cmd.add("install"); cmd.addAll(packageNames);
                return runPrivileged(cmd);
            }
            default -> {
                return CommandResult.unavailable("No supported package manager was detected on this system.");
            }
        }
    }

    /**
     * Attempts to identify which package provides a missing shared library,
     * using each distribution's native "file to package" lookup tool when present.
     * This is best-effort: on many systems these auxiliary tools (pkgfile, apt-file, ...)
     * are not installed, in which case {@code null} is returned and the caller should
     * inform the user that manual resolution is required.
     */
    public String findPackageProviding(String libraryName) {
        try {
            return switch (primaryType) {
                case PACMAN -> runAndReadFirstLine(List.of("pkgfile", "-b", libraryName));
                case APT, APT_GET -> runAndReadFirstLine(List.of("apt-file", "search", libraryName));
                case DNF -> runAndReadFirstLine(List.of("dnf", "provides", "*/" + libraryName));
                case ZYPPER -> runAndReadFirstLine(List.of("zypper", "--non-interactive", "what-provides", libraryName));
                default -> null;
            };
        } catch (Exception e) {
            logger.warn("Could not resolve package for library '" + libraryName + "': " + e.getMessage());
            return null;
        }
    }

    private String runAndReadFirstLine(List<String> command) throws IOException, InterruptedException {
        if (!isOnPath(command.get(0))) {
            return null;
        }
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        process.waitFor(10, TimeUnit.SECONDS);
        if (output.isBlank()) return null;
        String firstLine = output.lines().findFirst().orElse("");
        // Rough extraction: first whitespace/colon-delimited token containing the package name.
        return firstLine.split("[\\s:]+")[0].trim().isEmpty() ? null : firstLine.split("[\\s:]+")[0].trim();
    }

    private CommandResult runPrivileged(List<String> command) {
        List<String> full = new ArrayList<>();
        full.add("pkexec");
        full.addAll(command);
        return runUnprivileged(full);
    }

    private CommandResult runUnprivileged(List<String> command) {
        logger.info("Executing: " + String.join(" ", command));
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            int exitCode = finished ? process.exitValue() : -1;
            if (!finished) {
                process.destroyForcibly();
                return CommandResult.failure("Command timed out: " + String.join(" ", command));
            }
            if (exitCode != 0) {
                logger.error("Command failed (exit " + exitCode + "): " + output);
                return CommandResult.failure(output.isBlank() ? "Command exited with code " + exitCode : output);
            }
            logger.info(output.isBlank() ? "Command completed successfully." : output);
            return CommandResult.success(output);
        } catch (IOException e) {
            return CommandResult.failure("Could not execute command (is '" + command.get(0) + "' installed?): " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CommandResult.failure("Command was interrupted.");
        }
    }

    /** Simple immutable result of a shell command execution. */
    public static final class CommandResult {
        private final boolean ok;
        private final boolean unavailable;
        private final String message;

        private CommandResult(boolean ok, boolean unavailable, String message) {
            this.ok = ok;
            this.unavailable = unavailable;
            this.message = message;
        }

        static CommandResult success(String message) { return new CommandResult(true, false, message); }
        static CommandResult failure(String message) { return new CommandResult(false, false, message); }
        static CommandResult unavailable(String message) { return new CommandResult(false, true, message); }

        public boolean isOk() { return ok; }
        public boolean isUnavailable() { return unavailable; }
        public String getMessage() { return message; }
    }
}
