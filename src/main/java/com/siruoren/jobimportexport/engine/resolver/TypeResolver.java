package com.siruoren.jobimportexport.engine.resolver;

import com.siruoren.jobimportexport.engine.model.NodeType;
import com.siruoren.jobimportexport.engine.model.TreeNode;

/**
 * 类型解析器，负责节点类型和 Job 类型的判断。
 */
public class TypeResolver {

    public NodeType resolve(TreeNode node) {
        if (node.hasConfigXml) {
            return NodeType.JOB;
        }
        return NodeType.FOLDER;
    }

    public NodeType resolveWithDefault(TreeNode node, NodeType defaultType) {
        if (node.hasConfigXml) {
            return NodeType.JOB;
        }
        return defaultType;
    }

    /**
     * 根据 Item 的 Class 获取 Job 类型名称
     *
     * @param clazz Item 的 Class 对象
     * @return Job 类型名称字符串
     */
    public static String getJobTypeFromItemClass(Class<?> clazz) {
        String name = clazz.getName();
        if (name.contains("FreeStyleProject")) return "Freestyle";
        if (name.contains("WorkflowJob")) return "Pipeline";
        if (name.contains("MavenModuleSet")) return "Maven";
        if (name.contains("MatrixProject")) return "Matrix";
        return clazz.getSimpleName();
    }
}
