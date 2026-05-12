package com.siruoren.jobimportexport.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ImportContext {

    private boolean blocked = false;
    private String blockedReason;
    private Map<String, NodeType> typeMap = new HashMap<>();
    private Set<String> blockedPaths = new HashSet<>();

    public boolean isBlocked() {
        return blocked;
    }

    public String getBlockedReason() {
        return blockedReason;
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

    public void addBlockedPath(String path) {
        blockedPaths.add(path);
    }

    public boolean isConflict(String path, NodeType newType) {
        NodeType oldType = typeMap.get(path);
        if (oldType == null) {
            typeMap.put(path, newType);
            return false;
        }
        return oldType != newType;
    }

    public enum NodeType {
        UNKNOWN,
        FOLDER,
        JOB
    }
}