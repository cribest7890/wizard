package com.wizard.icon;

import com.wizard.logging.LoggerService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Downloads icon images from the web, either from an explicit URL or via a
 * best-effort automatic lookup based on the application name.
 */
public final class ImageDownloader {

    private final LoggerService logger = LoggerService.getInstance();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]");

    /** Downloads the resource at {@code url} to {@code targetFile}. Returns true on success. */
    public boolean downloadTo(String url, Path targetFile) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body().length > 0) {
                java.nio.file.Files.createDirectories(targetFile.getParent());
                java.nio.file.Files.write(targetFile, response.body());
                logger.info("Icon downloaded from " + url + " -> " + targetFile);
                return true;
            }
            logger.warn("Icon download failed (HTTP " + response.statusCode() + ") from " + url);
        } catch (IOException | InterruptedException e) {
            logger.warn("Icon download error from " + url + ": " + e.getMessage());
        }
        return false;
    }

    /**
     * Best-effort automatic icon lookup based on the application name, using the
     * Clearbit public logo API as a heuristic (guessing a "{name}.com" domain).
     * This is a convenience feature only: if it fails, the caller should fall back
     * to a manual URL/file selection or the default system icon.
     */
    public boolean searchAndDownload(String appName, Path targetFile) {
        String slug = NON_ALNUM.matcher(appName.toLowerCase(Locale.ROOT).trim().replace(" ", "")).replaceAll("");
        if (slug.isBlank()) {
            return false;
        }
        String guessedUrl = "https://logo.clearbit.com/" + slug + ".com";
        logger.info("Attempting automatic icon lookup for '" + appName + "' via " + guessedUrl);
        return downloadTo(guessedUrl, targetFile);
    }
}
