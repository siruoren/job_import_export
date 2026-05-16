package com.siruoren.jobimportexport.engine.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ImportContext {
    public Map<String, String> renameMap = new HashMap<>();
    public Set<String> createdFolders = new HashSet<>();
    public Set<String> createdJobs = new HashSet<>();
    public Set<String> virtualFolders = new HashSet<>();
    public List<String> warnings = new ArrayList<>();
    
    public boolean overwrite;
    public boolean dryRun;
    public boolean autoRename;
    
    public hudson.model.ItemGroup targetGroup;
    public String basePath;
    
    public boolean applyRootConfigToCurrentFolder;
    public hudson.model.Item currentFolderItem;
    
    public List<ImportResult> rootConfigResults = new ArrayList<>();
    
    // 类型冲突检查字段
    public boolean blocked = false;
    public String blockedReason;
    public Map<String, NodeType> typeMap = new HashMap<>();
    public Set<String> blockedPaths = new HashSet<>();
    
    // 父任务类型错误路径集合（用于跳过子任务）
    public Set<String> parentTypeErrors = new HashSet<>();
    
    // 父任务权限不足路径集合（用于跳过子任务）
    public Set<String> parentPermissionErrors = new HashSet<>();
    
    public ImportContext() {
    }
    
    public ImportContext(boolean dryRun, boolean overwrite, boolean autoRename, hudson.model.ItemGroup targetGroup) {
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
}
