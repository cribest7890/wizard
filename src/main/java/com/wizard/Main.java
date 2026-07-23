package com.wizard;

import com.wizard.logging.LoggerService;
import javafx.application.Application;

/**
 * Application entry point.
 * <p>
 * Kept separate from {@link WizardApplication} (which extends {@code javafx.application.Application})
 * so that the fat-jar produced by the shade plugin can be launched with a plain
 * {@code Main-Class} manifest entry without tripping the JavaFX classpath/module checks
 * that can occur when the main class itself extends Application in some packaging setups.
 * <p>
 * This application deliberately supports Linux only. It is not designed to run on
 * Windows or macOS and will refuse to start on those platforms.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        String osName = System.getProperty("os.name", "").toLowerCase();

        if (!(osName.contains("nux") || osName.contains("nix") || osName.contains("aix"))) {
            System.err.println("=======================================================");
            System.err.println(" Application Wizard supports Linux only.");
            System.err.println(" Detected operating system: " + System.getProperty("os.name"));
            System.err.println(" This application will now exit.");
            System.err.println("=======================================================");
            System.exit(1);
            return;
        }

        LoggerService.getInstance().info("Starting Application Wizard on: " + System.getProperty("os.name"));
        Application.launch(WizardApplication.class, args);
    }
}
