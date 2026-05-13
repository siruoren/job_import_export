package com.siruoren.jobimportexport.engine.model;

import java.util.ArrayList;
import java.util.List;

public class DiffResult {

    public String sourcePath;
    public String targetPath;
    public String status;
    public String message;
    public List<String> missingPlugins = new ArrayList<>();

}