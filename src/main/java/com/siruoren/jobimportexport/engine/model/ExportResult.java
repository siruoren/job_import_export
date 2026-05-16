package com.siruoren.jobimportexport.engine.model;

public class ExportResult {
    public String jobPath;
    public String fullPath;
    public String status;
    public String message;
    public boolean success;
    public boolean skipped;

    public ExportResult(String jobPath, String fullPath, String status, String message) {
        this.jobPath = jobPath;
        this.fullPath = fullPath;
        this.status = status;
        this.message = message;
        this.success = "EXPORTED".equals(status);
        this.skipped = "SKIPPED".equals(status);
    }
}