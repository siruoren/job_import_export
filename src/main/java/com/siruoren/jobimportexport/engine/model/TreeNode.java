package com.siruoren.jobimportexport.engine.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class TreeNode {

    public String name;
    public String fullPath;
    public boolean hasConfigXml;
    public NodeType type = NodeType.FOLDER;
    public Map<String, TreeNode> children = new LinkedHashMap<>();
    public byte[] configXml;
    public byte[] rootConfigXml;

    public TreeNode() {
    }

    public TreeNode(String name, String fullPath) {
        this.name = name;
        this.fullPath = fullPath;
    }
}
