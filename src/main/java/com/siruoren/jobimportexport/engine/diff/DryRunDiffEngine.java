package com.siruoren.jobimportexport.engine.diff;

import com.siruoren.jobimportexport.engine.model.*;
import hudson.model.Item;
import jenkins.model.Jenkins;

import java.util.*;

public class DryRunDiffEngine {

    public List<Diff> dryRun(TreeNode root) {
        List<Diff> result = new ArrayList<>();
        dryRun(root, "", result);
        return result;
    }

    /**
     * 带 rename DAG 传播的 dry-run
     * @param root 树根节点
     * @param autoRename 是否自动重命名冲突项
     * @return DryRunResult 包含 folderActions 和 jobActions
     */
    public DryRunResult dryRunWithRenameDag(TreeNode root, boolean autoRename) {
        DryRunResult result = new DryRunResult();
        // 按路径长度排序，确保 parent 在 child 之前处理
        Map<String, String> renameMap = new LinkedHashMap<>();
        List<String> orderedPaths = collectAllPaths(root);
        Collections.sort(orderedPaths, (a, b) -> {
            // 路径短的先处理（parent 先于 child）
            int depthA = a.split("/").length;
            int depthB = b.split("/").length;
            if (depthA != depthB) return depthA - depthB;
            return a.compareTo(b);
        });
        
        // 按顺序处理每个路径
        for (String originalPath : orderedPaths) {
            // 应用已有的 renameMap，计算当前路径的最终位置
            String resolvedPath = resolveWithDag(originalPath, renameMap);
            
            // 检查 Jenkins 中是否存在
            Item existingItem = Jenkins.get().getItemByFullName(resolvedPath);
            
            // 判断节点是否有 config.xml
            boolean hasConfig = hasConfigXmlFromTree(root, originalPath);
            
            // 只有没有 config.xml 的节点才作为 Folder 处理
            if (!hasConfig) {
                // Folder 处理
                if (existingItem != null && existingItem instanceof hudson.model.ItemGroup) {
                    if (autoRename) {
                        // 自动重命名：基于已解析的父路径计算新名称
                        String parentPath = getParentPath(resolvedPath);
                        String newName = generateUniqueName(parentPath, getLastPathSegment(resolvedPath));
                        String newPath = parentPath.isEmpty() ? newName : parentPath + "/" + newName;
                        
                        // 记录 rename DAG（原始路径 -> 最终路径）
                        renameMap.put(originalPath, newPath);
                        
                        result.addFolderAction(new NodeAction(originalPath, Action.RENAME, "目录已重命名为: " + newPath));
                    } else {
                        result.addFolderAction(new NodeAction(resolvedPath, Action.REUSE, "文件夹已存在，复用"));
                    }
                } else if (existingItem != null) {
                    result.addFolderAction(new NodeAction(resolvedPath, Action.CREATE_FOLDER, "路径已存在但不是文件夹"));
                } else {
                    result.addFolderAction(new NodeAction(resolvedPath, Action.CREATE_FOLDER, "将创建文件夹"));
                }
            }
            
            // 有 config.xml 的节点作为 Job 处理
            if (hasConfig) {
                // Job 处理
                if (existingItem != null) {
                    if (autoRename) {
                        // 自动重命名：基于已解析的父路径计算新名称
                        String parentPath = getParentPath(resolvedPath);
                        String newName = generateUniqueJobName(parentPath, getLastPathSegment(resolvedPath));
                        String newPath = parentPath.isEmpty() ? newName : parentPath + "/" + newName;
                        
                        // 记录 rename DAG（原始路径 -> 最终路径）
                        renameMap.put(originalPath, newPath);
                        
                        result.addJobAction(new NodeAction(originalPath, Action.RENAME, "任务已重命名为: " + newPath));
                    } else {
                        result.addJobAction(new NodeAction(resolvedPath, Action.OVERWRITE, "将覆盖现有任务"));
                    }
                } else {
                    result.addJobAction(new NodeAction(resolvedPath, Action.CREATE_JOB, "将创建任务"));
                }
            }
        }
        
        return result;
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
     * 收集树中所有路径
     */
    private List<String> collectAllPaths(TreeNode node) {
        List<String> paths = new ArrayList<>();
        collectPathsRecursive(node, "", paths);
        return paths;
    }
    
    private void collectPathsRecursive(TreeNode node, String parent, List<String> paths) {
        if ("/".equals(node.name)) {
            // ROOT 节点，递归处理子节点
            for (TreeNode child : node.children.values()) {
                collectPathsRecursive(child, "", paths);
            }
            return;
        }
        
        String path = parent.isEmpty() ? node.name : parent + "/" + node.name;
        paths.add(path);
        
        for (TreeNode child : node.children.values()) {
            collectPathsRecursive(child, path, paths);
        }
    }
    
    /**
     * 根据路径从树中获取节点类型
     */
    private NodeType getNodeTypeFromTree(TreeNode root, String path) {
        if (path.isEmpty()) return NodeType.FOLDER;
        
        String[] parts = path.split("/");
        TreeNode current = root;
        
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (current.children == null || current.children.isEmpty()) {
                return NodeType.JOB; // 最后一个部分
            }
            if (!current.children.containsKey(part)) {
                return NodeType.FOLDER; // 假设是文件夹
            }
            current = current.children.get(part);
        }
        
        return current.type;
    }
    
    /**
     * 根据路径从树中检查是否有 config.xml
     */
    private boolean hasConfigXmlFromTree(TreeNode root, String path) {
        if (path.isEmpty()) return false;
        
        String[] parts = path.split("/");
        TreeNode current = root;
        
        for (String part : parts) {
            if (current.children == null || !current.children.containsKey(part)) {
                return false;
            }
            current = current.children.get(part);
        }
        
        return current.hasConfigXml;
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
    
    private String generateUniqueJobName(String folderPath, String jobName) {
        String baseName = jobName;
        int counter = 2;
        String candidate;
        
        do {
            candidate = baseName + "_" + counter;
            counter++;
        } while (Jenkins.get().getItemByFullName(folderPath.isEmpty() ? candidate : folderPath + "/" + candidate) != null);
        
        return candidate;
    }
    
    /**
     * 生成唯一的名称（支持 Folder 和 Job）
     */
    private String generateUniqueName(String folderPath, String baseName) {
        int counter = 2;
        String candidate;
        String fullPath;
        
        do {
            candidate = baseName + "_" + counter;
            counter++;
            fullPath = folderPath.isEmpty() ? candidate : folderPath + "/" + candidate;
        } while (Jenkins.get().getItemByFullName(fullPath) != null);
        
        return candidate;
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
