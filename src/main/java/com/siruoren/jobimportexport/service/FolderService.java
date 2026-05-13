package com.siruoren.jobimportexport.service;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.ItemGroup;
import jenkins.model.Jenkins;
import jenkins.model.ModifiableTopLevelItemGroup;
import hudson.model.TopLevelItem;

import java.io.IOException;
import java.util.regex.Pattern;

public class FolderService {

    private static final Pattern INVALID_CHARS = Pattern.compile("[^a-zA-Z0-9_\\-]");

    public ItemGroup<?> ensureFolderPath(ItemGroup<?> baseGroup, String folderPath,
                                         boolean create) throws IOException {
        ItemGroup<?> current = baseGroup;

        if (folderPath == null || folderPath.trim().isEmpty()) {
            return current;
        }

        folderPath = normalizePath(folderPath);
        String[] parts = folderPath.split("/");
        StringBuilder currentPathBuilder = new StringBuilder();

        for (String raw : parts) {
            String part = sanitizeJobName(raw);
            if (part == null || part.isEmpty()) {
                continue;
            }

            if (currentPathBuilder.length() > 0) {
                currentPathBuilder.append("/");
            }
            currentPathBuilder.append(part);
            String currentPath = currentPathBuilder.toString();

            Item item = current.getItem(part);

            if (item == null) {
                if (!create) {
                    continue;
                }

                if (!hasFolderPlugin()) {
                    throw new IOException("缺少 Folder 插件，无法创建目录: " + part);
                }

                if (!(current instanceof ModifiableTopLevelItemGroup)) {
                    throw new IOException("目录不支持创建 Folder: " + part);
                }

                TopLevelItem folder = ((ModifiableTopLevelItemGroup) current)
                        .createProject(
                                Jenkins.get().getDescriptorByType(Folder.DescriptorImpl.class),
                                part,
                                false
                        );
                folder.save();
                item = folder;
            }

            if (!(item instanceof ItemGroup)) {
                throw new IOException("路径不是 Folder: " + currentPath);
            }

            current = (ItemGroup<?>) item;
        }

        return current;
    }

    public String buildCurrentPath(ItemGroup<?> baseGroup, String folderPath) {
        if (folderPath == null || folderPath.trim().isEmpty()) {
            if (baseGroup instanceof AbstractItem) {
                return ((AbstractItem) baseGroup).getFullName();
            }
            return "";
        }

        if (baseGroup instanceof AbstractItem) {
            String baseFullName = ((AbstractItem) baseGroup).getFullName();
            if (baseFullName != null && !baseFullName.isEmpty()) {
                return baseFullName + "/" + folderPath;
            }
        }
        return folderPath;
    }

    public boolean hasFolderPlugin() {
        return Jenkins.get().getPluginManager().getPlugin("cloudbees-folder") != null;
    }

    public String normalizePath(String path) {
        return path.replace("\\", "/")
                .replaceAll("^/+", "")
                .replaceAll("/+", "/");
    }

    public String sanitizeJobName(String name) {
        if (name == null) {
            return null;
        }
        String sanitized = INVALID_CHARS.matcher(name).replaceAll("_");
        return sanitized.isEmpty() ? null : sanitized;
    }

    public boolean isCreatableGroup(ItemGroup<?> g) {
        return g instanceof ModifiableTopLevelItemGroup
                && !(g instanceof AbstractItem
                        && g.getClass().getName().contains("ComputedFolder"));
    }

    public String buildFullPath(ItemGroup<?> baseGroup, String folderPath, String jobName) {
        StringBuilder sb = new StringBuilder();
        if (baseGroup instanceof AbstractItem) {
            String baseFullName = ((AbstractItem) baseGroup).getFullName();
            if (baseFullName != null && !baseFullName.isEmpty()) {
                sb.append(baseFullName).append("/");
            }
        }
        if (folderPath != null && !folderPath.trim().isEmpty()) {
            sb.append(folderPath.replace("\\", "/").replaceAll("^/+", "").replaceAll("/+", "/"));
        }
        if (jobName != null && !jobName.isEmpty()) {
            if (sb.length() > 0 && !folderPath.endsWith("/")) {
                sb.append("/");
            }
            sb.append(jobName);
        }
        return sb.toString();
    }
}