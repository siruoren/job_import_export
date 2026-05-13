package com.siruoren.jobimportexport.engine;

import com.siruoren.jobimportexport.engine.model.*;
import com.siruoren.jobimportexport.engine.resolver.PathResolver;
import com.siruoren.jobimportexport.engine.resolver.TypeResolver;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.ItemGroup;
import jenkins.model.Jenkins;
import jenkins.model.ModifiableTopLevelItemGroup;

import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ExecutionEngine {

    private final TypeResolver typeResolver = new TypeResolver();
    private final PathResolver pathResolver = new PathResolver();

    private List<ImportResult> results = new ArrayList<>();

    public List<ImportResult> execute(Node node, String parent, ImportContext ctx) {
        if (node.name == null || node.name.isEmpty()) {
            for (Node child : node.children) {
                execute(child, parent, ctx);
            }
            return results;
        }

        String path = parent.isEmpty() ? node.name : parent + "/" + node.name;

        path = pathResolver.resolve(path, ctx);

        ensureFolder(path, ctx);

        NodeType type = typeResolver.resolve(node);

        ImportResult result = createResult(node, path);

        if (type == NodeType.JOB) {
            processJob(node, path, result, ctx);
        } else {
            processFolder(node, path, result, ctx);
        }

        results.add(result);

        for (Node child : node.children) {
            execute(child, path, ctx);
        }

        return results;
    }

    private ImportResult createResult(Node node, String path) {
        String folderPath = getParentPath(path);
        String jobName = getLastPathSegment(path);
        ImportResult result = new ImportResult(jobName, folderPath);
        result.finalName = path;
        result.fullPath = path;
        result.zipPath = node.fullPath;
        result.sourcePath = node.fullPath;
        result.displayPath = node.fullPath;
        return result;
    }

    private void processJob(Node node, String path, ImportResult result, ImportContext ctx) {
        Item existingItem = Jenkins.get().getItemByFullName(path);

        if (existingItem != null) {
            if (ctx.overwrite) {
                handleOverwrite(existingItem, node, path, result, ctx);
            } else if (ctx.autoRename) {
                handleAutoRename(node, path, result, ctx);
            } else {
                result.status = "SKIP_EXISTS";
                result.skipped = true;
                result.message = "任务已存在，已跳过";
            }
        } else {
            createJob(node, path, result, ctx);
        }
    }

    private void handleOverwrite(Item existingItem, Node node, String path, ImportResult result, ImportContext ctx) {
        if (!ctx.dryRun) {
            try {
                backupConfig(existingItem);
                if (existingItem instanceof AbstractItem) {
                    try (InputStream in = new ByteArrayInputStream(node.configXml)) {
                        ((AbstractItem) existingItem).updateByXml(new StreamSource(in));
                        ((AbstractItem) existingItem).save();
                    }
                } else {
                    existingItem.delete();
                    createJobDirect(path, node.configXml, ctx);
                }
            } catch (Exception e) {
                result.status = "ERROR";
                result.message = "覆盖失败: " + e.getMessage();
                return;
            }
        }
        result.status = "OVERWRITE";
        result.success = true;
        result.message = "已覆盖任务配置";
    }

    private void handleAutoRename(Node node, String path, ImportResult result, ImportContext ctx) {
        String newName = generateUniqueJobName(ctx.targetGroup, getParentPath(path), getLastPathSegment(path));
        String newPath = getParentPath(path).isEmpty() ? newName : getParentPath(path) + "/" + newName;
        
        ctx.renameMap.put(path, newPath);
        
        result.finalName = newPath;
        result.renamed = true;
        result.status = "RENAME";
        result.message = "任务已重命名为: " + newName;

        if (!ctx.dryRun) {
            try {
                createJobDirect(newPath, node.configXml, ctx);
            } catch (Exception e) {
                result.status = "ERROR";
                result.message = "创建失败: " + e.getMessage();
                return;
            }
        }
        result.success = true;
    }

    private void createJob(Node node, String path, ImportResult result, ImportContext ctx) {
        if (!ctx.dryRun) {
            try {
                createJobDirect(path, node.configXml, ctx);
            } catch (Exception e) {
                result.status = "ERROR";
                result.message = "创建失败: " + e.getMessage();
                return;
            }
        }
        result.status = "OK";
        result.success = true;
        result.message = "已创建任务";
    }

    private void createJobDirect(String path, byte[] configXml, ImportContext ctx) throws Exception {
        String folderPath = getParentPath(path);
        String jobName = getLastPathSegment(path);

        ItemGroup parentGroup = getParentItemGroup(ctx.targetGroup, folderPath);
        if (parentGroup instanceof ModifiableTopLevelItemGroup) {
            try (InputStream xmlStream = new ByteArrayInputStream(configXml)) {
                ((ModifiableTopLevelItemGroup) parentGroup).createProjectFromXML(jobName, xmlStream);
                ctx.createdJobs.add(path);
            }
        }
    }

    private void processFolder(Node node, String path, ImportResult result, ImportContext ctx) {
        Item existingItem = Jenkins.get().getItemByFullName(path);

        if (existingItem != null && existingItem instanceof ItemGroup) {
            if (node.hasConfigXml && ctx.overwrite) {
                if (!ctx.dryRun && existingItem instanceof AbstractItem) {
                    try {
                        backupConfig(existingItem);
                        try (InputStream in = new ByteArrayInputStream(node.configXml)) {
                            ((AbstractItem) existingItem).updateByXml(new StreamSource(in));
                            ((AbstractItem) existingItem).save();
                        }
                    } catch (Exception e) {
                        result.status = "ERROR";
                        result.message = "覆盖文件夹配置失败: " + e.getMessage();
                        return;
                    }
                }
                result.status = "OVERWRITE";
                result.success = true;
                result.message = "已覆盖文件夹配置";
            } else {
                result.status = "REUSE";
                result.success = true;
                result.message = "文件夹已存在，复用";
            }
        } else {
            if (!ctx.dryRun) {
                try {
                    ensureFolderPath(ctx.targetGroup, path, true, ctx);
                } catch (Exception e) {
                    result.status = "ERROR";
                    result.message = "创建文件夹失败: " + e.getMessage();
                    return;
                }
            }
            result.status = "OK";
            result.success = true;
            result.message = "已创建文件夹";
        }
    }

    private void ensureFolder(String path, ImportContext ctx) {
        String parentPath = getParentPath(path);
        if (parentPath != null && !parentPath.isEmpty()) {
            ensureFolder(parentPath, ctx);
        }

        if (ctx.dryRun) {
            ctx.virtualFolders.add(path);
        } else {
            try {
                ensureFolderPath(ctx.targetGroup, path, true, ctx);
                ctx.createdFolders.add(path);
            } catch (Exception e) {
            }
        }
    }

    private ItemGroup getParentItemGroup(ItemGroup base, String folderPath) {
        if (folderPath == null || folderPath.isEmpty()) {
            return base;
        }
        String[] parts = folderPath.split("/");
        ItemGroup current = base;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            Item item = Jenkins.get().getItemByFullName(part);
            if (item instanceof ItemGroup) {
                current = (ItemGroup) item;
            }
        }
        return current;
    }

    private ItemGroup ensureFolderPath(ItemGroup itemGroup, String folderPath, boolean create, ImportContext ctx) throws Exception {
        if (folderPath == null || folderPath.isEmpty()) {
            return itemGroup;
        }
        String[] parts = folderPath.split("/");
        ItemGroup current = itemGroup;
        StringBuilder currentPath = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (currentPath.length() > 0) {
                currentPath.append("/");
            }
            currentPath.append(part);
            String fullPath = currentPath.toString();

            Item item = current.getItem(part);
            if (item == null) {
                if (create && current instanceof ModifiableTopLevelItemGroup) {
                    try {
                        Class.forName("com.cloudbees.hudson.plugins.folder.Folder");
                        hudson.model.TopLevelItemDescriptor folderDescriptor = Jenkins.get().getDescriptorByType(
                            com.cloudbees.hudson.plugins.folder.Folder.DescriptorImpl.class);
                        hudson.model.TopLevelItem folder = ((ModifiableTopLevelItemGroup) current).createProject(folderDescriptor, part, false);
                        current = (ItemGroup) folder;
                    } catch (ClassNotFoundException e) {
                        throw new Exception("Folder plugin not available", e);
                    }
                } else {
                    throw new Exception("Folder not found: " + fullPath);
                }
            } else if (item instanceof ItemGroup) {
                current = (ItemGroup) item;
            } else {
                throw new Exception("Path exists but is not a folder: " + fullPath);
            }
        }
        return current;
    }

    private String getParentPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1) return "";
        return path.substring(0, lastSlash);
    }

    private String getLastPathSegment(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1) return path;
        return path.substring(lastSlash + 1);
    }

    private String generateUniqueJobName(ItemGroup itemGroup, String folderPath, String jobName) {
        String baseName = jobName;
        int counter = 2;
        String candidate;

        ItemGroup parentGroup = getParentItemGroup(itemGroup, folderPath);
        do {
            candidate = baseName + "_" + counter;
            counter++;
        } while (Jenkins.get().getItemByFullName(folderPath.isEmpty() ? candidate : folderPath + "/" + candidate) != null);

        return candidate;
    }

    private void backupConfig(Item item) throws Exception {
        if (item instanceof AbstractItem) {
            AbstractItem abstractItem = (AbstractItem) item;
            Path configFile = Paths.get(abstractItem.getRootDir().getAbsolutePath(), "config.xml");
            Path backupFile = Paths.get(abstractItem.getRootDir().getAbsolutePath(), "config.xml.bak");

            if (Files.exists(configFile)) {
                Files.copy(configFile, backupFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public List<ImportResult> getResults() {
        return results;
    }
}