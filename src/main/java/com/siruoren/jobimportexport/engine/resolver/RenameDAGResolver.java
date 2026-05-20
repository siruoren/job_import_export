package com.siruoren.jobimportexport.engine.resolver;

import com.siruoren.jobimportexport.engine.model.ImportContext;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 重命名 DAG 传播解析器。
 * 负责将重命名映射通过 DAG 传播方式应用到路径上。
 */
public class RenameDAGResolver {

    private static final Logger LOGGER = Logger.getLogger(RenameDAGResolver.class.getName());

    /** 最大传播迭代次数，防止循环映射导致无限循环 */
    private static final int MAX_PROPAGATION_ITERATIONS = 100;

    /**
     * 使用 DAG 传播解析路径（带循环检测）
     *
     * @param path      需要解析的原始路径
     * @param renameMap 重命名映射
     * @return 解析后的最终路径
     * @throws IllegalStateException 如果检测到循环映射或超过最大迭代次数
     */
    public String resolvePath(String path, Map<String, String> renameMap) {
        if (renameMap == null || renameMap.isEmpty()) {
            return normalizePath(path);
        }

        String resolved = path;
        boolean changed = true;
        int iterations = 0;
        Set<String> visitedPaths = new HashSet<>();
        visitedPaths.add(resolved);

        while (changed) {
            changed = false;
            iterations++;

            if (iterations > MAX_PROPAGATION_ITERATIONS) {
                LOGGER.log(Level.WARNING, "Rename DAG propagation exceeded max iterations ({0}) for path: {1}",
                        new Object[]{MAX_PROPAGATION_ITERATIONS, path});
                throw new IllegalStateException(
                        "Rename DAG propagation exceeded max iterations (" + MAX_PROPAGATION_ITERATIONS
                                + "), possible circular mapping detected for path: " + path);
            }

            for (Map.Entry<String, String> entry : renameMap.entrySet()) {
                String from = entry.getKey();
                String to = entry.getValue();

                if (resolved.equals(from)) {
                    resolved = to;
                    if (!visitedPaths.add(resolved)) {
                        throw new IllegalStateException(
                                "Circular rename mapping detected: path resolved back to '" + resolved
                                        + "' during DAG propagation for original path: " + path);
                    }
                    changed = true;
                    break;
                }

                if (resolved.startsWith(from + "/")) {
                    resolved = to + resolved.substring(from.length());
                    if (!visitedPaths.add(resolved)) {
                        throw new IllegalStateException(
                                "Circular rename mapping detected: path resolved back to '" + resolved
                                        + "' during DAG propagation for original path: " + path);
                    }
                    changed = true;
                    break;
                }
            }
        }

        return normalizePath(resolved);
    }

    /**
     * 使用 DAG 传播解析路径（带循环检测）
     *
     * @param path 需要解析的原始路径
     * @param ctx  导入上下文（包含 renameMap）
     * @return 解析后的最终路径
     * @throws IllegalStateException 如果检测到循环映射或超过最大迭代次数
     */
    public String resolvePath(String path, ImportContext ctx) {
        return resolvePath(path, ctx.renameMap);
    }

    /**
     * 单次解析（不传播，仅匹配一次）
     */
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
