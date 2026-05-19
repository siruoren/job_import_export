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
    public Status statusEnum;
    public String message;
    public List<String> missingPlugins = new ArrayList<>();
    public String zipPath;
    public String sourcePath;
    public String displayPath;
    public String fullPath;
    public String blockedBy;
    public String reason;
    public boolean isFolder;
    public boolean isJob;
    
    public void setStatusEnum(Status statusEnum) {
        this.statusEnum = statusEnum;
        if (statusEnum != null) {
            this.status = StatusUtil.getLocalizedStatus(statusEnum);
        }
    }
    
    public void setStatus(String statusCode) {
        this.status = StatusUtil.getLocalizedStatus(statusCode);
        try {
            this.statusEnum = Status.valueOf(statusCode);
        } catch (Exception e) {
            this.statusEnum = null;
        }
    }
    
    public void setStatusEnumAndMessage(Status statusEnum, String message) {
        setStatusEnum(statusEnum);
        this.message = message;
    }

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
