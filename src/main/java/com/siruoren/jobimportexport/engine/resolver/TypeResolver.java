package com.siruoren.jobimportexport.engine.resolver;

import com.siruoren.jobimportexport.engine.model.Node;
import com.siruoren.jobimportexport.engine.model.NodeType;

public class TypeResolver {

    public NodeType resolve(Node node) {
        if (node.hasConfigXml) {
            return NodeType.JOB;
        }
        return NodeType.FOLDER;
    }
}
