package com.wizard.system;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Detects generic Linux/freedesktop facts: desktop environment and standard
 * XDG base directories. Distribution-specific detection lives in {@link DistributionDetector}.
 */
public final class LinuxSystemDetector {

    public enum DesktopEnvironment {
        GNOME, KDE, XFCE, CINNAMON, MATE, LXQT, BUDGIE, UNKNOWN
    }

    private static final LinuxSystemDetector INSTANCE = new LinuxSystemDetector();

    public static LinuxSystemDetector getInstance() {
        return INSTANCE;
    }

    public boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("nux");
    }

    public DesktopEnvironment detectDesktopEnvironment() {
        String xdgCurrent = envOrEmpty("XDG_CURRENT_DESKTOP").toUpperCase(Locale.ROOT);
        String desktopSession = envOrEmpty("DESKTOP_SESSION").toUpperCase(Locale.ROOT);
        String combined = xdgCurrent + " " + desktopSession;

        if (combined.contains("GNOME")) return DesktopEnvironment.GNOME;
        if (combined.contains("KDE") || combined.contains("PLASMA")) return DesktopEnvironment.KDE;
        if (combined.contains("XFCE")) return DesktopEnvironment.XFCE;
        if (combined.contains("CINNAMON")) return DesktopEnvironment.CINNAMON;
        if (combined.contains("MATE")) return DesktopEnvironment.MATE;
        if (combined.contains("LXQT")) return DesktopEnvironment.LXQT;
        if (combined.contains("BUDGIE")) return DesktopEnvironment.BUDGIE;
        return DesktopEnvironment.UNKNOWN;
    }

    private String envOrEmpty(String name) {
        String v = System.getenv(name);
        return v == null ? "" : v;
    }

    public Path homeDirectory() {
        return Paths.get(System.getProperty("user.home"));
    }

    public Path xdgDataHome() {
        String v = System.getenv("XDG_DATA_HOME");
        return (v != null && !v.isBlank()) ? Paths.get(v) : homeDirectory().resolve(".local/share");
    }

    public Path xdgConfigHome() {
        String v = System.getenv("XDG_CONFIG_HOME");
        return (v != null && !v.isBlank()) ? Paths.get(v) : homeDirectory().resolve(".config");
    }

    public Path applicationsDirectory() {
        return xdgDataHome().resolve("applications");
    }

    /**
     * Resolves the user's Desktop directory. Tries, in order: the XDG user-dirs
     * configuration ({@code xdg-user-dir DESKTOP} if available, then
     * {@code ~/.config/user-dirs.dirs}), then common localised fallbacks
     * ({@code ~/Desktop}, {@code ~/Scrivania}).
     */
    public Path resolveDesktopDirectory() {
        String fromXdgUserDir = tryXdgUserDirCommand();
        if (fromXdgUserDir != null) {
            Path p = Paths.get(fromXdgUserDir);
            if (p.toFile().isDirectory() || p.toFile().mkdirs()) {
                return p;
            }
        }

        Path fromConfigFile = tryUserDirsConfigFile();
        if (fromConfigFile != null) {
            return fromConfigFile;
        }

        Path standard = homeDirectory().resolve("Desktop");
        if (standard.toFile().isDirectory()) {
            return standard;
        }
        Path italian = homeDirectory().resolve("Scrivania");
        if (italian.toFile().isDirectory()) {
            return italian;
        }
        // Neither exists yet: default to the freedesktop-standard English name.
        return standard;
    }

    private String tryXdgUserDirCommand() {
        try {
            Process process = new ProcessBuilder("xdg-user-dir", "DESKTOP")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            boolean finished = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0 && !output.isBlank()) {
                return output;
            }
        } catch (Exception ignored) {
            // xdg-user-dir may not be installed on minimal systems; fall back silently.
        }
        return null;
    }

    private Path tryUserDirsConfigFile() {
        Path configFile = xdgConfigHome().resolve("user-dirs.dirs");
        if (!configFile.toFile().isFile()) {
            return null;
        }
        try {
            for (String line : java.nio.file.Files.readAllLines(configFile)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("XDG_DESKTOP_DIR")) {
                    int eq = trimmed.indexOf('=');
                    if (eq < 0) continue;
                    String value = trimmed.substring(eq + 1).trim()
                            .replace("\"", "")
                            .replace("$HOME", System.getProperty("user.home"));
                    return Paths.get(value);
                }
            }
        } catch (Exception ignored) {
            // Malformed or unreadable config: ignore and let caller fall back.
        }
        return null;
    }
}
