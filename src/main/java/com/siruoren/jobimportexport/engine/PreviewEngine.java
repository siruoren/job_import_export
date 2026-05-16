package com.siruoren.jobimportexport.engine;

import com.siruoren.jobimportexport.engine.diff.DryRunDiffEngine;
import com.siruoren.jobimportexport.engine.model.*;
import com.siruoren.jobimportexport.engine.tree.TreeBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipInputStream;

public class PreviewEngine {

    private final TreeBuilder treeBuilder = new TreeBuilder();
    private final DryRunDiffEngine diffEngine = new DryRunDiffEngine();
    private final ImportEngine importEngine = new ImportEngine();

    public List<Diff> previewWithDiff(ZipInputStream zipInputStream) throws IOException {
        return previewWithDiff(zipInputStream, null);
    }

    public List<Diff> previewWithDiff(ZipInputStream zipInputStream, ImportContext ctx) throws IOException {
        TreeNode root = treeBuilder.buildTree(zipInputStream);
        return diffEngine.dryRun(root, ctx);
    }

    public List<Diff> previewWithDiff(List<String> zipPaths) {
        return previewWithDiff(zipPaths, null);
    }

    public List<Diff> previewWithDiff(List<String> zipPaths, ImportContext ctx) {
        TreeNode root = treeBuilder.buildTree(zipPaths);
        return diffEngine.dryRun(root, ctx);
    }

    /**
     * 分层预览：Folders 和 Jobs 分开（带 rename DAG 传播）
     */
    public DryRunResult previewWithGroups(List<String> zipPaths, boolean autoRename) {
        return previewWithGroups(zipPaths, autoRename, null);
    }

    /**
     * 分层预览：Folders 和 Jobs 分开（带 rename DAG 传播，支持 targetGroup）
     */
    public DryRunResult previewWithGroups(List<String> zipPaths, boolean autoRename, ImportContext ctx) {
        TreeNode root = treeBuilder.buildTree(zipPaths);
        return diffEngine.dryRunWithRenameDag(root, autoRename, ctx);
    }

    /**
     * 分层预览：Folders 和 Jobs 分开（带 rename DAG 传播）
     */
    public DryRunResult previewWithGroups(ZipInputStream zipInputStream, boolean autoRename) throws IOException {
        return previewWithGroups(zipInputStream, autoRename, null);
    }

    /**
     * 分层预览：Folders 和 Jobs 分开（带 rename DAG 传播，支持 targetGroup）
     */
    public DryRunResult previewWithGroups(ZipInputStream zipInputStream, boolean autoRename, ImportContext ctx) throws IOException {
        TreeNode root = treeBuilder.buildTree(zipInputStream);
        return diffEngine.dryRunWithRenameDag(root, autoRename, ctx);
    }

    public List<DiffResult> preview(List<String> zipPaths, ImportContext ctx) {
        ctx.dryRun = true;
        List<ImportResult> results = importEngine.importZip(zipPaths, ctx);
        return collectDiff(results, ctx);
    }

    public List<DiffResult> preview(ZipInputStream zipInputStream, ImportContext ctx) throws IOException {
        ctx.dryRun = true;
        List<ImportResult> results = importEngine.importZip(zipInputStream, ctx);
        return collectDiff(results, ctx);
    }

    private List<DiffResult> collectDiff(List<ImportResult> results, ImportContext ctx) {
        List<DiffResult> diffs = new ArrayList<>();

        for (ImportResult result : results) {
            DiffResult diff = new DiffResult();
            diff.sourcePath = result.sourcePath;
            diff.targetPath = result.finalName;
            diff.status = result.status;
            diff.message = result.message;
            diff.missingPlugins = result.missingPlugins;

            // 根据状态设置 action
            if (result.statusEnum != null) {
                switch (result.statusEnum) {
                    case CREATE_JOB:
                        diff.action = Action.CREATE_JOB;
                        break;
                    case OVERWRITE_JOB:
                        diff.action = Action.OVERWRITE;
                        break;
                    case RENAME_JOB:
                        diff.action = Action.RENAME;
                        break;
                    case CREATE_FOLDER:
                        diff.action = Action.CREATE_FOLDER;
                        break;
                    case REUSE_FOLDER:
                        diff.action = Action.REUSE;
                        break;
                    case SKIP_EXISTS:
                    case SKIP_EMPTY:
                        diff.action = Action.SKIP_NO_CONFIG;
                        break;
                    case UPDATE_CONFIG:
                        diff.action = Action.UPDATE_CONFIG;
                        break;
                    default:
                        diff.action = Action.CREATE_FOLDER;
                }
            }

            diffs.add(diff);
        }

        return diffs;
    }
}
