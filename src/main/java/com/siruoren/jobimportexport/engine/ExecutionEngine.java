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
import java.util.*;

public class ExecutionEngine {

    private final TypeResolver typeResolver = new TypeResolver();
    private final RenameDAGResolver renameResolver = new RenameDAGResolver();

    private List<ImportResult> results = new ArrayList<>();
    
    // 阶段1：收集所有需要创建的路径
    private List<String> folderPathsToCreate = new ArrayList<>();
    private Map<String, TreeNode> jobNodesToCreate = new LinkedHashMap<>();
    // 跟踪 FOLDER_WITH_CONFIG 类型的路径（用于覆盖模式）
    private Map<String, TreeNode> folderWithConfigToCreate = new LinkedHashMap<>();

    public List<ImportResult> execute(TreeNode root, ImportContext ctx) {
        results.clear();
        folderPathsToCreate.clear();
        jobNodesToCreate.clear();
        folderWithConfigToCreate.clear();

        // ✔ 阶段0：收集所有路径（用于后续 rename 计算）
        collectPaths(root, "", ctx);

        // ✔ 阶段0.5：预扫描所有冲突，收集完整的 renameMap（按深度排序）
        if (ctx.autoRename) {
            collectAllRenames(root, ctx);
        }

        // ✔ 阶段1：先创建所有 Folder
        createAllFolders(ctx);

        // ✔ 阶段2：最后统一创建 Job
        createAllJobs(ctx);

        return results;
    }

    /**
     * 阶段0：收集所有需要创建的路径（不实际创建）
     */
    private void collectPaths(TreeNode node, String parent, ImportContext ctx) {
        // 构建当前路径（使用原始路径，不立即 resolve）
        String path = parent.isEmpty() ? node.name : parent + "/" + node.name;

        // ROOT 跳过
        if (!"/".equals(node.name)) {
            // 🚨 根据节点类型处理
            if (node.type == NodeType.FOLDER_WITH_CONFIG) {
                // 有配置的目录：跟踪到 folderWithConfigToCreate
                folderPathsToCreate.add(path);
                folderWithConfigToCreate.put(path, node);
            } else if (node.hasConfigXml) {
                // 普通 Job：有 config.xml，作为 Job 处理
                jobNodesToCreate.put(path, node);
            } else {
                // 普通 Folder：没有 config.xml，作为 Folder 处理
                folderPathsToCreate.add(path);
            }
        }

        // 递归收集子节点
        for (TreeNode child : node.children.values()) {
            collectPaths(child, path, ctx);
        }
    }

    /**
     * 阶段0.5：预扫描所有冲突，收集完整的 renameMap（按深度排序，确保 parent 在 child 之前）
     * 同时处理 Folder 和 Job 的重命名
     */
    private void collectAllRenames(TreeNode root, ImportContext ctx) {
        // 🚨 合并 Folder 和 Job 的路径，按深度排序（短的先处理，parent 在 child 之前）
        Set<String> allPaths = new HashSet<>();
        allPaths.addAll(folderPathsToCreate);
        allPaths.addAll(jobNodesToCreate.keySet());
        
        List<String> sortedPaths = new ArrayList<>(allPaths);
        sortedPaths.sort((a, b) -> {
            int depthA = a.split("/").length;
            int depthB = b.split("/").length;
            if (depthA != depthB) return depthA - depthB;
            return a.compareTo(b);
        });

        // 按顺序处理每个路径，计算最终路径
        for (String originalPath : sortedPaths) {
            // 应用已有的 renameMap，计算当前路径的最终位置
            String resolvedPath = resolveWithDag(originalPath, ctx.renameMap);

            String fullPath = getFullPath(resolvedPath, ctx);
            Item existingItem = Jenkins.get().getItemByFullName(fullPath);

            if (existingItem != null) {
                // 需要 rename
                String parentPath = getParentPath(resolvedPath);
                String newName = generateUniqueName(ctx.targetGroup, parentPath, getLastPathSegment(resolvedPath), ctx);
                String newPath = parentPath.isEmpty() ? newName : parentPath + "/" + newName;

                // 记录 rename DAG（原始路径 -> 最终路径）
                ctx.renameMap.put(originalPath, newPath);
            }
        }
    }

    /**
     * 使用 DAG 传播解析路径
     */
    private String resolveWithDag(String path, Map<String, String> renameMap) {
        String resolved = path;
        boolean changed = true;

        while (changed) {
            changed = false;

            // 按路径长度从长到短排序，确保更长的路径优先匹配
            List<Map.Entry<String, String>> entries = new ArrayList<>(renameMap.entrySet());
            entries.sort((a, b) -> b.getKey().length() - a.getKey().length());

            for (Map.Entry<String, String> entry : entries) {
                String from = entry.getKey();
                String to = entry.getValue();

                // 精确匹配
                if (resolved.equals(from)) {
                    resolved = to;
                    changed = true;
                    break;
                }

                // 子路径传播：parent rename 传播到 child
                if (resolved.startsWith(from + "/")) {
                    resolved = to + resolved.substring(from.length());
                    changed = true;
                    break;
                }
            }
        }

        return resolved;
    }

    /**
     * 阶段1：创建所有 Folder
     */
    private void createAllFolders(ImportContext ctx) {
        // 按路径深度排序，确保父文件夹先于子文件夹创建
        List<String> sortedPaths = new ArrayList<>(folderPathsToCreate);
        sortedPaths.sort((a, b) -> {
            int depthA = a.split("/").length;
            int depthB = b.split("/").length;
            if (depthA != depthB) return depthA - depthB;
            return a.compareTo(b);
        });

        for (String path : sortedPaths) {
            // ✔ 使用 Rename DAG 解析最终路径
            String resolvedPath = renameResolver.resolvePath(path, ctx);
            
            // 检查是否是 FOLDER_WITH_CONFIG 类型
            TreeNode folderNode = folderWithConfigToCreate.get(path);
            boolean isFolderWithConfig = (folderNode != null);
            
            // 检查 Jenkins 中是否已存在
            String fullPath = getFullPath(resolvedPath, ctx);
            Item existingItem = Jenkins.get().getItemByFullName(fullPath);
            
            if (ctx.dryRun) {
                ctx.virtualFolders.add(fullPath);
                
                // dryRun 模式下，所有目录都需要记录到 results
                ImportResult result = createFolderResult(path, resolvedPath, ctx);
                
                if (existingItem != null) {
                    // Jenkins 中已存在
                    if (isFolderWithConfig) {
                        if (ctx.autoRename && !path.equals(resolvedPath)) {
                            result.statusEnum = Status.RENAME_FOLDER;
                            result.status = "RENAME_FOLDER";
                            result.renamed = true;
                            result.message = "目录已重命名为: " + getLastPathSegment(resolvedPath);
                        } else if (ctx.overwrite) {
                            result.statusEnum = Status.OVERWRITE_FOLDER;
                            result.status = "OVERWRITE_FOLDER";
                            result.message = "将覆盖目录任务配置";
                        } else {
                            result.statusEnum = Status.SKIP_EXISTS;
                            result.status = "SKIP_EXISTS";
                            result.skipped = true;
                            result.message = "目录任务已存在，已跳过";
                        }
                    } else {
                        // 普通 Folder 已存在则复用
                        result.statusEnum = Status.REUSE_FOLDER;
                        result.status = "REUSE_FOLDER";
                        result.message = "目录已存在，复用";
                    }
                } else {
                    // Jenkins 中不存在，将创建
                    if (isFolderWithConfig) {
                        if (ctx.autoRename && !path.equals(resolvedPath)) {
                            result.statusEnum = Status.RENAME_FOLDER;
                            result.status = "RENAME_FOLDER";
                            result.renamed = true;
                            result.message = "目录已重命名为: " + getLastPathSegment(resolvedPath);
                        } else {
                            result.statusEnum = Status.CREATE_FOLDER;
                            result.status = "CREATE_FOLDER";
                            result.message = "将创建目录任务";
                        }
                    } else {
                        result.statusEnum = Status.CREATE_FOLDER;
                        result.status = "CREATE_FOLDER";
                        result.message = "将创建目录";
                    }
                }
                result.success = true;
                results.add(result);
                continue;
            }

            if (ctx.createdFolders.contains(fullPath)) {
                continue;
            }
            
            if (existingItem != null) {
                if (isFolderWithConfig) {
                    // 有配置的目录任务已存在
                    if (ctx.overwrite) {
                        // 覆盖模式：更新目录配置
                        handleOverwriteFolder(existingItem, folderNode, resolvedPath, ctx);
                    } else {
                        // 非覆盖模式：跳过，记录到 results
                        ImportResult result = createFolderResult(path, resolvedPath, ctx);
                        result.statusEnum = Status.SKIP_EXISTS;
                        result.status = "SKIP_EXISTS";
                        result.skipped = true;
                        result.message = "目录任务已存在，已跳过";
                        results.add(result);
                    }
                }
                // 普通 Folder 已存在则复用，不记录到 results
                ctx.createdFolders.add(fullPath);
                continue;
            }

            try {
                ensureFolderPath(ctx.targetGroup, resolvedPath, true, ctx);
                ctx.createdFolders.add(fullPath);
                
                // 如果是有配置的目录且有 config.xml，需要更新配置
                if (isFolderWithConfig && folderNode.configXml != null && folderNode.configXml.length > 0) {
                    Item newItem = Jenkins.get().getItemByFullName(fullPath);
                    if (newItem != null) {
                        updateFolderConfig(newItem, folderNode, resolvedPath);
                    }
                }
                
                // 刷新 Jenkins 内存状态，确保后续重命名检查能感知到刚创建的目录
                Jenkins.get().reload();
            } catch (Exception e) {
                // 忽略创建失败，后续 Job 创建会处理
            }
        }
    }
    
    /**
     * 处理目录任务的覆盖更新
     */
    private void handleOverwriteFolder(Item existingItem, TreeNode node, String path, ImportContext ctx) {
        ImportResult result = createFolderResult(path, path, ctx);
        
        try {
            // 如果有新的 config.xml，更新配置
            if (node.configXml != null && node.configXml.length > 0) {
                updateFolderConfig(existingItem, node, path);
                result.statusEnum = Status.OVERWRITE_FOLDER;
                result.status = "OVERWRITE_FOLDER";
                result.success = true;
                result.message = "已更新目录任务配置";
            } else {
                // 没有新配置，复用原来的
                result.statusEnum = Status.REUSE_FOLDER;
                result.status = "REUSE_FOLDER";
                result.success = true;
                result.message = "目录任务已存在，复用原配置";
            }
        } catch (Exception e) {
            result.statusEnum = Status.ERROR;
            result.status = "ERROR";
            result.success = false;
            result.message = "更新目录任务失败: " + e.getMessage();
        }
        
        results.add(result);
        ctx.createdFolders.add(getFullPath(path, ctx));
    }
    
    /**
     * 更新目录任务的配置
     */
    private void updateFolderConfig(Item item, TreeNode node, String path) throws Exception {
        if (item instanceof AbstractItem) {
            AbstractItem abstractItem = (AbstractItem) item;
            try (InputStream in = new ByteArrayInputStream(node.configXml)) {
                abstractItem.updateByXml(new StreamSource(in));
                abstractItem.save();
            }
        }
    }
    
    /**
     * 创建 Folder 的 ImportResult
     */
    private ImportResult createFolderResult(String originalPath, String resolvedPath, ImportContext ctx) {
        String folderPath = getParentPath(resolvedPath);
        String folderName = getLastPathSegment(resolvedPath);
        ImportResult result = new ImportResult(folderName, folderPath);
        result.finalName = resolvedPath;
        result.fullPath = getFullPath(resolvedPath, ctx);
        // 移除所有前导斜杠，避免显示 "/test" 或 "//test"
        result.sourcePath = originalPath.replaceFirst("^/+", "");
        result.displayPath = result.sourcePath;
        result.isFolder = true;
        result.isJob = false;
        return result;
    }

    /**
     * 阶段2：创建所有 Job
     */
    private void createAllJobs(ImportContext ctx) {
        for (Map.Entry<String, TreeNode> entry : jobNodesToCreate.entrySet()) {
            String originalPath = entry.getKey();
            TreeNode node = entry.getValue();
            
            // ✔ 使用 Rename DAG 解析最终路径
            String resolvedPath = renameResolver.resolvePath(originalPath, ctx);

            // 检查父目录是否被跳过（如果父目录是 FOLDER_WITH_CONFIG 且不覆盖/不重命名）
            String parentPath = getParentPath(resolvedPath);
            if (shouldSkipDueToParentFolder(originalPath, resolvedPath, ctx)) {
                // 父目录冲突且不覆盖/不重命名，跳过此任务
                ImportResult result = createResult(node, resolvedPath, ctx);
                result.statusEnum = Status.SKIP_EXISTS;
                result.status = "SKIP_EXISTS";
                result.skipped = true;
                result.message = "父目录任务已存在，已跳过";
                results.add(result);
                continue;
            }

            ImportResult result = createResult(node, resolvedPath, ctx);
            processJob(node, resolvedPath, result, ctx);
            results.add(result);
        }
    }
    
    /**
     * 检查是否应该因为父目录被跳过而跳过此任务
     */
    private boolean shouldSkipDueToParentFolder(String originalPath, String resolvedPath, ImportContext ctx) {
        // 只有在不覆盖且不重命名的情况下才检查
        if (ctx.overwrite || ctx.autoRename) {
            return false;
        }
        
        // 检查父路径
        for (String folderPath : folderPathsToCreate) {
            String resolvedFolderPath = renameResolver.resolvePath(folderPath, ctx);
            
            // 检查 resolvedPath 是否在这个目录下面
            if (resolvedPath.startsWith(resolvedFolderPath + "/")) {
                // dryRun 模式下，检查 virtualFolders
                if (ctx.dryRun) {
                    String fullFolderPath = getFullPath(resolvedFolderPath, ctx);
                    if (ctx.virtualFolders.contains(fullFolderPath)) {
                        // 父目录在 dryRun 模式下会被创建，子任务正常创建
                        return false;
                    }
                    // 父目录不在 virtualFolders 中，说明被跳过了
                    return true;
                }
                
                // 非 dryRun 模式下，检查 Jenkins 中是否存在
                String fullFolderPath = getFullPath(resolvedFolderPath, ctx);
                Item existingItem = Jenkins.get().getItemByFullName(fullFolderPath);
                if (existingItem != null) {
                    // 父目录存在且未覆盖/未重命名，会被跳过，所以子任务也应该跳过
                    return true;
                }
            }
        }
        
        return false;
    }

    private ImportResult createResult(TreeNode node, String path, ImportContext ctx) {
        String folderPath = getParentPath(path);
        String jobName = getLastPathSegment(path);
        ImportResult result = new ImportResult(jobName, folderPath);
        result.finalName = path;
        result.fullPath = getFullPath(path, ctx);
        result.sourcePath = node.fullPath;
        result.displayPath = node.fullPath;
        result.isFolder = (node.type == NodeType.FOLDER);
        result.isJob = (node.type == NodeType.JOB);
        return result;
    }

    private void processJob(TreeNode node, String path, ImportResult result, ImportContext ctx) {
        String fullPath = getFullPath(path, ctx);
        Item existingItem = Jenkins.get().getItemByFullName(fullPath);

        if (existingItem != null) {
            if (ctx.overwrite) {
                handleOverwrite(existingItem, node, path, result, ctx);
            } else if (ctx.autoRename) {
                // 检查是否已经通过 renameMap 重命名过了
                // 如果 path 已经不同于原始路径（node.fullPath），说明 DAG 已经处理过
                if (!path.equals(node.fullPath)) {
                    // DAG 已处理过，直接使用已解析的路径创建任务
                    handleAutoRenameResolved(node, path, result, ctx);
                } else {
                    // 路径未变，说明 DAG 阶段漏掉了（不应该发生），执行重命名
                    handleAutoRename(node, path, result, ctx);
                }
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
        String parentPath = getParentPath(path);
        String newName = generateUniqueName(ctx.targetGroup, parentPath, getLastPathSegment(path), ctx);
        String newPath = parentPath.isEmpty() ? newName : parentPath + "/" + newName;

        // 更新 renameMap，确保后续节点能正确解析
        ctx.renameMap.put(path, newPath);

        result.finalName = newPath;
        result.renamed = true;
        
        if (node.hasConfigXml) {
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
        } else {
            result.statusEnum = Status.RENAME_FOLDER;
            result.status = "RENAME_FOLDER";
            result.message = "目录已重命名为: " + newName;

            if (!ctx.dryRun) {
                try {
                    ensureFolderPath(ctx.targetGroup, newPath, true, ctx);
                    ctx.createdFolders.add(getFullPath(newPath, ctx));
                } catch (Exception e) {
                    result.statusEnum = Status.ERROR;
                    result.status = "ERROR";
                    result.message = "创建目录失败: " + e.getMessage();
                    return;
                }
            }
        }
        result.success = true;
    }

    /**
     * 处理已通过 DAG 重命名过的任务路径
     * 此时 path 已经是重命名后的唯一路径，直接创建任务即可
     */
    private void handleAutoRenameResolved(TreeNode node, String resolvedPath, ImportResult result, ImportContext ctx) {
        String folderPath = getParentPath(resolvedPath);
        String jobName = getLastPathSegment(resolvedPath);

        result.finalName = resolvedPath;
        result.renamed = true;
        result.statusEnum = Status.RENAME_JOB;
        result.status = "RENAME_JOB";
        result.message = "任务已重命名为: " + jobName;

        if (!ctx.dryRun) {
            try {
                createJobDirect(resolvedPath, node.configXml, ctx);
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
        result.message = ctx.dryRun ? "将创建任务" : "已创建任务";
    }

    private void createJobDirect(String path, byte[] configXml, ImportContext ctx) throws Exception {
        String folderPath = getParentPath(path);
        String jobName = getLastPathSegment(path);

        ItemGroup parentGroup = ensureParentFolders(folderPath, ctx);
        if (parentGroup instanceof ModifiableTopLevelItemGroup) {
            try (InputStream xmlStream = new ByteArrayInputStream(configXml)) {
                ((ModifiableTopLevelItemGroup) parentGroup).createProjectFromXML(jobName, xmlStream);
                ctx.createdJobs.add(getFullPath(path, ctx));
                
                // 刷新 Jenkins 内存状态，确保后续重命名检查能感知到刚创建的任务
                Jenkins.get().reload();
            }
        }
    }

    private ItemGroup ensureParentFolders(String folderPath, ImportContext ctx) throws Exception {
        if (folderPath == null || folderPath.isEmpty()) {
            return ctx.targetGroup;
        }

        String[] parts = folderPath.split("/");
        ItemGroup current = ctx.targetGroup;
        StringBuilder currentPath = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) continue;

            if (currentPath.length() > 0) {
                currentPath.append("/");
            }
            currentPath.append(part);
            String fullPath = currentPath.toString();
            String fullJenkinsPath = getFullPath(fullPath, ctx);

            // 检查是否是当前会话中刚创建的目录
            if (ctx.createdFolders.contains(fullJenkinsPath)) {
                Item item = Jenkins.get().getItemByFullName(fullJenkinsPath);
                if (item instanceof ItemGroup) {
                    current = (ItemGroup) item;
                    continue;
                }
            }

            Item item = current.getItem(part);
            if (item == null) {
                if (current instanceof ModifiableTopLevelItemGroup) {
                    try {
                        Class.forName("com.cloudbees.hudson.plugins.folder.Folder");
                        hudson.model.TopLevelItemDescriptor folderDescriptor = Jenkins.get().getDescriptorByType(
                            com.cloudbees.hudson.plugins.folder.Folder.DescriptorImpl.class);
                        hudson.model.TopLevelItem folder = ((ModifiableTopLevelItemGroup) current).createProject(folderDescriptor, part, false);
                        current = (ItemGroup) folder;
                        ctx.createdFolders.add(fullJenkinsPath);
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
            String fullJenkinsPath = getFullPath(fullPath, ctx);

            // 检查是否是当前会话中刚创建的目录
            Item item = null;
            if (ctx != null && ctx.createdFolders.contains(fullJenkinsPath)) {
                item = Jenkins.get().getItemByFullName(fullJenkinsPath);
            }
            
            if (item == null) {
                item = current.getItem(part);
            }
            
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

    private String getFullPath(String relativePath, ImportContext ctx) {
        if (ctx.basePath == null || ctx.basePath.isEmpty()) {
            return relativePath;
        }
        return ctx.basePath + "/" + relativePath;
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

    /**
     * 生成唯一的名称（支持 Folder 和 Job，检查当前会话中刚创建的项目）
     */
    private String generateUniqueName(ItemGroup itemGroup, String folderPath, String baseName, ImportContext ctx) {
        int counter = 2;
        String candidate;
        String fullPath;
        String checkPath;

        do {
            candidate = baseName + "_" + counter;
            counter++;
            fullPath = folderPath.isEmpty() ? candidate : folderPath + "/" + candidate;
            checkPath = getFullPath(fullPath, ctx);
        } while (Jenkins.get().getItemByFullName(checkPath) != null
                 || ctx.createdFolders.contains(checkPath)
                 || ctx.createdJobs.contains(checkPath)
                 || ctx.virtualFolders.contains(checkPath));

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