package com.siruoren.jobimportexport;

import hudson.util.Secret;
import jenkins.model.Jenkins;
import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages import checkpoints for rollback and recovery
 */
public class CheckpointManager {
    private static final Logger LOGGER = Logger.getLogger(CheckpointManager.class.getName());
    private static final String CHECKPOINT_DIR = "import-checkpoints";
    
    private static CheckpointManager instance;
    private final Map<String, List<ImportCheckpoint>> checkpoints = new ConcurrentHashMap<>();
    private final File checkpointDir;

    private CheckpointManager() {
        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            this.checkpointDir = new File(jenkins.getRootDir(), CHECKPOINT_DIR);
        } else {
            this.checkpointDir = new File("import-checkpoints");
        }
        if (!checkpointDir.exists()) {
            checkpointDir.mkdirs();
        }
        loadCheckpoints();
    }

    public static synchronized CheckpointManager getInstance() {
        if (instance == null) {
            instance = new CheckpointManager();
        }
        return instance;
    }

    public String createBatchId() {
        return UUID.randomUUID().toString();
    }

    public void addCheckpoint(ImportCheckpoint checkpoint) {
        String batchId = checkpoint.getBatchId();
        checkpoints.computeIfAbsent(batchId, k -> new ArrayList<>()).add(checkpoint);
        saveCheckpoint(checkpoint);
    }

    public void updateCheckpoint(ImportCheckpoint checkpoint) {
        List<ImportCheckpoint> batch = checkpoints.get(checkpoint.getBatchId());
        if (batch != null) {
            for (int i = 0; i < batch.size(); i++) {
                if (batch.get(i).getJobName().equals(checkpoint.getJobName())) {
                    batch.set(i, checkpoint);
                    saveCheckpoint(checkpoint);
                    break;
                }
            }
        }
    }

    public List<ImportCheckpoint> getCheckpoints(String batchId) {
        return checkpoints.getOrDefault(batchId, new ArrayList<>());
    }

    public List<ImportCheckpoint> getFailedCheckpoints(String batchId) {
        List<ImportCheckpoint> failed = new ArrayList<>();
        for (ImportCheckpoint cp : getCheckpoints(batchId)) {
            if ("FAILED".equals(cp.getStatus())) {
                failed.add(cp);
            }
        }
        return failed;
    }

    public void rollbackBatch(String batchId) {
        for (ImportCheckpoint cp : getCheckpoints(batchId)) {
            if ("DONE".equals(cp.getStatus()) || "STARTED".equals(cp.getStatus())) {
                rollbackJob(cp.getFinalName() != null ? cp.getFinalName() : cp.getJobName());
                cp.markRolledBack();
                updateCheckpoint(cp);
            }
        }
    }

    public void rollbackJob(String jobName) {
        try {
            hudson.model.Item item = Jenkins.getInstance().getItemByFullName(jobName);
            if (item != null) {
                item.delete();
                LOGGER.info("Rolled back job: " + jobName);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to rollback job: " + jobName, e);
        }
    }

    public void cleanupBatch(String batchId) {
        checkpoints.remove(batchId);
        File batchDir = new File(checkpointDir, batchId);
        if (batchDir.exists()) {
            deleteDirectory(batchDir);
        }
    }

    private void saveCheckpoint(ImportCheckpoint checkpoint) {
        try {
            File batchDir = new File(checkpointDir, checkpoint.getBatchId());
            if (!batchDir.exists()) {
                batchDir.mkdirs();
            }
            File checkpointFile = new File(batchDir, checkpoint.getJobName() + ".checkpoint");
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(checkpointFile))) {
                oos.writeObject(checkpoint);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to save checkpoint: " + checkpoint.getJobName(), e);
        }
    }

    private void loadCheckpoints() {
        if (!checkpointDir.exists()) {
            return;
        }
        for (File batchDir : checkpointDir.listFiles()) {
            if (batchDir.isDirectory()) {
                String batchId = batchDir.getName();
                List<ImportCheckpoint> batch = new ArrayList<>();
                for (File checkpointFile : batchDir.listFiles()) {
                    if (checkpointFile.getName().endsWith(".checkpoint")) {
                        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(checkpointFile))) {
                            ImportCheckpoint cp = (ImportCheckpoint) ois.readObject();
                            batch.add(cp);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Failed to load checkpoint: " + checkpointFile.getName(), e);
                        }
                    }
                }
                checkpoints.put(batchId, batch);
            }
        }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
}
