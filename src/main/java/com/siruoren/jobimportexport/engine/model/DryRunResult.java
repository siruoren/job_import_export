package com.siruoren.jobimportexport.engine.model;

import java.util.ArrayList;
import java.util.List;

public class DryRunResult {
    public List<NodeAction> folderActions = new ArrayList<>();
    public List<NodeAction> jobActions = new ArrayList<>();

    public void addFolderAction(NodeAction action) {
        folderActions.add(action);
    }

    public void addJobAction(NodeAction action) {
        jobActions.add(action);
    }
}
