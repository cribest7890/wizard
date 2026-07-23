package com.wizard.logging;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Central logging facility. Writes to a rotating-free log file under
 * {@code ~/.local/share/wizard-app/logs/wizard.log} and echoes every entry
 * to any registered UI listeners so the wizard's log panel can show live output.
 */
public final class LoggerService {

    public enum Level { INFO, WARN, ERROR, SUCCESS }

    private static final LoggerService INSTANCE = new LoggerService();
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private PrintWriter fileWriter;

    private LoggerService() {
        try {
            Path dataHome = resolveDataHome();
            Path logDir = dataHome.resolve("wizard-app").resolve("logs");
            Files.createDirectories(logDir);
            Path logFile = logDir.resolve("wizard.log");
            fileWriter = new PrintWriter(Files.newBufferedWriter(logFile,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND), true);
        } catch (IOException e) {
            // Fall back to console-only logging if the log directory cannot be created.
            fileWriter = null;
        }
    }

    private static Path resolveDataHome() {
        String xdgData = System.getenv("XDG_DATA_HOME");
        if (xdgData != null && !xdgData.isBlank()) {
            return Paths.get(xdgData);
        }
        return Paths.get(System.getProperty("user.home"), ".local", "share");
    }

    public static LoggerService getInstance() {
        return INSTANCE;
    }

    public void addListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    public void info(String message) {
        log(Level.INFO, message);
    }

    public void warn(String message) {
        log(Level.WARN, message);
    }

    public void error(String message) {
        log(Level.ERROR, message);
    }

    public void success(String message) {
        log(Level.SUCCESS, message);
    }

    public synchronized void log(Level level, String message) {
        String line = "[" + LocalDateTime.now().format(TS) + "] [" + level + "] " + message;
        System.out.println(line);
        if (fileWriter != null) {
            fileWriter.println(line);
        }
        for (Consumer<String> listener : listeners) {
            listener.accept(line);
        }
    }
}
