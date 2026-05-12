package com.siruoren.jobimportexport;

import java.io.Serializable;

/**
 * Plugin suggestion with metadata for auto-install
 */
public class PluginSuggestion implements Serializable {
    private static final long serialVersionUID = 1L;

    private String shortName;
    private String displayName;
    private String version;
    private String installUrl;
    private boolean required;
    private String description;

    public PluginSuggestion(String shortName) {
        this.shortName = shortName;
        this.required = true;
        this.installUrl = "/pluginManager/install?plugin." + shortName;
    }

    // Getters and Setters
    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getInstallUrl() {
        return installUrl;
    }

    public void setInstallUrl(String installUrl) {
        this.installUrl = installUrl;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
