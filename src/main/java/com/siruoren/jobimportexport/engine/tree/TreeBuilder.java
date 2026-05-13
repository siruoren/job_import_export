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
                current.type = NodeType.JOB;
            }
        }

        return root;
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
                current.type = NodeType.JOB;
                current.configXml = readAllBytes(zipInputStream);
            }

            zipInputStream.closeEntry();
        }

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
