package com.wizard.desktop;

import com.wizard.logging.LoggerService;
import com.wizard.system.LinuxSystemDetector;
import com.wizard.util.ValidationUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Creates freedesktop.org-compliant {@code .desktop} launcher files and installs
 * them into the applications menu and/or the user's physical Desktop folder.
 * This format is understood by every major desktop environment (GNOME, KDE Plasma,
 * XFCE, Cinnamon, MATE, and derivatives).
 */
public final class DesktopEntryService {

    private final LoggerService logger = LoggerService.getInstance();
    private final LinuxSystemDetector systemDetector = LinuxSystemDetector.getInstance();

    public record DesktopEntryRequest(
            String appName,
            String comment,
            String execPath,
            String iconPath,
            String category,
            boolean terminal
    ) {}

    public String buildEntryContent(DesktopEntryRequest request) {
        String category = ValidationUtils.isBlank(request.category()) ? "Utility" : request.category();
        return "[Desktop Entry]\n"
                + "Type=Application\n"
                + "Name=" + escape(request.appName()) + "\n"
                + "Comment=" + escape(request.comment()) + "\n"
                + "Exec=" + escapeExec(request.execPath()) + "\n"
                + "Icon=" + escape(request.iconPath()) + "\n"
                + "Terminal=" + request.terminal() + "\n"
                + "Categories=" + escape(category) + ";\n";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\n", " ").trim();
    }

    private String escapeExec(String execPath) {
        String cleaned = escape(execPath);
        // Quote the exec path if it contains spaces, per the Desktop Entry Specification.
        return cleaned.contains(" ") ? "\"" + cleaned + "\"" : cleaned;
    }

    /** Writes the entry into {@code ~/.local/share/applications/<slug>.desktop} (the application menu). */
    public Path installToApplicationsMenu(String slug, String content) throws IOException {
        Path dir = systemDetector.applicationsDirectory();
        Files.createDirectories(dir);
        Path target = dir.resolve(slug + ".desktop");
        writeAndMarkExecutable(target, content);
        logger.success("Application menu entry created: " + target);
        return target;
    }

    /** Writes the entry into the user's physical Desktop directory, if the user opted in. */
    public Path installToDesktop(String slug, String content) throws IOException {
        Path desktopDir = systemDetector.resolveDesktopDirectory();
        Files.createDirectories(desktopDir);
        Path target = desktopDir.resolve(slug + ".desktop");
        writeAndMarkExecutable(target, content);
        logger.success("Desktop shortcut created: " + target);
        return target;
    }

    private void writeAndMarkExecutable(Path target, String content) throws IOException {
        Files.writeString(target, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        target.toFile().setExecutable(true, false);
    }
}
