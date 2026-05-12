package com.siruoren.jobimportexport.model;

import java.util.HashSet;
import java.util.Set;

public class VirtualFsState {

    private final Set<String> existingFolders = new HashSet<>();
    private final Set<String> createdFolders = new HashSet<>();

    public boolean existsFolder(String path) {
        return existingFolders.contains(path) || createdFolders.contains(path);
    }

    public void createFolder(String path) {
        createdFolders.add(path);
    }

    public void addExistingFolder(String path) {
        existingFolders.add(path);
    }
}