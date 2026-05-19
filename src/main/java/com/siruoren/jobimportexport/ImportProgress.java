package com.siruoren.jobimportexport;

import com.siruoren.jobimportexport.engine.model.ImportResult;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ImportProgress implements Serializable {
    private static final long serialVersionUID = 1L;

    private volatile String batchId;
    private volatile String currentJob;
    private volatile int currentJobIndex;
    private volatile int totalJobs;
    private volatile int overallProgress;
    private volatile String status;
    private volatile String message;

    private volatile String resultMessage;
    private volatile int successCount;
    private volatile int failCount;
    private volatile int skipCount;
    private volatile List<ImportResult> details = new ArrayList<>();
    private volatile boolean dryRun;
    private volatile String redirect;
    private volatile boolean resultReady = false;

    public ImportProgress(String batchId, int totalJobs) {
        this.batchId = batchId;
        this.totalJobs = totalJobs;
        this.currentJobIndex = 0;
        this.overallProgress = 0;
        this.status = "STARTED";
    }

    public synchronized void updateJob(String jobName, int index, String status, String message) {
        this.currentJob = jobName;
        this.currentJobIndex = index;
        this.status = status;
        this.message = message;
        this.overallProgress = totalJobs > 0 ? (int) ((index * 100.0) / totalJobs) : 0;
    }

    public synchronized void complete() {
        this.status = "DONE";
        this.overallProgress = 100;
        this.message = Messages.ImportProgress_importComplete();
    }

    public synchronized void error(String errorMessage) {
        this.status = "ERROR";
        this.message = errorMessage;
    }

    public synchronized void setResult(String message, int successCount, int failCount, int skipCount, List<ImportResult> details, boolean dryRun, String redirect) {
        this.status = "DONE";
        this.overallProgress = 100;
        this.resultMessage = message;
        this.successCount = successCount;
        this.failCount = failCount;
        this.skipCount = skipCount;
        this.details = details != null ? details : new ArrayList<>();
        this.dryRun = dryRun;
        this.redirect = redirect;
        this.resultReady = true;
    }

    public synchronized void setErrorResult(String errorMessage, int successCount, int failCount, int skipCount, List<ImportResult> details, boolean dryRun, String redirect) {
        this.status = "ERROR";
        this.overallProgress = 100;
        this.resultMessage = errorMessage;
        this.successCount = successCount;
        this.failCount = failCount;
        this.skipCount = skipCount;
        this.details = details != null ? details : new ArrayList<>();
        this.dryRun = dryRun;
        this.redirect = redirect;
        this.resultReady = true;
    }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public String getCurrentJob() { return currentJob; }
    public void setCurrentJob(String currentJob) { this.currentJob = currentJob; }
    public int getCurrentJobIndex() { return currentJobIndex; }
    public void setCurrentJobIndex(int currentJobIndex) { this.currentJobIndex = currentJobIndex; }
    public int getTotalJobs() { return totalJobs; }
    public void setTotalJobs(int totalJobs) { this.totalJobs = totalJobs; }
    public int getOverallProgress() { return overallProgress; }
    public void setOverallProgress(int overallProgress) { this.overallProgress = overallProgress; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getResultMessage() { return resultMessage; }
    public int getSuccessCount() { return successCount; }
    public int getFailCount() { return failCount; }
    public int getSkipCount() { return skipCount; }
    public List<ImportResult> getDetails() { return details; }
    public boolean isDryRun() { return dryRun; }
    public String getRedirect() { return redirect; }
    public boolean isResultReady() { return resultReady; }
}
