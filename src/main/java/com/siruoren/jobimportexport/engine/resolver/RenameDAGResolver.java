package com.siruoren.jobimportexport.engine.resolver;

import com.siruoren.jobimportexport.engine.model.ImportContext;

import java.util.Map;

public class RenameDAGResolver {

    public String resolvePath(String path, ImportContext ctx) {
        if (ctx.renameMap == null || ctx.renameMap.isEmpty()) {
            return path;
        }

        boolean changed = true;
        String resolved = path;

        while (changed) {
            changed = false;

            for (Map.Entry<String, String> entry : ctx.renameMap.entrySet()) {
                String from = entry.getKey();
                String to = entry.getValue();

                // 完全匹配
                if (resolved.equals(from)) {
                    resolved = to;
                    changed = true;
                    break;
                }

                // 前缀匹配（必须是完整路径段）
                if (resolved.startsWith(from + "/")) {
                    resolved = to + resolved.substring(from.length());
                    changed = true;
                    break;
                }
            }
        }

        return resolved;
    }

    public String resolvePathSingle(String path, ImportContext ctx) {
        if (ctx.renameMap == null || ctx.renameMap.isEmpty()) {
            return path;
        }

        for (Map.Entry<String, String> entry : ctx.renameMap.entrySet()) {
            String from = entry.getKey();
            String to = entry.getValue();

            if (path.equals(from)) {
                return to;
            }

            if (path.startsWith(from + "/")) {
                return to + path.substring(from.length());
            }
        }

        return path;
    }
}
