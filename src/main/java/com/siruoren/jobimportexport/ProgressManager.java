package com.siruoren.jobimportexport;

import com.siruoren.jobimportexport.engine.model.ImportResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProgressManager {
    private static final Logger LOGGER = Logger.getLogger(ProgressManager.class.getName());

    private static ProgressManager instance;
    private final Map<String, ImportProgress> progressMap = new ConcurrentHashMap<>();

    private ProgressManager() {
    }

    public static synchronized ProgressManager getInstance() {
        if (instance == null) {
            instance = new ProgressManager();
        }
        return instance;
    }

    public void createProgress(String batchId, int totalJobs) {
        ImportProgress existing = progressMap.get(batchId);
        if (existing != null) {
            existing.setTotalJobs(totalJobs);
        } else {
            ImportProgress progress = new ImportProgress(batchId, totalJobs);
            progressMap.put(batchId, progress);
        }
    }

    public ImportProgress getProgress(String batchId) {
        return progressMap.get(batchId);
    }

    public void updateProgress(String batchId, String jobName, int index, String status, String message) {
        ImportProgress progress = progressMap.get(batchId);
        if (progress != null) {
            progress.updateJob(jobName, index, status, message);
        }
    }

    public void completeProgress(String batchId) {
        ImportProgress progress = progressMap.get(batchId);
        if (progress != null) {
            progress.complete();
        }
    }

    public void errorProgress(String batchId, String errorMessage) {
        ImportProgress progress = progressMap.get(batchId);
        if (progress != null) {
            progress.error(errorMessage);
        }
    }

    public void setResult(String batchId, String message, int successCount, int failCount, int skipCount, List<ImportResult> details, boolean dryRun, String redirect) {
        ImportProgress progress = progressMap.get(batchId);
        if (progress != null) {
            progress.setResult(message, successCount, failCount, skipCount, details, dryRun, redirect);
        }
    }

    public void setErrorResult(String batchId, String errorMessage, int successCount, int failCount, int skipCount, List<ImportResult> details, boolean dryRun, String redirect) {
        ImportProgress progress = progressMap.get(batchId);
        if (progress != null) {
            progress.setErrorResult(errorMessage, successCount, failCount, skipCount, details, dryRun, redirect);
        }
    }

    public void removeProgress(String batchId) {
        progressMap.remove(batchId);
    }
}
