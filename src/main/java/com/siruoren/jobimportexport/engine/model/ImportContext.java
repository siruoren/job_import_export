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
    
    public ImportContext() {
    }
    
    public ImportContext(boolean dryRun, boolean overwrite, boolean autoRename, hudson.model.ItemGroup targetGroup) {
        this.dryRun = dryRun;
        this.overwrite = overwrite;
        this.autoRename = autoRename;
        this.targetGroup = targetGroup;
    }
    
    public void addWarning(String warning) {
        this.warnings.add(warning);
    }
}
