package com.wizard.model;

import java.util.List;

/** Outcome of an installation run: whether it succeeded and where the artifacts ended up. */
public final class InstallationResult {

    private final boolean success;
    private final String menuEntryPath;
    private final String desktopShortcutPath;
    private final List<String> messages;

    public InstallationResult(boolean success, String menuEntryPath, String desktopShortcutPath, List<String> messages) {
        this.success = success;
        this.menuEntryPath = menuEntryPath;
        this.desktopShortcutPath = desktopShortcutPath;
        this.messages = messages;
    }

    public boolean isSuccess() { return success; }
    public String getMenuEntryPath() { return menuEntryPath; }
    public String getDesktopShortcutPath() { return desktopShortcutPath; }
    public List<String> getMessages() { return messages; }
}
