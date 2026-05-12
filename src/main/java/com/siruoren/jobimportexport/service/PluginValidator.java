package com.siruoren.jobimportexport.service;

import hudson.PluginManager;
import hudson.PluginWrapper;
import jenkins.model.Jenkins;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

public class PluginValidator {

    private static final String PLUGIN_NAMESPACE = "hudson.plugins";
    private static final String ORG_JENKINSCI_PLUGINS = "org.jenkinsci.plugins";

    public List<String> checkMissingPlugins(byte[] xmlBytes) {
        List<String> missingPlugins = new ArrayList<>();

        try (ByteArrayInputStream is = new ByteArrayInputStream(xmlBytes)) {
            DocumentBuilder builder = SecureXmlParser.newSafeBuilder();
            Document doc = builder.parse(is);

            NodeList nodes = doc.getElementsByTagName("*");
            PluginManager pluginManager = Jenkins.get().getPluginManager();

            for (int i = 0; i < nodes.getLength(); i++) {
                Element element = (Element) nodes.item(i);
                String tagName = element.getTagName();

                String pluginName = extractPluginName(tagName);
                if (pluginName != null) {
                    String normalizedName = normalizePluginName(pluginName);
                    if (!isPluginInstalled(pluginManager, normalizedName)) {
                        if (!missingPlugins.contains(normalizedName)) {
                            missingPlugins.add(normalizedName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors, return empty list
        }

        return missingPlugins;
    }

    private String extractPluginName(String tagName) {
        if (tagName.contains(PLUGIN_NAMESPACE)) {
            return tagName.substring(PLUGIN_NAMESPACE.length() + 1);
        } else if (tagName.contains(ORG_JENKINSCI_PLUGINS)) {
            return tagName.substring(ORG_JENKINSCI_PLUGINS.length() + 1);
        }
        return null;
    }

    private String normalizePluginName(String name) {
        if (name.contains(".")) {
            String[] parts = name.split("\\.");
            if (parts.length >= 2) {
                return parts[0] + "-" + parts[1];
            }
        }
        return name.toLowerCase();
    }

    private boolean isPluginInstalled(PluginManager pluginManager, String pluginName) {
        PluginWrapper plugin = pluginManager.getPlugin(pluginName);
        return plugin != null && plugin.isActive();
    }
}