package com.siruoren.jobimportexport;

import java.io.Serializable;

/**
 * Import progress information for SSE streaming
 */
public class ImportProgress implements Serializable {
    private static final long serialVersionUID = 1L;

    private String batchId;
    private String currentJob;
    private int currentJobIndex;
    private int totalJobs;
    private int overallProgress;
    private String status; // STARTED, PARSING, CHECKING, CREATING, DONE, ERROR
    private String message;

    public ImportProgress(String batchId, int totalJobs) {
        this.batchId = batchId;
        this.totalJobs = totalJobs;
        this.currentJobIndex = 0;
        this.overallProgress = 0;
        this.status = "STARTED";
    }

    public void updateJob(String jobName, int index, String status, String message) {
        this.currentJob = jobName;
        this.currentJobIndex = index;
        this.status = status;
        this.message = message;
        this.overallProgress = (int) ((index * 100.0) / totalJobs);
    }

    public void complete() {
        this.status = "DONE";
        this.overallProgress = 100;
        this.message = Messages.ImportProgress_importComplete();
    }

    public void error(String errorMessage) {
        this.status = "ERROR";
        this.message = errorMessage;
    }

    // Getters and Setters
    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getCurrentJob() {
        return currentJob;
    }

    public void setCurrentJob(String currentJob) {
        this.currentJob = currentJob;
    }

    public int getCurrentJobIndex() {
        return currentJobIndex;
    }

    public void setCurrentJobIndex(int currentJobIndex) {
        this.currentJobIndex = currentJobIndex;
    }

    public int getTotalJobs() {
        return totalJobs;
    }

    public void setTotalJobs(int totalJobs) {
        this.totalJobs = totalJobs;
    }

    public int getOverallProgress() {
        return overallProgress;
    }

    public void setOverallProgress(int overallProgress) {
        this.overallProgress = overallProgress;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
