package com.siruoren.jobimportexport.engine.model;

import java.util.ArrayList;
import java.util.List;

public class Node {
    public String name;
    public String fullPath;

    public boolean isFolder;
    public boolean isJob;
    public boolean hasConfigXml;
    public boolean hasAnyConfigInSubtree;

    public byte[] configXml;

    public List<Node> children = new ArrayList<>();

    public Node() {
        this.isFolder = true;
        this.isJob = false;
    }

    public Node(String name, String fullPath) {
        this.name = name;
        this.fullPath = fullPath;
        this.isFolder = true;
        this.isJob = false;
    }
}
