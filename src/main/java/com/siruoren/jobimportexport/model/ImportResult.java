package com.siruoren.jobimportexport.model;

import java.util.ArrayList;
import java.util.List;

public class ImportResult {

    public String jobName;
    public String folderPath;
    public String displayName;
    public String finalName;
    public String finalDisplayName;
    public String zipPath;
    public String fullPath;
    public ImportStatus status;
    public String message;
    public boolean success;
    public boolean skipped;
    public boolean renamed;
    public List<String> missingPlugins = new ArrayList<>();
    public String blockedBy;
    public String reason;

    public ImportResult(String jobName) {
        this.jobName = jobName;
        this.folderPath = "";
        this.displayName = jobName;
        this.finalName = jobName;
        this.finalDisplayName = jobName;
    }

    public ImportResult(String jobName, String folderPath) {
        this.jobName = jobName;
        this.folderPath = folderPath != null ? folderPath : "";
        this.displayName = buildDisplayName(this.folderPath, jobName);
        this.finalName = jobName;
        this.finalDisplayName = this.displayName;
    }

    private static String buildDisplayName(String folderPath, String jobName) {
        if (folderPath == null || folderPath.isEmpty()) {
            return jobName;
        }
        return folderPath + "/" + jobName;
    }

    public static ImportResult ok(String jobName, String folderPath) {
        ImportResult r = new ImportResult(jobName, folderPath);
        r.status = ImportStatus.OK;
        r.success = true;
        r.message = "可以导入";
        r.fullPath = buildDisplayName(folderPath, jobName);
        return r;
    }

    public static ImportResult skipExists(String jobName, String folderPath) {
        ImportResult r = new ImportResult(jobName, folderPath);
        r.status = ImportStatus.SKIP_EXISTS;
        r.skipped = true;
        r.message = "任务已存在，已跳过";
        return r;
    }

    public static ImportResult skipFolderMissing(String jobName, String folderPath) {
        ImportResult r = new ImportResult(jobName, folderPath);
        r.status = ImportStatus.SKIP_FOLDER_MISSING;
        r.skipped = true;
        r.message = "目录不存在：" + folderPath;
        return r;
    }

    public static ImportResult error(String jobName, String folderPath, String message) {
        ImportResult r = new ImportResult(jobName, folderPath);
        r.status = ImportStatus.ERROR;
        r.message = message;
        return r;
    }

    public static ImportResult errorInvalidName(String jobName, String folderPath, String detail) {
        ImportResult r = new ImportResult(jobName, folderPath);
        r.status = ImportStatus.ERROR_INVALID_NAME;
        r.message = "任务名称不合法：" + detail;
        return r;
    }

    public static ImportResult errorPlugin(String jobName, String folderPath, List<String> missingPlugins) {
        ImportResult r = new ImportResult(jobName, folderPath);
        r.status = ImportStatus.ERROR_PLUGIN;
        r.missingPlugins = missingPlugins;
        r.message = "缺少插件依赖：" + String.join(", ", missingPlugins);
        return r;
    }

    public static ImportResult conflict(String jobName, String folderPath, String message) {
        ImportResult r = new ImportResult(jobName, folderPath);
        r.status = ImportStatus.CONFLICT;
        r.message = message;
        return r;
    }

    public static ImportResult blocked(String jobName, String folderPath, String blockedBy) {
        ImportResult r = new ImportResult(jobName, folderPath);
        r.status = ImportStatus.BLOCKED;
        r.blockedBy = blockedBy;
        r.reason = "parent folder mismatch";
        r.message = "上游冲突阻断，后续路径禁止创建";
        return r;
    }

    public static ImportResult overwrite(String jobName, String folderPath) {
        ImportResult r = new ImportResult(jobName, folderPath);
        r.status = ImportStatus.OVERWRITE;
        r.message = "将覆盖已存在的任务";
        return r;
    }

    public static ImportResult rename(String jobName, String folderPath, String newName) {
        ImportResult r = new ImportResult(jobName, folderPath);
        r.finalName = newName;
        r.finalDisplayName = buildDisplayName(folderPath, newName);
        r.renamed = true;
        r.status = ImportStatus.RENAME;
        r.message = "任务已存在，将重命名为：" + newName;
        return r;
    }

    public void updateFullPath() {
        this.fullPath = buildDisplayName(this.folderPath, this.finalName);
    }
}