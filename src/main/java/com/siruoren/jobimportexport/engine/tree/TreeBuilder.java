package com.siruoren.jobimportexport.engine.tree;

import com.siruoren.jobimportexport.engine.model.NodeType;
import com.siruoren.jobimportexport.engine.model.TreeNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class TreeBuilder {

    public TreeNode buildTree(List<String> zipEntries) {
        TreeNode root = new TreeNode();
        root.name = "/";
        root.fullPath = "/";

        Map<String, TreeNode> index = new HashMap<>();
        index.put("/", root);

        for (String entry : zipEntries) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }

            String path = entry;

            // 🚨 config.xml 不作为节点
            boolean isConfig = path.endsWith("/config.xml");
            if (isConfig) {
                path = path.substring(0, path.length() - "/config.xml".length());
            }

            String[] parts = path.split("/");
            StringBuilder currentPath = new StringBuilder();
            TreeNode current = root;

            for (String part : parts) {
                if (part.isEmpty()) continue;

                if (currentPath.length() > 0) {
                    currentPath.append("/");
                }
                currentPath.append(part);

                String key = currentPath.toString();

                TreeNode child = current.children.get(part);
                if (child == null) {
                    child = new TreeNode();
                    child.name = part;
                    child.fullPath = key;
                    current.children.put(part, child);
                    index.put(key, child);
                }

                current = child;
            }

            // 🚨 config.xml 只附加 metadata
            if (isConfig) {
                current.hasConfigXml = true;
                // 标记为 JOB，后续会根据是否有子节点重新调整为 FOLDER_WITH_CONFIG
                current.type = NodeType.JOB;
            }
        }

        // 🚨 第二遍扫描：识别"有配置的目录类型"
        // 如果目录本身有 config.xml，且有子节点，则为 FOLDER_WITH_CONFIG
        propagateFolderWithConfig(root);

        return root;
    }

    /**
     * 识别"有配置的目录类型"
     * 规则：如果目录本身有 config.xml（hasConfigXml=true），且有子节点，
     * 则该目录为 FOLDER_WITH_CONFIG 类型（作为有配置的目录创建，只创建一次）
     */
    private void propagateFolderWithConfig(TreeNode node) {
        for (TreeNode child : node.children.values()) {
            // 如果当前目录有 config.xml，且有子节点，则为有配置的目录
            if (child.hasConfigXml && !child.children.isEmpty()) {
                child.type = NodeType.FOLDER_WITH_CONFIG;
            }
            // 递归处理子节点
            propagateFolderWithConfig(child);
        }
    }

    public TreeNode buildTree(ZipInputStream zipInputStream) throws IOException {
        TreeNode root = new TreeNode();
        root.name = "/";
        root.fullPath = "/";

        Map<String, TreeNode> index = new HashMap<>();
        index.put("/", root);

        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            String path = entry.getName();
            if (path == null || path.isEmpty()) {
                zipInputStream.closeEntry();
                continue;
            }

            // 🚨 config.xml 不作为节点
            boolean isConfig = path.endsWith("/config.xml");
            String nodePath = path;
            if (isConfig) {
                nodePath = path.substring(0, path.length() - "/config.xml".length());
            }

            String[] parts = nodePath.split("/");
            StringBuilder currentPath = new StringBuilder();
            TreeNode current = root;

            for (String part : parts) {
                if (part.isEmpty()) continue;

                if (currentPath.length() > 0) {
                    currentPath.append("/");
                }
                currentPath.append(part);

                String key = currentPath.toString();

                TreeNode child = current.children.get(part);
                if (child == null) {
                    child = new TreeNode();
                    child.name = part;
                    child.fullPath = key;
                    current.children.put(part, child);
                    index.put(key, child);
                }

                current = child;
            }

            // 🚨 config.xml 只附加 metadata
            if (isConfig) {
                current.hasConfigXml = true;
                // 标记为 JOB，后续会根据是否有子节点重新调整为 FOLDER_WITH_CONFIG
                current.type = NodeType.JOB;
                current.configXml = readAllBytes(zipInputStream);
            }

            zipInputStream.closeEntry();
        }

        // 🚨 第二遍扫描：识别"有配置的目录类型"
        // 如果目录本身有 config.xml，且有子节点，则为 FOLDER_WITH_CONFIG
        propagateFolderWithConfig(root);

        return root;
    }

    private byte[] readAllBytes(InputStream in) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }
}
