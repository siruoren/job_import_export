package com.siruoren.jobimportexport.engine.model;

public enum NodeType {
    FOLDER,              // 普通文件夹
    JOB,                 // Job（带 config.xml）
    FOLDER_WITH_CONFIG   // 有配置的目录（目录本身有 config.xml，且包含子任务）
}
