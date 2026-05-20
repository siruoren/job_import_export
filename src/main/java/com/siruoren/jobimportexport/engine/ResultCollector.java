package com.siruoren.jobimportexport.engine;

import com.siruoren.jobimportexport.engine.model.ImportResult;
import com.siruoren.jobimportexport.engine.model.Status;
import com.siruoren.jobimportexport.engine.model.StatusUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 导入结果的收集与进度通知。
 * 将结果收集逻辑从 ExecutionEngine 中提取。
 */
public class ResultCollector {

    private final List<ImportResult> results = new ArrayList<>();
    private ProgressCallback progressCallback;
    private int estimatedTotal = 0;

    /** 暂存 jobNodesToCreate 的 key 集合，供 PermissionChecker 使用 */
    private List<String> jobPaths = new ArrayList<>();

    public interface ProgressCallback {
        void onResult(ImportResult result, int currentIndex, int totalCount);
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    public void setEstimatedTotal(int total) {
        this.estimatedTotal = total;
    }

    public int getEstimatedTotal() {
        return estimatedTotal;
    }

    public void addResult(ImportResult result) {
        results.add(result);
        if (progressCallback != null) {
            progressCallback.onResult(result, results.size(), estimatedTotal);
        }
    }

    public void setJobPaths(List<String> paths) {
        this.jobPaths = paths;
    }

    public List<String> getJobPaths() {
        return jobPaths;
    }

    /**
     * 获取所有结果（不可变视图）
     */
    public List<ImportResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    /**
     * 本地化所有结果的状态显示文本
     */
    public void localizeResults() {
        for (ImportResult result : results) {
            if (result.statusEnum != null) {
                result.status = StatusUtil.getLocalizedStatus(result.statusEnum);
            }
        }
    }

    public void clear() {
        results.clear();
        jobPaths.clear();
        estimatedTotal = 0;
    }
}
