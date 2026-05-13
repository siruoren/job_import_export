package com.siruoren.jobimportexport.engine;

import java.util.Map;

public class PathResolver {

    public String resolve(String path, Map<String, String> renameMap) {

        if (renameMap == null || renameMap.isEmpty()) {
            return path;
        }

        boolean changed = true;

        while (changed) {
            changed = false;

            for (Map.Entry<String, String> e : renameMap.entrySet()) {
                String from = e.getKey();
                String to = e.getValue();

                if (path.equals(from)) {
                    path = to;
                    changed = true;
                    break;
                }

                if (path.startsWith(from + "/")) {
                    path = to + path.substring(from.length());
                    changed = true;
                    break;
                }
            }
        }

        return path;
    }
}
