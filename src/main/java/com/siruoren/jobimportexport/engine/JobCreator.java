package com.siruoren.jobimportexport.engine;

import com.siruoren.jobimportexport.engine.model.*;
import com.siruoren.jobimportexport.engine.resolver.RenameDAGResolver;
import com.siruoren.jobimportexport.engine.resolver.TypeResolver;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 负责导入过程中的 Job 创建逻辑。
 * 从 ExecutionEngine 中提取，遵循单一职责原则。
 */
public class JobCreator {

    private static final Logger LOGGER = Logger.getLogger(JobCreator.class.getName());

    private static final int MAX_UNIQUE_NAME_ATTEMPTS = 1000;

    private final RenameDAGResolver renameResolver;
    private final FolderCreator folderCreator;

    public JobCreator(RenameDAGResolver renameResolver, FolderCreator folderCreator) {
        this.renameResolver = renameResolver;
        this.folderCreator = folderCreator;
    }

    /**
     * 创建所有 Job
     *
     * @param jobNodesToCreate    需要创建的 Job 节点映射
     * @param folderPathsToCreate 文件夹路径列表（用于父目录跳过检查）
     * @param resultCollector     结果收集器
     * @param ctx                 导入上下文
     */
    public void createAllJobs(
            Map<String, TreeNode> jobNodesToCreate,
            List<String> folderPathsToCreate,
            ResultCollector resultCollector,
            ImportContext ctx) {

        for (Map.Entry<String, TreeNode> entry : jobNodesToCreate.entrySet()) {
            String originalPath = entry.getKey();
            TreeNode node = entry.getValue();

            String resolvedPath = renameResolver.resolvePath(originalPath, ctx);

            // 检查父任务是否有类型错误
            if (ctx.hasParentTypeError(resolvedPath)) {
                String parentErrorPath = ctx.getParentTypeErrorPath(resolvedPath);
                ImportResult result = createResult(node, resolvedPath, ctx);
                result.setStatusEnum(Status.ERROR);
                result.success = false;
                result.skipped = true;
                result.message = Messages.ExecutionEngine_parentTypeErrorSkip(parentErrorPath);
                resultCollector.addResult(result);
                continue;
            }

            // 检查父任务是否有权限不足
            if (ctx.hasParentPermissionError(resolvedPath)) {
                String parentErrorPath = ctx.getParentPermissionErrorPath(resolvedPath);
                ImportResult result = createResult(node, resolvedPath, ctx);
                result.setStatusEnum(Status.ERROR);
                result.success = false;
                result.skipped = true;
                result.message = Messages.ExecutionEngine_parentPermissionErrorSkip(parentErrorPath);
                resultCollector.addResult(result);
                continue;
            }

            // 检查父目录是否被跳过
            if (shouldSkipDueToParentFolder(originalPath, resolvedPath, folderPathsToCreate, ctx)) {
                ImportResult result = createResult(node, resolvedPath, ctx);
                result.setStatusEnum(Status.SKIP_EXISTS);
                result.skipped = true;
                result.message = Messages.ExecutionEngine_parentDirExistsSkipped();
                resultCollector.addResult(result);
                continue;
            }

            // 权限检查
            ImportResult permissionError = PermissionChecker.checkPermissionForPath(
                    originalPath, resolvedPath, ctx, folderPathsToCreate, resultCollector);
            if (permissionError != null) {
                resultCollector.addResult(permissionError);
                continue;
            }

            ImportResult result = createResult(node, resolvedPath, ctx);
            processJob(node, resolvedPath, result, ctx);
            resultCollector.addResult(result);
        }
    }

    private void processJob(TreeNode node, String path, ImportResult result, ImportContext ctx) {
        String fullPath = FolderCreator.getFullPath(path, ctx);
        Item existingItem = Jenkins.get().getItemByFullName(fullPath);

        if (existingItem != null) {
            if (ctx.overwrite) {
                handleOverwrite(existingItem, node, path, result, ctx);
            } else if (ctx.autoRename) {
                if (!path.equals(node.fullPath)) {
                    handleAutoRenameResolved(node, path, result, ctx);
                } else {
                    handleAutoRename(node, path, result, ctx);
                }
            } else {
                result.setStatusEnum(Status.SKIP_EXISTS);
                result.skipped = true;
                result.message = Messages.ExecutionEngine_jobExistsSkipped();
            }
        } else {
            createJob(node, path, result, ctx);
        }
    }

    private void handleOverwrite(Item existingItem, TreeNode node, String path,
            ImportResult result, ImportContext ctx) {
        if (ctx.dryRun) {
            if (existingItem instanceof Job) {
                String existingType = TypeResolver.getJobTypeFromItemClass(existingItem.getClass());
                String newType = SecureXmlParser.getJobTypeFromXmlSafe(node.configXml);

                if (existingType != null && newType != null && !existingType.equals(newType)) {
                    result.setStatusEnum(Status.ERROR);
                    result.success = false;
                    result.message = Messages.ExecutionEngine_jobTypeMismatchCannotOverwrite(existingType, newType);
                    ctx.parentTypeErrors.add(path);
                    return;
                }
            }
        } else {
            try {
                backupConfig(existingItem);
                if (existingItem instanceof AbstractItem) {
                    byte[] sanitizedXml = SecureXmlParser.sanitizeJobConfig(node.configXml);
                    try (InputStream in = new ByteArrayInputStream(sanitizedXml)) {
                        ((AbstractItem) existingItem).updateByXml(new StreamSource(in));
                    }
                    ((AbstractItem) existingItem).save();
                } else {
                    existingItem.delete();
                    createJobDirect(path, node.configXml, ctx);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to overwrite job: " + path, e);
                result.setStatusEnum(Status.ERROR);
                result.message = Messages.ExecutionEngine_overwriteFailed(e.getMessage());
                return;
            }
        }
        result.setStatusEnum(Status.OVERWRITE_JOB);
        result.success = true;
        result.message = ctx.dryRun
                ? Messages.ExecutionEngine_willOverwriteJobConfig()
                : Messages.ExecutionEngine_overwroteJobConfig();
    }

    private void handleAutoRename(TreeNode node, String path, ImportResult result, ImportContext ctx) {
        String parentPath = FolderCreator.getParentPath(path);
        String newName = generateUniqueName(ctx.targetGroup, parentPath,
                FolderCreator.getLastPathSegment(path), ctx);
        String newPath = parentPath.isEmpty() ? newName : parentPath + "/" + newName;

        ctx.renameMap.put(path, newPath);

        result.finalName = newPath;
        result.renamed = true;

        if (node.hasConfigXml) {
            result.setStatusEnum(Status.RENAME_JOB);
            result.message = Messages.ExecutionEngine_jobRenamedTo(newName);

            if (!ctx.dryRun) {
                try {
                    createJobDirect(newPath, node.configXml, ctx);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to create renamed job: " + newPath, e);
                    result.setStatusEnum(Status.ERROR);
                    result.message = Messages.ExecutionEngine_createFailed(e.getMessage());
                    return;
                }
            }
        } else {
            result.setStatusEnum(Status.RENAME_FOLDER);
            result.message = Messages.ExecutionEngine_dirRenamedTo(newName);

            if (!ctx.dryRun) {
                try {
                    folderCreator.ensureFolderPath(ctx.targetGroup, newPath, true, ctx);
                    ctx.createdFolders.add(FolderCreator.getFullPath(newPath, ctx));
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to create renamed folder: " + newPath, e);
                    result.setStatusEnum(Status.ERROR);
                    result.message = Messages.ExecutionEngine_createDirFailed(e.getMessage());
                    return;
                }
            }
        }
        result.success = true;
    }

    private void handleAutoRenameResolved(TreeNode node, String resolvedPath,
            ImportResult result, ImportContext ctx) {
        String jobName = FolderCreator.getLastPathSegment(resolvedPath);

        result.finalName = resolvedPath;
        result.renamed = true;
        result.setStatusEnum(Status.RENAME_JOB);
        result.message = Messages.ExecutionEngine_jobRenamedTo(jobName);

        if (!ctx.dryRun) {
            try {
                createJobDirect(resolvedPath, node.configXml, ctx);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to create renamed job: " + resolvedPath, e);
                result.setStatusEnum(Status.ERROR);
                result.message = Messages.ExecutionEngine_createFailed(e.getMessage());
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
                LOGGER.log(Level.WARNING, "Failed to create job: " + path, e);
                result.setStatusEnum(Status.ERROR);
                result.message = Messages.ExecutionEngine_createFailed(e.getMessage());
                return;
            }
        }
        result.setStatusEnum(Status.CREATE_JOB);
        result.success = true;
        result.message = ctx.dryRun
                ? Messages.ExecutionEngine_willCreateJob()
                : Messages.ExecutionEngine_createdJob();
    }

    /**
     * 直接创建 Job（核心创建逻辑）
     */
    void createJobDirect(String path, byte[] configXml, ImportContext ctx) throws Exception {
        String folderPath = FolderCreator.getParentPath(path);
        String jobName = FolderCreator.getLastPathSegment(path);

        ItemGroup parentGroup = folderCreator.ensureFolderPath(ctx.targetGroup, folderPath, true, ctx);
        if (!(parentGroup instanceof ModifiableTopLevelItemGroup)) {
            throw new Exception(Messages.ExecutionEngine_createFailed("Parent group does not support creating jobs"));
        }

        ModifiableTopLevelItemGroup modifiableGroup = (ModifiableTopLevelItemGroup) parentGroup;

        TopLevelItemDescriptor descriptor = SecureXmlParser.determineJobDescriptor(configXml);
        if (descriptor == null) {
            throw new Exception(Messages.ExecutionEngine_createFailed(Messages.ExecutionEngine_unknownJobType()));
        }

        TopLevelItem item = modifiableGroup.createProject(descriptor, jobName, false);

        try {
            byte[] sanitizedXml = SecureXmlParser.sanitizeJobConfig(configXml);
            try (InputStream in = new ByteArrayInputStream(sanitizedXml)) {
                ((AbstractItem) item).updateByXml(new StreamSource(in));
            }
            ((AbstractItem) item).save();
            ctx.createdJobs.add(FolderCreator.getFullPath(path, ctx));
        } catch (Exception e) {
            try {
                item.delete();
            } catch (Exception deleteEx) {
                LOGGER.log(Level.WARNING, "Failed to cleanup partially created job: {0}", deleteEx.getMessage());
            }
            throw e;
        }
    }

    /**
     * 检查是否应该因为父目录被跳过而跳过此任务
     */
    private boolean shouldSkipDueToParentFolder(String originalPath, String resolvedPath,
            List<String> folderPathsToCreate, ImportContext ctx) {
        if (ctx.overwrite || ctx.autoRename) {
            return false;
        }

        for (String folderPath : folderPathsToCreate) {
            String resolvedFolderPath = renameResolver.resolvePath(folderPath, ctx);

            if (resolvedPath.startsWith(resolvedFolderPath + "/")) {
                if (ctx.dryRun) {
                    String fullFolderPath = FolderCreator.getFullPath(resolvedFolderPath, ctx);
                    if (ctx.virtualFolders.contains(fullFolderPath)) {
                        return false;
                    }
                    return true;
                }

                String fullFolderPath = FolderCreator.getFullPath(resolvedFolderPath, ctx);
                Item existingItem = Jenkins.get().getItemByFullName(fullFolderPath);
                if (existingItem != null) {
                    if (ctx.createdFolders.contains(fullFolderPath)) {
                        return false;
                    }
                    return false;
                }
            }
        }
        return false;
    }

    private ImportResult createResult(TreeNode node, String path, ImportContext ctx) {
        String folderPath = FolderCreator.getParentPath(path);
        String jobName = FolderCreator.getLastPathSegment(path);
        ImportResult result = new ImportResult(jobName, folderPath);
        result.finalName = path;
        result.fullPath = FolderCreator.getFullPath(path, ctx);
        result.sourcePath = node.fullPath;
        result.displayPath = node.fullPath;
        result.isFolder = (node.type == NodeType.FOLDER);
        result.isJob = (node.type == NodeType.JOB);
        return result;
    }

    /**
     * 生成唯一的名称（支持 Folder 和 Job，检查当前会话中刚创建的项目）
     * 带有最大尝试次数限制，防止无限循环
     */
    String generateUniqueName(ItemGroup itemGroup, String folderPath, String baseName, ImportContext ctx) {
        int counter = 2;
        String candidate;
        String fullPath;
        String checkPath;

        do {
            candidate = baseName + "_" + counter;
            counter++;
            fullPath = folderPath.isEmpty() ? candidate : folderPath + "/" + candidate;
            checkPath = FolderCreator.getFullPath(fullPath, ctx);
            if (counter > MAX_UNIQUE_NAME_ATTEMPTS) {
                LOGGER.log(Level.WARNING, "Exceeded max attempts ({0}) to generate unique name for: {1}",
                        new Object[]{MAX_UNIQUE_NAME_ATTEMPTS, baseName});
                break;
            }
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
}
