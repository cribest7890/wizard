package com.wizard.system;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Identifies the running Linux distribution by parsing {@code /etc/os-release},
 * following the freedesktop.org standard so it works across the whole ecosystem
 * rather than assuming any single distribution.
 */
public final class DistributionDetector {

    public enum Distribution {
        ARCH, DEBIAN, UBUNTU, LINUX_MINT, POP_OS, FEDORA, OPENSUSE, UNKNOWN
    }

    private static final Path OS_RELEASE = Path.of("/etc/os-release");

    private final Distribution distribution;
    private final String prettyName;
    private final String idLike;

    public DistributionDetector() {
        Map<String, String> fields = parseOsRelease();
        this.prettyName = fields.getOrDefault("PRETTY_NAME", fields.getOrDefault("NAME", "Unknown Linux"));
        this.idLike = fields.getOrDefault("ID_LIKE", "");
        this.distribution = classify(fields.getOrDefault("ID", ""), idLike);
    }

    private Map<String, String> parseOsRelease() {
        Map<String, String> map = new HashMap<>();
        if (!Files.isReadable(OS_RELEASE)) {
            return map;
        }
        try {
            for (String line : Files.readAllLines(OS_RELEASE)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                    continue;
                }
                int eq = trimmed.indexOf('=');
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim().replaceAll("^\"|\"$", "");
                map.put(key, value);
            }
        } catch (Exception ignored) {
            // If /etc/os-release is unreadable we simply fall back to UNKNOWN.
        }
        return map;
    }

    private Distribution classify(String id, String idLike) {
        String i = id.toLowerCase(Locale.ROOT);
        String like = idLike.toLowerCase(Locale.ROOT);

        return switch (i) {
            case "arch" -> Distribution.ARCH;
            case "debian" -> Distribution.DEBIAN;
            case "ubuntu" -> Distribution.UBUNTU;
            case "linuxmint" -> Distribution.LINUX_MINT;
            case "pop" -> Distribution.POP_OS;
            case "fedora" -> Distribution.FEDORA;
            case "opensuse", "opensuse-leap", "opensuse-tumbleweed" -> Distribution.OPENSUSE;
            default -> classifyByFamily(i, like);
        };
    }

    private Distribution classifyByFamily(String id, String like) {
        if (id.contains("suse") || like.contains("suse")) return Distribution.OPENSUSE;
        if (id.contains("fedora") || like.contains("fedora") || like.contains("rhel")) return Distribution.FEDORA;
        if (id.contains("arch") || like.contains("arch")) return Distribution.ARCH;
        if (like.contains("ubuntu")) return Distribution.UBUNTU;
        if (like.contains("debian")) return Distribution.DEBIAN;
        return Distribution.UNKNOWN;
    }

    public Distribution getDistribution() {
        return distribution;
    }

    public String getPrettyName() {
        return prettyName;
    }

    public boolean isArchFamily() {
        return distribution == Distribution.ARCH;
    }

    public boolean isDebianFamily() {
        return distribution == Distribution.DEBIAN || distribution == Distribution.UBUNTU
                || distribution == Distribution.LINUX_MINT || distribution == Distribution.POP_OS
                || idLike.toLowerCase(Locale.ROOT).contains("debian");
    }

    public boolean isFedoraFamily() {
        return distribution == Distribution.FEDORA || idLike.toLowerCase(Locale.ROOT).contains("fedora")
                || idLike.toLowerCase(Locale.ROOT).contains("rhel");
    }

    public boolean isOpenSuseFamily() {
        return distribution == Distribution.OPENSUSE || idLike.toLowerCase(Locale.ROOT).contains("suse");
    }
}
