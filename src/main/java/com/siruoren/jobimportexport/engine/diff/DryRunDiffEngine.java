package com.siruoren.jobimportexport.engine.diff;

import com.siruoren.jobimportexport.engine.model.*;
import hudson.model.Item;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.List;

public class DryRunDiffEngine {

    public List<Diff> dryRun(TreeNode root) {
        List<Diff> result = new ArrayList<>();
        dryRun(root, "", result);
        return result;
    }

    private void dryRun(TreeNode node, String parent, List<Diff> result) {
        String path = parent.isEmpty() ? node.name : parent + "/" + node.name;

        Item existingItem = Jenkins.get().getItemByFullName(path);

        // Folder 永远被处理
        if (existingItem != null && existingItem instanceof hudson.model.ItemGroup) {
            result.add(new Diff(
                path,
                Action.CREATE_FOLDER,
                "文件夹已存在，复用"
            ));
        } else if (existingItem != null) {
            result.add(new Diff(
                path,
                Action.CREATE_FOLDER,
                "路径已存在但不是文件夹"
            ));
        } else {
            result.add(new Diff(
                path,
                Action.CREATE_FOLDER,
                "将创建文件夹"
            ));
        }

        // Job 只在有 config.xml 时创建
        if (node.hasConfigXml) {
            if (existingItem != null) {
                result.add(new Diff(
                    path,
                    Action.OVERWRITE,
                    "将覆盖现有任务"
                ));
            } else {
                result.add(new Diff(
                    path,
                    Action.CREATE_JOB,
                    "将创建任务"
                ));
            }
        }

        // 递归处理子节点
        for (TreeNode child : node.children.values()) {
            dryRun(child, path, result);
        }
    }

    /**
     * 分层输出：Folders 和 Jobs 分开
     */
    public DryRunResult dryRunWithGroups(TreeNode root) {
        DryRunResult result = new DryRunResult();
        dryRunWithGroups(root, "", result);
        return result;
    }

    private void dryRunWithGroups(TreeNode node, String parent, DryRunResult result) {
        String path = parent.isEmpty() ? node.name : parent + "/" + node.name;

        Item existingItem = Jenkins.get().getItemByFullName(path);

        // ✔ Folder 处理（添加到 folderActions）
        Action folderAction;
        String folderMessage;
        if (existingItem != null && existingItem instanceof hudson.model.ItemGroup) {
            folderAction = Action.REUSE;
            folderMessage = "文件夹已存在，复用";
        } else if (existingItem != null) {
            folderAction = Action.CREATE_FOLDER;
            folderMessage = "路径已存在但不是文件夹";
        } else {
            folderAction = Action.CREATE_FOLDER;
            folderMessage = "将创建文件夹";
        }
        result.addFolderAction(new NodeAction(path, folderAction, folderMessage));

        // ✔ Job 只在有 config.xml 时处理（添加到 jobActions）
        if (node.hasConfigXml) {
            Action jobAction;
            String jobMessage;
            if (existingItem != null) {
                jobAction = Action.OVERWRITE;
                jobMessage = "将覆盖现有任务";
            } else {
                jobAction = Action.CREATE_JOB;
                jobMessage = "将创建任务";
            }
            result.addJobAction(new NodeAction(path, jobAction, jobMessage));
        }

        // 递归处理子节点
        for (TreeNode child : node.children.values()) {
            dryRunWithGroups(child, path, result);
        }
    }
}
