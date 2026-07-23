package com.wizard.model;

import com.wizard.icon.IconManager;

/** Aggregates all information collected by the wizard pages before installation starts. */
public final class InstallationRequest {

    private String sourceFilePath = "";
    private String appName = "";
    private String appComment = "";
    private String appCategory = "Utility";
    private IconManager.Mode iconMode = IconManager.Mode.DEFAULT;
    private String iconUserInput = "";
    private boolean copySupportFiles = false;
    private boolean createDesktopShortcut = false;

    public String getSourceFilePath() { return sourceFilePath; }
    public void setSourceFilePath(String sourceFilePath) { this.sourceFilePath = sourceFilePath; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public String getAppComment() { return appComment; }
    public void setAppComment(String appComment) { this.appComment = appComment; }

    public String getAppCategory() { return appCategory; }
    public void setAppCategory(String appCategory) { this.appCategory = appCategory; }

    public IconManager.Mode getIconMode() { return iconMode; }
    public void setIconMode(IconManager.Mode iconMode) { this.iconMode = iconMode; }

    public String getIconUserInput() { return iconUserInput; }
    public void setIconUserInput(String iconUserInput) { this.iconUserInput = iconUserInput; }

    public boolean isCopySupportFiles() { return copySupportFiles; }
    public void setCopySupportFiles(boolean copySupportFiles) { this.copySupportFiles = copySupportFiles; }

    public boolean isCreateDesktopShortcut() { return createDesktopShortcut; }
    public void setCreateDesktopShortcut(boolean createDesktopShortcut) { this.createDesktopShortcut = createDesktopShortcut; }
}
