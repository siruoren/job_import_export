package com.siruoren.jobimportexport.engine.tree;

import com.siruoren.jobimportexport.engine.model.NodeType;
import com.siruoren.jobimportexport.engine.model.TreeNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;

public class ZipTreeBuilder {

    public TreeNode build(List<ZipEntry> entries) {

        TreeNode root = new TreeNode();
        root.name = "/";
        root.fullPath = "/";

        Map<String, TreeNode> index = new HashMap<>();
        index.put("/", root);

        for (ZipEntry entry : entries) {

            String path = entry.getName();

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
}
