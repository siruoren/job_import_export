package com.siruoren.jobimportexport.engine.model;

import java.util.ArrayList;
import java.util.List;

public class TreeNode {
    public String name;
    public String fullPath;
    
    public NodeType type;
    public boolean hasConfigXml;
    
    public byte[] configXml;
    
    public List<TreeNode> children = new ArrayList<>();

    public TreeNode() {
        this.type = NodeType.FOLDER;
        this.hasConfigXml = false;
    }

    public TreeNode(String name, String fullPath) {
        this.name = name;
        this.fullPath = fullPath;
        this.type = NodeType.FOLDER;
        this.hasConfigXml = false;
    }
}
