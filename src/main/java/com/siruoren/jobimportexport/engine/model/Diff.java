package com.siruoren.jobimportexport.engine.model;

public class Diff {
    public String path;
    public Action action;
    public String message;

    public Diff() {
    }

    public Diff(String path, Action action, String message) {
        this.path = path;
        this.action = action;
        this.message = message;
    }
}
