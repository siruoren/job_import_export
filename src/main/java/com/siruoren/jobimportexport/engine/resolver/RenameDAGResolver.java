package com.siruoren.jobimportexport.engine.resolver;

import com.siruoren.jobimportexport.engine.model.ImportContext;

import java.util.Map;

public class RenameDAGResolver {

    public String resolvePath(String path, ImportContext ctx) {
        if (ctx.renameMap == null || ctx.renameMap.isEmpty()) {
            return normalizePath(path);
        }

        boolean changed = true;
        String resolved = path;

        while (changed) {
            changed = false;

            for (Map.Entry<String, String> entry : ctx.renameMap.entrySet()) {
                String from = entry.getKey();
                String to = entry.getValue();

                if (resolved.equals(from)) {
                    resolved = to;
                    changed = true;
                    break;
                }

                if (resolved.startsWith(from + "/")) {
                    resolved = to + resolved.substring(from.length());
                    changed = true;
                    break;
                }
            }
        }

        return normalizePath(resolved);
    }

    public String resolvePathSingle(String path, ImportContext ctx) {
        if (ctx.renameMap == null || ctx.renameMap.isEmpty()) {
            return normalizePath(path);
        }

        for (Map.Entry<String, String> entry : ctx.renameMap.entrySet()) {
            String from = entry.getKey();
            String to = entry.getValue();

            if (path.equals(from)) {
                return normalizePath(to);
            }

            if (path.startsWith(from + "/")) {
                return normalizePath(to + path.substring(from.length()));
            }
        }

        return normalizePath(path);
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }

        path = path.replaceAll("/+", "/");

        if (path.startsWith("/")) {
            path = path.substring(1);
        }

        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }

        return path;
    }
}
