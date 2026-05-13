package com.siruoren.jobimportexport.engine;

import com.siruoren.jobimportexport.engine.model.*;
import com.siruoren.jobimportexport.engine.resolver.RenameDAGResolver;
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
    private final RenameDAGResolver renameResolver = new RenameDAGResolver();

    private List<ImportResult> results = new ArrayList<>();

    public List<ImportResult> execute(TreeNode root, ImportContext ctx) {
        results.clear();

        if (root.name == null || root.name.isEmpty() || "/".equals(root.name)) {
            for (TreeNode child : root.children.values()) {
                walk(child, "", ctx);
            }
        } else {
            walk(root, "", ctx);
        }

        return results;
    }

    private void walk(TreeNode node, String parent, ImportContext ctx) {
        // 构建当前路径
        String path = parent.isEmpty() ? node.name : parent + "/" + node.name;

        // ✔ Rename DAG 解析（预览和导入共用同一逻辑）
        path = renameResolver.resolvePath(path, ctx);

        // ROOT 跳过
        if (!"/".equals(node.name)) {
            // ✔ 永远创建 folder（关键修复：不能跳过）
            ensureFolder(path, ctx);

            // ✔ 只有 node 才是 Job
            if (node.type == NodeType.JOB && node.hasConfigXml) {
                ImportResult result = createResult(node, path);
                processJob(node, path, result, ctx);
                results.add(result);
            }
        }

        // ✔ 关键：永远递归 children
        for (TreeNode child : node.children.values()) {
            walk(child, path, ctx);
        }
    }

    private ImportResult createResult(TreeNode node, String path) {
        String folderPath = getParentPath(path);
        String jobName = getLastPathSegment(path);
        ImportResult result = new ImportResult(jobName, folderPath);
        result.finalName = path;
        result.fullPath = path;
        result.sourcePath = node.fullPath;
        result.displayPath = node.fullPath;
        result.isFolder = (node.type == NodeType.FOLDER);
        result.isJob = (node.type == NodeType.JOB);
        return result;
    }

    private void processJob(TreeNode node, String path, ImportResult result, ImportContext ctx) {
        Item existingItem = Jenkins.get().getItemByFullName(path);

        if (existingItem != null) {
            if (ctx.overwrite) {
                handleOverwrite(existingItem, node, path, result, ctx);
            } else if (ctx.autoRename) {
                handleAutoRename(node, path, result, ctx);
            } else {
                result.statusEnum = Status.SKIP_EXISTS;
                result.status = "SKIP_EXISTS";
                result.skipped = true;
                result.message = "任务已存在，已跳过";
            }
        } else {
            createJob(node, path, result, ctx);
        }
    }

    private void handleOverwrite(Item existingItem, TreeNode node, String path, ImportResult result, ImportContext ctx) {
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
                result.statusEnum = Status.ERROR;
                result.status = "ERROR";
                result.message = "覆盖失败: " + e.getMessage();
                return;
            }
        }
        result.statusEnum = Status.OVERWRITE_JOB;
        result.status = "OVERWRITE_JOB";
        result.success = true;
        result.message = "已覆盖任务配置";
    }

    private void handleAutoRename(TreeNode node, String path, ImportResult result, ImportContext ctx) {
        String newName = generateUniqueJobName(ctx.targetGroup, getParentPath(path), getLastPathSegment(path));
        String newPath = getParentPath(path).isEmpty() ? newName : getParentPath(path) + "/" + newName;

        // 更新 renameMap，确保后续节点能正确解析
        ctx.renameMap.put(path, newPath);

        result.finalName = newPath;
        result.renamed = true;
        result.statusEnum = Status.RENAME_JOB;
        result.status = "RENAME_JOB";
        result.message = "任务已重命名为: " + newName;

        if (!ctx.dryRun) {
            try {
                createJobDirect(newPath, node.configXml, ctx);
            } catch (Exception e) {
                result.statusEnum = Status.ERROR;
                result.status = "ERROR";
                result.message = "创建失败: " + e.getMessage();
                return;
            }
        }
        result.success = true;
    }

    private void createJob(TreeNode node, String path, ImportResult result, ImportContext ctx) {
        if (!ctx.dryRun) {
            try {
                createJobDirect(path, node.configXml, ctx);
            } catch (Exception e) {
                result.statusEnum = Status.ERROR;
                result.status = "ERROR";
                result.message = "创建失败: " + e.getMessage();
                return;
            }
        }
        result.statusEnum = Status.CREATE_JOB;
        result.status = "CREATE_JOB";
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
