package com.siruoren.jobimportexport;

import java.io.Serializable;
import java.util.Date;

/**
 * Checkpoint for tracking import progress and enabling rollback/recovery
 */
public class ImportCheckpoint implements Serializable {
    private static final long serialVersionUID = 1L;

    private String batchId;
    private String jobName;
    private String finalName;
    private String folderPath;
    private String fullName;
    private String status; // STARTED, DONE, FAILED, ROLLED_BACK, RECOVERED
    private byte[] xmlBytes;
    private Date timestamp;
    private String errorMessage;

    public ImportCheckpoint(String batchId, String jobName, byte[] xmlBytes) {
        this.batchId = batchId;
        this.jobName = jobName;
        this.xmlBytes = xmlBytes;
        this.status = "STARTED";
        this.timestamp = new Date();
    }

    public void markSuccess(String finalName) {
        this.status = "DONE";
        this.finalName = finalName;
        this.timestamp = new Date();
    }

    public void markFailed(String errorMessage) {
        this.status = "FAILED";
        this.errorMessage = errorMessage;
        this.timestamp = new Date();
    }

    public void markRolledBack() {
        this.status = "ROLLED_BACK";
        this.timestamp = new Date();
    }

    public void markRecovered() {
        this.status = "RECOVERED";
        this.timestamp = new Date();
    }

    // Getters and Setters
    public String getBatchId() {
        return batchId;
    }

    public void setBatchId(String batchId) {
        this.batchId = batchId;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getFinalName() {
        return finalName;
    }

    public void setFinalName(String finalName) {
        this.finalName = finalName;
    }

    public String getFolderPath() {
        return folderPath;
    }

    public void setFolderPath(String folderPath) {
        this.folderPath = folderPath;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public byte[] getXmlBytes() {
        return xmlBytes;
    }

    public void setXmlBytes(byte[] xmlBytes) {
        this.xmlBytes = xmlBytes;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
