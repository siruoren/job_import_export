package com.siruoren.jobimportexport;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages import progress for SSE streaming
 */
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
        ImportProgress progress = new ImportProgress(batchId, totalJobs);
        progressMap.put(batchId, progress);
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

    public void removeProgress(String batchId) {
        progressMap.remove(batchId);
    }
}
