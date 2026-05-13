package com.siruoren.jobimportexport.engine.scanner;

import com.siruoren.jobimportexport.engine.model.Node;

public class ConfigScanner {

    public boolean computeHasConfig(Node node) {
        boolean self = node.hasConfigXml;
        boolean child = false;

        for (Node c : node.children) {
            child |= computeHasConfig(c);
        }

        node.hasAnyConfigInSubtree = self || child;
        return node.hasAnyConfigInSubtree;
    }

    public boolean shouldSkipSubtree(Node node) {
        return !node.hasAnyConfigInSubtree;
    }
}
