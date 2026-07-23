package com.wizard.ui;

import javafx.scene.Scene;

import java.util.Objects;

/** Applies the wizard's visual theme (a single modern flat stylesheet) to a JavaFX Scene. */
public final class ThemeManager {

    private static final String STYLESHEET_PATH = "/css/style.css";

    public void applyTheme(Scene scene) {
        String resource = Objects.requireNonNull(getClass().getResource(STYLESHEET_PATH),
                "Stylesheet not found on classpath: " + STYLESHEET_PATH).toExternalForm();
        scene.getStylesheets().add(resource);
    }
}
