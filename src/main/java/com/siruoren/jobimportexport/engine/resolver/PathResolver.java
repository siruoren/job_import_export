package com.siruoren.jobimportexport.engine.resolver;

import com.siruoren.jobimportexport.engine.model.ImportContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PathResolver {

    public String resolve(String path, ImportContext ctx) {
        boolean changed = true;

        while (changed) {
            changed = false;

            List<String> sortedKeys = new ArrayList<>(ctx.renameMap.keySet());
            sortedKeys.sort((a, b) -> b.length() - a.length());

            for (String from : sortedKeys) {
                String to = ctx.renameMap.get(from);

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
