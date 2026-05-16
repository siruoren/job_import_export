package com.siruoren.jobimportexport.engine.resolver;

import com.siruoren.jobimportexport.engine.model.NodeType;
import com.siruoren.jobimportexport.engine.model.TreeNode;

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
}
