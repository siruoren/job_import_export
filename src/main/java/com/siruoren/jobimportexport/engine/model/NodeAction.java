package com.siruoren.jobimportexport.engine.model;

public class NodeAction {
    public String path;
    public Action action;
    public String message;

    public NodeAction() {
    }

    public NodeAction(String path, Action action, String message) {
        this.path = path;
        this.action = action;
        this.message = message;
    }
}
