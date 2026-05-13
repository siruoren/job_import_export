package com.siruoren.jobimportexport.engine.tree;

import com.siruoren.jobimportexport.engine.model.Node;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipTreeBuilder {

    public Node build(List<String> zipPaths) {
        Node root = new Node();
        root.fullPath = "/";

        Map<String, Node> index = new HashMap<>();
        index.put("/", root);

        for (String path : zipPaths) {
            // 规则：config.xml 是 metadata，不是独立节点
            if (path.endsWith("config.xml")) {
                String folderPath = getParentPath(path);
                if (folderPath != null && !folderPath.isEmpty()) {
                    // 确保路径树存在
                    ensurePathTree(folderPath, root, index);
                    // 附加 config.xml 到父目录节点
                    Node folderNode = index.get(folderPath);
                    if (folderNode != null) {
                        folderNode.hasConfigXml = true;
                        folderNode.isJob = true;
                    }
                }
            } else {
                // 非 config.xml 文件，确保路径树存在
                ensurePathTree(path, root, index);
            }
        }

        // 扫描整棵树的 config.xml 分布
        computeHasConfig(root);

        return root;
    }

    public Node build(ZipInputStream zipInputStream) throws IOException {
        Node root = new Node();
        root.fullPath = "/";

        Map<String, Node> index = new HashMap<>();
        index.put("/", root);

        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            String path = entry.getName();
            if (path.isEmpty()) {
                zipInputStream.closeEntry();
                continue;
            }

            // 规则：config.xml 是 metadata，不是独立节点
            if (path.endsWith("config.xml")) {
                String folderPath = getParentPath(path);
                if (folderPath != null && !folderPath.isEmpty()) {
                    // 确保路径树存在
                    ensurePathTree(folderPath, root, index);
                    // 附加 config.xml 到父目录节点
                    Node folderNode = index.get(folderPath);
                    if (folderNode != null) {
                        folderNode.hasConfigXml = true;
                        folderNode.isJob = true;
                        folderNode.configXml = readAllBytes(zipInputStream);
                    }
                }
            } else {
                // 非 config.xml 文件，确保路径树存在
                ensurePathTree(path, root, index);
            }

            zipInputStream.closeEntry();
        }

        // 扫描整棵树的 config.xml 分布
        computeHasConfig(root);

        return root;
    }

    private void ensurePathTree(String path, Node root, Map<String, Node> index) {
        String[] parts = path.split("/");
        Node current = root;
        StringBuilder full = new StringBuilder();

        for (String p : parts) {
            if (p.isEmpty()) continue;

            if (full.length() > 0) full.append("/");
            full.append(p);

            String key = full.toString();

            final Node finalCurrent = current;
            current = index.computeIfAbsent(key, k -> {
                Node n = new Node();
                n.name = p;
                n.fullPath = k;
                n.isFolder = true;
                n.isJob = false;
                finalCurrent.children.add(n);
                return n;
            });
        }
    }

    private boolean computeHasConfig(Node node) {
        boolean self = node.hasConfigXml;
        boolean child = false;

        for (Node c : node.children) {
            child |= computeHasConfig(c);
        }

        node.hasAnyConfigInSubtree = self || child;
        return node.hasAnyConfigInSubtree;
    }

    private String getParentPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) return null;
        return path.substring(0, lastSlash);
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
