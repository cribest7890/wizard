package com.wizard.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * Small collection of validation and path-normalisation helpers.
 * Mirrors the sanitisation performed by the original bash script
 * (quote stripping, {@code ~} expansion, extension parsing, etc.).
 */
public final class ValidationUtils {

    private static final Pattern SAFE_FILENAME = Pattern.compile("[^A-Za-z0-9._-]");

    private ValidationUtils() {
    }

    /** Strips leading/trailing quotes (as produced by drag & drop in some file managers) and expands {@code ~}. */
    public static String expandUserPath(String rawPath) {
        if (rawPath == null) {
            return "";
        }
        String path = rawPath.trim();
        if (path.length() >= 2 && ((path.startsWith("'") && path.endsWith("'"))
                || (path.startsWith("\"") && path.endsWith("\"")))) {
            path = path.substring(1, path.length() - 1);
        }
        path = path.replace("\\ ", " "); // undo shell-style space escaping from some drag & drop sources
        if (path.startsWith("~")) {
            String home = System.getProperty("user.home");
            path = home + path.substring(1);
        }
        return path;
    }

    public static boolean isValidUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    public static String baseNameWithoutExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public static String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "application";
        }
        String cleaned = SAFE_FILENAME.matcher(name.trim().replace(' ', '-')).replaceAll("");
        return cleaned.isBlank() ? "application" : cleaned;
    }

    public static Path resolveExisting(String rawPath) {
        return Paths.get(expandUserPath(rawPath));
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
