package com.siruoren.jobimportexport.engine.model;

import java.util.ArrayList;
import java.util.List;

public class MissingConfigReport {
    public String path;
    public List<String> leafNodesWithoutConfig = new ArrayList<>();

    public MissingConfigReport() {
    }

    public MissingConfigReport(String path) {
        this.path = path;
    }
}
