package com.siruoren.jobimportexport.engine;

import com.siruoren.jobimportexport.engine.model.*;
import com.siruoren.jobimportexport.engine.resolver.RenameDAGResolver;
import com.siruoren.jobimportexport.service.SecureXmlParser;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import jenkins.model.Jenkins;
import jenkins.model.ModifiableTopLevelItemGroup;

import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 负责导入过程中的文件夹创建逻辑。
 * 从 ExecutionEngine 中提取，遵循单一职责原则。
 */
public class FolderCreator {

    private static final Logger LOGGER = Logger.getLogger(FolderCreator.class.getName());

    private final RenameDAGResolver renameResolver;

    public FolderCreator(RenameDAGResolver renameResolver) {
        this.renameResolver = renameResolver;
    }

    /**
     * 创建所有文件夹（按路径深度排序，确保父目录先创建）
     *
     * @param folderPathsToCreate  需要创建的文件夹路径列表
     * @param folderWithConfigToCreate 带配置的文件夹映射
     * @param resultCollector      结果收集器
     * @param ctx                  导入上下文
     */
    public void createAllFolders(
            List<String> folderPathsToCreate,
            Map<String, TreeNode> folderWithConfigToCreate,
            ResultCollector resultCollector,
            ImportContext ctx) {

        List<String> sortedPaths = new ArrayList<>(folderPathsToCreate);
        sortedPaths.sort(this::compareByDepth);

        for (String path : sortedPaths) {
            String resolvedPath = renameResolver.resolvePath(path, ctx);

            TreeNode folderNode = folderWithConfigToCreate.get(path);
            boolean isFolderWithConfig = (folderNode != null);

            String fullPath = getFullPath(resolvedPath, ctx);
            Item existingItem = Jenkins.get().getItemByFullName(fullPath);

            // 权限检查
            ImportResult permissionError = PermissionChecker.checkPermissionForPath(
                    path, resolvedPath, ctx, folderPathsToCreate, resultCollector);
            if (permissionError != null) {
                resultCollector.addResult(permissionError);
                continue;
            }

            if (ctx.dryRun) {
                ctx.virtualFolders.add(fullPath);
                ImportResult result = createFolderResult(path, resolvedPath, ctx);

                if (existingItem != null) {
                    handleExistingFolderDryRun(existingItem, folderNode, isFolderWithConfig,
                            path, resolvedPath, result, ctx);
                } else {
                    handleNewFolderDryRun(isFolderWithConfig, path, resolvedPath, result, ctx);
                }
                result.success = true;
                resultCollector.addResult(result);
                continue;
            }

            if (ctx.createdFolders.contains(fullPath)) {
                continue;
            }

            if (existingItem != null) {
                handleExistingFolder(existingItem, folderNode, isFolderWithConfig,
                        path, resolvedPath, resultCollector, ctx);
                continue;
            }

            ImportResult result = createFolderResult(path, resolvedPath, ctx);
            try {
                ensureFolderPath(ctx.targetGroup, resolvedPath, true, ctx);
                ctx.createdFolders.add(fullPath);

                if (isFolderWithConfig && folderNode.configXml != null && folderNode.configXml.length > 0) {
                    Item newItem = Jenkins.get().getItemByFullName(fullPath);
                    if (newItem != null) {
                        updateFolderConfig(newItem, folderNode);
                    }
                }

                result.setStatusEnum(Status.CREATE_FOLDER);
                result.success = true;
                result.message = isFolderWithConfig
                        ? Messages.ExecutionEngine_createdDirJob()
                        : Messages.ExecutionEngine_createdDir();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to create folder: " + resolvedPath, e);
                result.setStatusEnum(Status.ERROR);
                result.success = false;
                result.message = Messages.ExecutionEngine_createDirFailed(e.getMessage());
                ctx.parentTypeErrors.add(resolvedPath);
            }
            resultCollector.addResult(result);
        }
    }

    private void handleExistingFolderDryRun(Item existingItem, TreeNode folderNode,
            boolean isFolderWithConfig, String path, String resolvedPath,
            ImportResult result, ImportContext ctx) {

        if (isFolderWithConfig) {
            if (ctx.autoRename && !path.equals(resolvedPath)) {
                result.setStatusEnum(Status.RENAME_FOLDER);
                result.renamed = true;
                result.message = Messages.ExecutionEngine_dirRenamedTo(getLastPathSegment(resolvedPath));
            } else if (ctx.overwrite) {
                if (folderNode.configXml != null && folderNode.configXml.length > 0
                        && !SecureXmlParser.isFolderConfigXml(folderNode.configXml)) {
                    result.setStatusEnum(Status.ERROR);
                    result.success = false;
                    result.message = Messages.ExecutionEngine_typeMismatchCannotOverwrite();
                    ctx.parentTypeErrors.add(resolvedPath);
                } else {
                    result.setStatusEnum(Status.OVERWRITE_FOLDER);
                    result.message = Messages.ExecutionEngine_willOverwriteDirConfig();
                }
            } else {
                result.setStatusEnum(Status.SKIP_EXISTS);
                result.skipped = true;
                result.message = Messages.ExecutionEngine_dirExistsSkipped();
            }
        } else {
            if (existingItem instanceof Job) {
                result.setStatusEnum(Status.ERROR);
                result.success = false;
                result.message = Messages.ExecutionEngine_typeMismatchCannotImportAsDir();
                ctx.parentTypeErrors.add(resolvedPath);
            } else {
                result.setStatusEnum(Status.REUSE_FOLDER);
                result.skipped = true;
                result.message = Messages.ExecutionEngine_dirExistsReuse();
            }
        }
    }

    private void handleNewFolderDryRun(boolean isFolderWithConfig, String path,
            String resolvedPath, ImportResult result, ImportContext ctx) {

        if (isFolderWithConfig) {
            if (ctx.autoRename && !path.equals(resolvedPath)) {
                result.setStatusEnum(Status.RENAME_FOLDER);
                result.renamed = true;
                result.message = Messages.ExecutionEngine_dirRenamedTo(getLastPathSegment(resolvedPath));
            } else {
                result.setStatusEnum(Status.CREATE_FOLDER);
                result.message = Messages.ExecutionEngine_willCreateDirJob();
            }
        } else {
            result.setStatusEnum(Status.CREATE_FOLDER);
            result.message = Messages.ExecutionEngine_willCreateDir();
        }
    }

    private void handleExistingFolder(Item existingItem, TreeNode folderNode,
            boolean isFolderWithConfig, String path, String resolvedPath,
            ResultCollector resultCollector, ImportContext ctx) {

        String fullPath = getFullPath(resolvedPath, ctx);
        ImportResult result = createFolderResult(path, resolvedPath, ctx);

        if (isFolderWithConfig) {
            if (ctx.overwrite) {
                handleOverwriteFolder(existingItem, folderNode, resolvedPath, result, ctx);
            } else {
                result.setStatusEnum(Status.SKIP_EXISTS);
                result.skipped = true;
                result.message = Messages.ExecutionEngine_dirExistsSkipped();
            }
        } else {
            if (existingItem instanceof Job) {
                result.setStatusEnum(Status.ERROR);
                result.success = false;
                result.message = Messages.ExecutionEngine_typeMismatchCannotImportAsDir();
                ctx.parentTypeErrors.add(resolvedPath);
            } else {
                result.setStatusEnum(Status.REUSE_FOLDER);
                result.skipped = true;
                result.message = Messages.ExecutionEngine_dirExistsReuse();
            }
        }
        resultCollector.addResult(result);
        ctx.createdFolders.add(fullPath);
    }

    /**
     * 处理目录任务的覆盖更新
     */
    private void handleOverwriteFolder(Item existingItem, TreeNode node, String path,
            ImportResult result, ImportContext ctx) {

        if (node.configXml != null && node.configXml.length > 0) {
            if (!SecureXmlParser.isFolderConfigXml(node.configXml)) {
                result.setStatusEnum(Status.ERROR);
                result.success = false;
                result.message = Messages.ExecutionEngine_typeMismatchCannotOverwrite();
                ctx.parentTypeErrors.add(path);
                return;
            }
        }

        try {
            if (node.configXml != null && node.configXml.length > 0) {
                updateFolderConfig(existingItem, node);
                result.setStatusEnum(Status.OVERWRITE_FOLDER);
                result.success = true;
                result.message = Messages.ExecutionEngine_updatedDirConfig();
            } else {
                result.setStatusEnum(Status.REUSE_FOLDER);
                result.skipped = true;
                result.message = Messages.ExecutionEngine_dirExistsReuseConfig();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to overwrite folder config: " + path, e);
            result.setStatusEnum(Status.ERROR);
            result.success = false;
            result.message = Messages.ExecutionEngine_updateDirFailed(e.getMessage());
            ctx.parentTypeErrors.add(path);
        }
    }

    /**
     * 更新目录任务的配置
     */
    void updateFolderConfig(Item item, TreeNode node) throws Exception {
        if (item instanceof AbstractItem) {
            AbstractItem abstractItem = (AbstractItem) item;
            try (InputStream in = new ByteArrayInputStream(node.configXml)) {
                abstractItem.updateByXml(new StreamSource(in));
                abstractItem.save();
            }
        }
    }

    /**
     * 确保文件夹路径存在（按层级逐级创建）
     */
    ItemGroup ensureFolderPath(ItemGroup itemGroup, String folderPath, boolean create, ImportContext ctx) throws Exception {
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
            String fullSegmentPath = currentPath.toString();
            String fullJenkinsPath = getFullPath(fullSegmentPath, ctx);

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
                        TopLevelItemDescriptor folderDescriptor = Jenkins.get().getDescriptorByType(
                                com.cloudbees.hudson.plugins.folder.Folder.DescriptorImpl.class);
                        TopLevelItem folder = ((ModifiableTopLevelItemGroup) current)
                                .createProject(folderDescriptor, part, false);
                        current = (ItemGroup) folder;
                        ctx.createdFolders.add(fullJenkinsPath);
                    } catch (ClassNotFoundException e) {
                        throw new Exception("Folder plugin not available", e);
                    }
                } else {
                    throw new Exception("Folder not found: " + fullSegmentPath);
                }
            } else if (item instanceof ItemGroup) {
                current = (ItemGroup) item;
            } else {
                throw new Exception("Path exists but is not a folder: " + fullSegmentPath);
            }
        }
        return current;
    }

    /**
     * 创建 Folder 的 ImportResult
     */
    ImportResult createFolderResult(String originalPath, String resolvedPath, ImportContext ctx) {
        String folderPath = getParentPath(resolvedPath);
        String folderName = getLastPathSegment(resolvedPath);
        ImportResult result = new ImportResult(folderName, folderPath);
        result.finalName = resolvedPath;
        result.fullPath = getFullPath(resolvedPath, ctx);
        result.sourcePath = originalPath.replaceFirst("^/+", "");
        result.displayPath = result.sourcePath;
        result.isFolder = true;
        result.isJob = false;
        return result;
    }

    // ==================== 静态工具方法 ====================

    static String getFullPath(String relativePath, ImportContext ctx) {
        if (ctx.basePath == null || ctx.basePath.isEmpty()) {
            return relativePath;
        }
        return ctx.basePath + "/" + relativePath;
    }

    static String getParentPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1) return "";
        return path.substring(0, lastSlash);
    }

    static String getLastPathSegment(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1) return path;
        return path.substring(lastSlash + 1);
    }

    private int compareByDepth(String a, String b) {
        int depthA = a.split("/").length;
        int depthB = b.split("/").length;
        if (depthA != depthB) return depthA - depthB;
        return a.compareTo(b);
    }
}
