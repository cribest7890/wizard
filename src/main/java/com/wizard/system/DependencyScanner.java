package com.wizard.system;

import com.wizard.logging.LoggerService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans an executable binary for missing shared library dependencies using {@code ldd},
 * mirroring the dependency-detection step of the original installer script.
 */
public final class DependencyScanner {

    private static final Pattern NOT_FOUND_LINE = Pattern.compile("^\\s*(\\S+)\\s*=>\\s*not found");
    private final LoggerService logger = LoggerService.getInstance();

    /** Returns the list of shared library names reported as "not found" by ldd. Empty if none, or if ldd is unavailable. */
    public List<String> findMissingLibraries(String executablePath) {
        List<String> missing = new ArrayList<>();
        if (!PackageManager.isOnPath("ldd")) {
            logger.warn("'ldd' is not available on this system; skipping dependency analysis.");
            return missing;
        }
        try {
            Process process = new ProcessBuilder("ldd", executablePath)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            process.waitFor(30, TimeUnit.SECONDS);

            for (String line : output.split("\\R")) {
                Matcher m = NOT_FOUND_LINE.matcher(line);
                if (m.find()) {
                    missing.add(m.group(1));
                }
            }
            if (missing.isEmpty()) {
                logger.info("No missing shared library dependencies detected.");
            } else {
                logger.warn("Missing shared libraries detected: " + String.join(", ", missing));
            }
        } catch (Exception e) {
            logger.warn("Dependency scan could not be completed: " + e.getMessage());
        }
        return missing;
    }
}
