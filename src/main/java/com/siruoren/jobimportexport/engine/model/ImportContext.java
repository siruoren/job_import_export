package com.siruoren.jobimportexport.engine.model;

import hudson.model.ItemGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 导入上下文，封装导入过程中的配置参数和运行时状态。
 * <p>
 * 字段保持包级可访问以兼容现有代码，但推荐使用 getter 方法访问。
 * 新代码应使用 Builder 模式创建实例，Builder 中包含参数校验。
 */
public class ImportContext {

    // ==================== 运行时状态 ====================
    public Map<String, String> renameMap = new HashMap<>();
    public Set<String> createdFolders = new HashSet<>();
    public Set<String> createdJobs = new HashSet<>();
    public Set<String> virtualFolders = new HashSet<>();
    public List<String> warnings = new ArrayList<>();
    public List<ImportResult> rootConfigResults = new ArrayList<>();

    // 类型冲突检查字段
    public boolean blocked = false;
    public String blockedReason;
    public Map<String, NodeType> typeMap = new HashMap<>();
    public Set<String> blockedPaths = new HashSet<>();

    // 父任务类型错误路径集合
    public Set<String> parentTypeErrors = new HashSet<>();

    // 父任务权限不足路径集合
    public Set<String> parentPermissionErrors = new HashSet<>();

    // ==================== 配置参数 ====================
    public boolean overwrite;
    public boolean dryRun;
    public boolean autoRename;

    public ItemGroup targetGroup;
    public String basePath;

    public boolean applyRootConfigToCurrentFolder;
    public hudson.model.Item currentFolderItem;

    public ImportContext() {
    }

    public ImportContext(boolean dryRun, boolean overwrite, boolean autoRename, ItemGroup targetGroup) {
        this.dryRun = dryRun;
        this.overwrite = overwrite;
        this.autoRename = autoRename;
        this.targetGroup = targetGroup;
        if (targetGroup instanceof hudson.model.AbstractItem) {
            this.basePath = ((hudson.model.AbstractItem) targetGroup).getFullName();
        } else {
            this.basePath = "";
        }
    }

    // ==================== Getter 方法（推荐使用） ====================

    public Map<String, String> getRenameMap() {
        return renameMap;
    }

    public Set<String> getCreatedFolders() {
        return createdFolders;
    }

    public Set<String> getCreatedJobs() {
        return createdJobs;
    }

    public Set<String> getVirtualFolders() {
        return virtualFolders;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public List<ImportResult> getRootConfigResults() {
        return rootConfigResults;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public String getBlockedReason() {
        return blockedReason;
    }

    public Map<String, NodeType> getTypeMap() {
        return typeMap;
    }

    public Set<String> getBlockedPaths() {
        return blockedPaths;
    }

    public Set<String> getParentTypeErrors() {
        return parentTypeErrors;
    }

    public Set<String> getParentPermissionErrors() {
        return parentPermissionErrors;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public boolean isAutoRename() {
        return autoRename;
    }

    public ItemGroup getTargetGroup() {
        return targetGroup;
    }

    public String getBasePath() {
        return basePath;
    }

    public boolean isApplyRootConfigToCurrentFolder() {
        return applyRootConfigToCurrentFolder;
    }

    public hudson.model.Item getCurrentFolderItem() {
        return currentFolderItem;
    }

    // ==================== 业务方法 ====================

    public void addWarning(String warning) {
        this.warnings.add(warning);
    }

    public void block(String reason) {
        this.blocked = true;
        this.blockedReason = reason;
    }

    public void reset() {
        this.blocked = false;
        this.blockedReason = null;
        this.typeMap.clear();
        this.blockedPaths.clear();
    }

    public boolean isPathBlocked(String path) {
        if (blockedPaths.contains(path)) {
            return true;
        }
        for (String blockedPath : blockedPaths) {
            if (path.startsWith(blockedPath + "/") || path.equals(blockedPath)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasParentTypeError(String path) {
        for (String parentError : parentTypeErrors) {
            if (path.startsWith(parentError + "/")) {
                return true;
            }
        }
        return false;
    }

    public String getParentTypeErrorPath(String path) {
        for (String parentError : parentTypeErrors) {
            if (path.startsWith(parentError + "/")) {
                return parentError;
            }
        }
        return null;
    }

    public boolean hasParentPermissionError(String path) {
        for (String parentError : parentPermissionErrors) {
            if (path.startsWith(parentError + "/") || path.equals(parentError)) {
                return true;
            }
        }
        return false;
    }

    public String getParentPermissionErrorPath(String path) {
        for (String parentError : parentPermissionErrors) {
            if (path.startsWith(parentError + "/") || path.equals(parentError)) {
                return parentError;
            }
        }
        return null;
    }

    // ==================== Builder ====================

    /**
     * Builder 模式创建 ImportContext，包含参数校验。
     * 不允许 overwrite 和 autoRename 同时为 true。
     */
    public static class Builder {
        private boolean overwrite;
        private boolean dryRun;
        private boolean autoRename;
        private ItemGroup targetGroup;
        private boolean applyRootConfigToCurrentFolder;
        private hudson.model.Item currentFolderItem;

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public Builder overwrite(boolean overwrite) {
            this.overwrite = overwrite;
            return this;
        }

        public Builder autoRename(boolean autoRename) {
            this.autoRename = autoRename;
            return this;
        }

        public Builder targetGroup(ItemGroup targetGroup) {
            this.targetGroup = targetGroup;
            return this;
        }

        public Builder applyRootConfigToCurrentFolder(boolean apply) {
            this.applyRootConfigToCurrentFolder = apply;
            return this;
        }

        public Builder currentFolderItem(hudson.model.Item item) {
            this.currentFolderItem = item;
            return this;
        }

        /**
         * 构建并校验 ImportContext。
         *
         * @throws IllegalStateException 如果 overwrite 和 autoRename 同时为 true
         */
        public ImportContext build() {
            if (overwrite && autoRename) {
                throw new IllegalStateException("overwrite and autoRename cannot both be true");
            }
            return new ImportContext(dryRun, overwrite, autoRename, targetGroup);
        }
    }
}
