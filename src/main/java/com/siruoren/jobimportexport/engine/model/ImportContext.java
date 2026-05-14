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
    
    // 类型冲突检查字段
    public boolean blocked = false;
    public String blockedReason;
    public Map<String, NodeType> typeMap = new HashMap<>();
    public Set<String> blockedPaths = new HashSet<>();
    
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
}
