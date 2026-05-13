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
                    finalCurrent.children.add(n);
                    return n;
                });
            }

            current.isLeaf = true;

            if (path.endsWith("config.xml")) {
                current.hasConfigXml = true;
            }
        }

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
                    finalCurrent.children.add(n);
                    return n;
                });
            }

            current.isLeaf = true;

            if (path.endsWith("config.xml")) {
                current.hasConfigXml = true;
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