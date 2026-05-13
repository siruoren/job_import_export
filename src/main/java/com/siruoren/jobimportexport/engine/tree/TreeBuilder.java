package com.siruoren.jobimportexport.engine.tree;

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
        root.fullPath = "/";

        Map<String, TreeNode> index = new HashMap<>();
        index.put("/", root);

        for (String entry : zipEntries) {
            if (entry == null || entry.isEmpty()) {
                continue;
            }

            String[] parts = entry.split("/");
            TreeNode current = root;
            StringBuilder path = new StringBuilder();

            for (String p : parts) {
                if (p.isEmpty()) {
                    continue;
                }

                if (path.length() > 0) {
                    path.append("/");
                }
                path.append(p);

                String key = path.toString();

                final TreeNode finalCurrent = current;
                current = index.computeIfAbsent(key, k -> {
                    TreeNode n = new TreeNode();
                    n.name = p;
                    n.fullPath = k;
                    finalCurrent.children.add(n);
                    return n;
                });
            }

            // config.xml 不创建 node，只挂载到 parent
            if (entry.endsWith("config.xml")) {
                current.hasConfigXml = true;
            }
        }

        // 解析节点类型
        resolveTypes(root);

        return root;
    }

    public TreeNode buildTree(ZipInputStream zipInputStream) throws IOException {
        TreeNode root = new TreeNode();
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

            String[] parts = path.split("/");
            TreeNode current = root;
            StringBuilder fullPath = new StringBuilder();

            for (String p : parts) {
                if (p.isEmpty()) {
                    continue;
                }

                if (fullPath.length() > 0) {
                    fullPath.append("/");
                }
                fullPath.append(p);

                String key = fullPath.toString();

                final TreeNode finalCurrent = current;
                current = index.computeIfAbsent(key, k -> {
                    TreeNode n = new TreeNode();
                    n.name = p;
                    n.fullPath = k;
                    finalCurrent.children.add(n);
                    return n;
                });
            }

            // config.xml 不创建 node，只挂载到 parent
            if (path.endsWith("config.xml")) {
                current.hasConfigXml = true;
                current.configXml = readAllBytes(zipInputStream);
            }

            zipInputStream.closeEntry();
        }

        // 解析节点类型
        resolveTypes(root);

        return root;
    }

    private void resolveTypes(TreeNode node) {
        // 如果有 config.xml，标记为 JOB
        if (node.hasConfigXml) {
            node.type = com.siruoren.jobimportexport.engine.model.NodeType.JOB;
        } else {
            node.type = com.siruoren.jobimportexport.engine.model.NodeType.FOLDER;
        }

        // 递归处理子节点
        for (TreeNode child : node.children) {
            resolveTypes(child);
        }
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
