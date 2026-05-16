package com.siruoren.jobimportexport;

import hudson.PluginWrapper;
import hudson.model.UpdateSite;
import hudson.model.UpdateSite.Plugin;
import jenkins.model.Jenkins;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages plugin suggestions with integration to Jenkins update center
 */
public class PluginSuggestionManager {
    private static final Logger LOGGER = Logger.getLogger(PluginSuggestionManager.class.getName());
    
    private static PluginSuggestionManager instance;

    private PluginSuggestionManager() {
    }

    public static synchronized PluginSuggestionManager getInstance() {
        if (instance == null) {
            instance = new PluginSuggestionManager();
        }
        return instance;
    }

    /**
     * Get plugin suggestions for missing plugins
     */
    public List<PluginSuggestion> getSuggestions(List<String> missingPlugins) {
        List<PluginSuggestion> suggestions = new ArrayList<>();
        Jenkins jenkins = Jenkins.getInstance();
        
        if (jenkins == null) {
            LOGGER.warning("Jenkins instance is null");
            return suggestions;
        }

        UpdateSite updateSite = jenkins.getUpdateCenter().getSite("default");
        if (updateSite == null) {
            LOGGER.warning("Update site is null");
            return suggestions;
        }

        for (String pluginShortName : missingPlugins) {
            PluginSuggestion suggestion = createSuggestion(pluginShortName, updateSite);
            if (suggestion != null) {
                suggestions.add(suggestion);
            } else {
                // Fallback if plugin not found in update center
                PluginSuggestion fallback = new PluginSuggestion(pluginShortName);
                fallback.setDisplayName(pluginShortName);
                suggestions.add(fallback);
            }
        }

        return suggestions;
    }

    /**
     * Check if a plugin is already installed
     */
    public boolean isPluginInstalled(String shortName) {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            return false;
        }
        
        for (PluginWrapper plugin : jenkins.getPluginManager().getPlugins()) {
            if (plugin.getShortName().equals(shortName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Install a plugin
     */
    public boolean installPlugin(String shortName) {
        try {
            Jenkins jenkins = Jenkins.getInstance();
            if (jenkins == null) {
                return false;
            }

            UpdateSite updateSite = jenkins.getUpdateCenter().getSite("default");
            if (updateSite == null) {
                LOGGER.warning("Update site is null");
                return false;
            }

            Plugin plugin = updateSite.getPlugin(shortName);
            if (plugin == null) {
                LOGGER.warning("Plugin not found in update center: " + shortName);
                return false;
            }

            plugin.deploy(true);
            LOGGER.info("Initiated installation of plugin: " + shortName);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to install plugin: " + shortName, e);
            return false;
        }
    }

    private PluginSuggestion createSuggestion(String shortName, UpdateSite updateSite) {
        try {
            Plugin plugin = updateSite.getPlugin(shortName);
            if (plugin == null) {
                return null;
            }

            PluginSuggestion suggestion = new PluginSuggestion(shortName);
            suggestion.setDisplayName(plugin.name);
            suggestion.setVersion(plugin.version.toString());
            suggestion.setDescription(plugin.getDisplayName());
            
            return suggestion;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to create suggestion for: " + shortName, e);
            return null;
        }
    }
}
