package com.siruoren.jobimportexport.engine.model;

import java.util.ArrayList;
import java.util.List;

public class ImportResult {
    public String jobName;
    public String folderPath;
    public String finalName;
    public boolean success;
    public boolean skipped;
    public boolean renamed;
    public String status;
    public String message;
    public List<String> missingPlugins = new ArrayList<>();
    public String zipPath;
    public String sourcePath;
    public String displayPath;
    public String fullPath;
    public String blockedBy;
    public String reason;

    public ImportResult(String jobName) {
        this.jobName = jobName;
        this.folderPath = "";
        this.finalName = jobName;
        this.missingPlugins = new ArrayList<>();
    }

    public ImportResult(String jobName, String folderPath) {
        this.jobName = jobName;
        this.folderPath = folderPath != null ? folderPath : "";
        this.finalName = jobName;
        this.missingPlugins = new ArrayList<>();
    }
}
