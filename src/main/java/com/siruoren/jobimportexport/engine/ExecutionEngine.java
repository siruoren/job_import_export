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
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 导入执行引擎（协调者）。
 * 负责协调 FolderCreator、JobCreator、PermissionChecker 等组件完成导入流程。
 * 本类不再直接包含业务逻辑，仅负责流程编排。
 */
public class ExecutionEngine {

    private static final Logger LOGGER = Logger.getLogger(ExecutionEngine.class.getName());

    /** 根节点名称常量，避免硬编码 */
    private static final String ROOT_NODE_NAME = "/";

    private final RenameDAGResolver renameResolver = new RenameDAGResolver();
    private final FolderCreator folderCreator = new FolderCreator(renameResolver);
    private final JobCreator jobCreator = new JobCreator(renameResolver, folderCreator);
    private final ResultCollector resultCollector = new ResultCollector();

    public void setProgressCallback(ResultCollector.ProgressCallback callback) {
        this.resultCollector.setProgressCallback(callback);
    }

    /**
     * 执行导入流程。
     *
     * 阶段0：收集路径与重命名映射
     * 阶段1：创建所有 Folder
     * 阶段2：创建所有 Job
     * 阶段3：处理根目录 config.xml
     *
     * @param root 树根节点
     * @param ctx  导入上下文
     * @return 导入结果列表
     */
    public List<ImportResult> execute(TreeNode root, ImportContext ctx) {
        resultCollector.clear();

        // 阶段0：收集路径
        ImportPlan plan = collectImportPlan(root, ctx);

        // 设置结果收集器的 jobPaths（供 PermissionChecker 使用）
        resultCollector.setJobPaths(new ArrayList<>(plan.jobNodesToCreate.keySet()));
        resultCollector.setEstimatedTotal(
                plan.folderPathsToCreate.size() + plan.jobNodesToCreate.size()
                + (root.rootConfigXml != null && root.rootConfigXml.length > 0
                        && ctx.applyRootConfigToCurrentFolder ? 1 : 0));

        // 预扫描重命名映射
        if (ctx.autoRename) {
            collectAllRenames(plan, ctx);
        }

        // 阶段1：创建所有 Folder
        folderCreator.createAllFolders(
                plan.folderPathsToCreate, plan.folderWithConfigToCreate, resultCollector, ctx);

        // 阶段2：创建所有 Job
        jobCreator.createAllJobs(
                plan.jobNodesToCreate, plan.folderPathsToCreate, resultCollector, ctx);

        // 阶段3：处理根目录 config.xml
        handleRootConfigXml(root, ctx);

        // 本地化状态显示文本
        resultCollector.localizeResults();

        return resultCollector.getResults();
    }

    /**
     * 阶段0：收集所有需要创建的路径（不实际创建）
     */
    private ImportPlan collectImportPlan(TreeNode root, ImportContext ctx) {
        prepareRootConfigFromMatchingFolder(root, ctx);

        ImportPlan plan = new ImportPlan();
        collectPaths(root, "", ctx, plan);
        return plan;
    }

    /**
     * 准备根配置：从匹配当前目录名的节点中提取 config.xml
     */
    private void prepareRootConfigFromMatchingFolder(TreeNode root, ImportContext ctx) {
        if (!ctx.applyRootConfigToCurrentFolder || ctx.currentFolderItem == null) {
            return;
        }

        String folderName = ctx.currentFolderItem.getName();
        TreeNode matchingChild = root.children.get(folderName);
        if (matchingChild == null || matchingChild.configXml == null || matchingChild.configXml.length == 0) {
            return;
        }

        root.rootConfigXml = matchingChild.configXml;

        for (TreeNode child : matchingChild.children.values()) {
            root.children.put(child.name, child);
        }

        root.children.remove(folderName);
    }

    /**
     * 递归收集路径
     */
    private void collectPaths(TreeNode node, String parent, ImportContext ctx, ImportPlan plan) {
        String path = parent.isEmpty() ? node.name : parent + "/" + node.name;

        // 使用常量替代硬编码的 "/"
        if (!ROOT_NODE_NAME.equals(node.name)) {
            if (node.type == NodeType.FOLDER_WITH_CONFIG) {
                plan.folderPathsToCreate.add(path);
                plan.folderWithConfigToCreate.put(path, node);
            } else if (node.hasConfigXml) {
                plan.jobNodesToCreate.put(path, node);
            } else {
                plan.folderPathsToCreate.add(path);
            }
        }

        for (TreeNode child : node.children.values()) {
            collectPaths(child, path, ctx, plan);
        }
    }

    /**
     * 阶段0.5：预扫描所有冲突，收集完整的 renameMap
     */
    private void collectAllRenames(ImportPlan plan, ImportContext ctx) {
        Set<String> allPaths = new HashSet<>();
        allPaths.addAll(plan.folderPathsToCreate);
        allPaths.addAll(plan.jobNodesToCreate.keySet());

        List<String> sortedPaths = new ArrayList<>(allPaths);
        sortedPaths.sort(this::compareByDepth);

        for (String originalPath : sortedPaths) {
            String resolvedPath = renameResolver.resolvePath(originalPath, ctx);

            String fullPath = FolderCreator.getFullPath(resolvedPath, ctx);
            Item existingItem = Jenkins.get().getItemByFullName(fullPath);

            if (existingItem != null) {
                String parentPath = FolderCreator.getParentPath(resolvedPath);
                String newName = jobCreator.generateUniqueName(
                        ctx.targetGroup, parentPath,
                        FolderCreator.getLastPathSegment(resolvedPath), ctx);
                String newPath = parentPath.isEmpty() ? newName : parentPath + "/" + newName;
                ctx.renameMap.put(originalPath, newPath);
            }
        }
    }

    /**
     * 阶段3：处理根目录 config.xml
     */
    private void handleRootConfigXml(TreeNode root, ImportContext ctx) {
        if (root.rootConfigXml == null || root.rootConfigXml.length == 0) {
            return;
        }

        if (!ctx.applyRootConfigToCurrentFolder || ctx.currentFolderItem == null) {
            return;
        }

        Item folderItem = ctx.currentFolderItem;
        String folderName = folderItem.getName();
        String folderFullName = (folderItem instanceof AbstractItem)
                ? ((AbstractItem) folderItem).getFullName()
                : folderName;

        ImportResult result = new ImportResult(folderName, "");
        result.finalName = folderName;
        result.fullPath = folderFullName;
        result.sourcePath = folderName;
        result.displayPath = folderName;
        result.isFolder = true;
        result.isJob = false;

        if (ctx.dryRun) {
            result.setStatusEnum(Status.UPDATE_CONFIG);
            result.success = true;
            result.message = Messages.ExecutionEngine_willUpdateConfig();
        } else {
            try {
                if (folderItem instanceof AbstractItem) {
                    AbstractItem abstractItem = (AbstractItem) folderItem;
                    try (InputStream in = new ByteArrayInputStream(root.rootConfigXml)) {
                        abstractItem.updateByXml(new StreamSource(in));
                        abstractItem.save();
                    }
                }
                result.setStatusEnum(Status.UPDATE_CONFIG);
                result.success = true;
                result.message = Messages.ExecutionEngine_updatedConfig();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to update root config for: " + folderName, e);
                result.setStatusEnum(Status.ERROR);
                result.success = false;
                result.message = Messages.ExecutionEngine_updateConfigFailed(e.getMessage());
            } catch (Error e) {
                // 严重错误（如 OutOfMemoryError）不允许被吞掉，向上传播
                throw e;
            }
        }

        resultCollector.addResult(result);
        ctx.rootConfigResults.add(result);
    }

    private int compareByDepth(String a, String b) {
        int depthA = a.split("/").length;
        int depthB = b.split("/").length;
        if (depthA != depthB) return depthA - depthB;
        return a.compareTo(b);
    }

    public List<ImportResult> getResults() {
        return resultCollector.getResults();
    }

    /**
     * 导入计划数据容器，用于在收集阶段传递数据。
     * 替代原先的实例变量，使 ExecutionEngine 成为无状态协调者。
     */
    static class ImportPlan {
        final List<String> folderPathsToCreate = new ArrayList<>();
        final Map<String, TreeNode> jobNodesToCreate = new LinkedHashMap<>();
        final Map<String, TreeNode> folderWithConfigToCreate = new LinkedHashMap<>();
    }
}
