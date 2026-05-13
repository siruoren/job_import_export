package com.siruoren.jobimportexport.engine.model;

import java.util.ArrayList;
import java.util.List;

public class Node {
    public String name;
    public String fullPath;

    public boolean isLeaf;
    public boolean hasConfigXml;

    public byte[] configXml;

    public List<Node> children = new ArrayList<>();

    public Node() {
    }

    public Node(String name, String fullPath) {
        this.name = name;
        this.fullPath = fullPath;
    }
}
