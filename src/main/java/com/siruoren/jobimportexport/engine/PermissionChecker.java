package com.siruoren.jobimportexport.engine;

import com.siruoren.jobimportexport.engine.model.ImportContext;
import com.siruoren.jobimportexport.engine.model.ImportResult;
import hudson.model.Item;
import hudson.model.ItemGroup;
import jenkins.model.Jenkins;

import java.util.List;

/**
 * 负责导入过程中的权限检查逻辑。
 * 从 ExecutionEngine 中提取，遵循单一职责原则。
 */
public class PermissionChecker {

    /**
     * 检查指定路径的权限
     *
     * @param originalPath  原始路径
     * @param resolvedPath   解析后路径
     * @param ctx            导入上下文
     * @param folderPathsToCreate 文件夹路径列表（用于判断叶子节点）
     * @param resultCollector 结果收集器（用于判断叶子节点）
     * @return 权限错误结果，null 表示权限检查通过
     */
    public static ImportResult checkPermissionForPath(
            String originalPath, String resolvedPath, ImportContext ctx,
            List<String> folderPathsToCreate, ResultCollector resultCollector) {

        String fullPath = FolderCreator.getFullPath(resolvedPath, ctx);
        Item existingItem = Jenkins.get().getItemByFullName(fullPath);
        boolean isLeaf = isLeafNode(originalPath, folderPathsToCreate, resultCollector);

        if (existingItem != null && ctx.overwrite) {
            return checkOverwritePermission(existingItem, resolvedPath, originalPath, fullPath, isLeaf, ctx);
        } else if (existingItem == null) {
            return checkCreatePermission(resolvedPath, originalPath, fullPath, isLeaf, ctx);
        }

        return null;
    }

    private static ImportResult checkOverwritePermission(Item existingItem, String resolvedPath,
            String originalPath, String fullPath, boolean isLeaf, ImportContext ctx) {

        if (!isLeaf) {
            if (!existingItem.hasPermission(Item.CONFIGURE)) {
                ImportResult errorResult = buildErrorResult(resolvedPath, originalPath, fullPath, true, false);
                errorResult.message = Messages.ExecutionEngine_noPermissionUpdateDirConfig();
                ctx.parentPermissionErrors.add(resolvedPath);
                return errorResult;
            }
            if (!existingItem.hasPermission(Item.CREATE)) {
                ImportResult errorResult = buildErrorResult(resolvedPath, originalPath, fullPath, true, false);
                errorResult.message = Messages.ExecutionEngine_noPermissionCreateInDir();
                ctx.parentPermissionErrors.add(resolvedPath);
                return errorResult;
            }
        } else {
            if (!existingItem.hasPermission(Item.CONFIGURE)) {
                ImportResult errorResult = buildErrorResult(resolvedPath, originalPath, fullPath, false, true);
                errorResult.message = Messages.ExecutionEngine_noPermissionUpdateJobConfig();
                return errorResult;
            }
        }
        return null;
    }

    private static ImportResult checkCreatePermission(String resolvedPath, String originalPath,
            String fullPath, boolean isLeaf, ImportContext ctx) {

        ItemGroup parentGroup = getParentGroupForPath(resolvedPath, ctx);
        if (parentGroup instanceof Item && !((Item) parentGroup).hasPermission(Item.CREATE)) {
            ImportResult errorResult = buildErrorResult(resolvedPath, originalPath, fullPath, !isLeaf, isLeaf);
            errorResult.message = Messages.ExecutionEngine_noPermissionCreateJob();
            if (!isLeaf) {
                ctx.parentPermissionErrors.add(resolvedPath);
            }
            return errorResult;
        }
        return null;
    }

    private static ImportResult buildErrorResult(String resolvedPath, String originalPath,
            String fullPath, boolean isFolder, boolean isJob) {
        ImportResult errorResult = new ImportResult(
                FolderCreator.getLastPathSegment(resolvedPath),
                FolderCreator.getParentPath(resolvedPath));
        errorResult.finalName = resolvedPath;
        errorResult.fullPath = fullPath;
        errorResult.sourcePath = originalPath.replaceFirst("^/+", "");
        errorResult.displayPath = errorResult.sourcePath;
        errorResult.isFolder = isFolder;
        errorResult.isJob = isJob;
        errorResult.setStatus("ERROR");
        errorResult.success = false;
        return errorResult;
    }

    private static ItemGroup getParentGroupForPath(String path, ImportContext ctx) {
        String parentPath = FolderCreator.getParentPath(path);
        if (parentPath.isEmpty()) {
            return ctx.targetGroup;
        }
        String fullParentPath = FolderCreator.getFullPath(parentPath, ctx);
        Item parentItem = Jenkins.get().getItemByFullName(fullParentPath);
        if (parentItem instanceof ItemGroup) {
            return (ItemGroup) parentItem;
        }
        return ctx.targetGroup;
    }

    /**
     * 判断指定路径是否为叶子节点（即没有子目录或子任务）
     */
    static boolean isLeafNode(String path, List<String> folderPathsToCreate,
            ResultCollector resultCollector) {
        for (String folderPath : folderPathsToCreate) {
            if (folderPath.startsWith(path + "/")) {
                return false;
            }
        }
        // 检查是否有子任务（通过 resultCollector 获取 jobNodesToCreate）
        if (resultCollector != null) {
            for (String jobPath : resultCollector.getJobPaths()) {
                if (jobPath.startsWith(path + "/")) {
                    return false;
                }
            }
        }
        return true;
    }
}
