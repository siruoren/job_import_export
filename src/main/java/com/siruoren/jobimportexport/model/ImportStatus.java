package com.siruoren.jobimportexport.model;

public enum ImportStatus {
    OK,
    SKIP_EXISTS,
    SKIP_FOLDER_MISSING,
    ERROR,
    ERROR_INVALID_NAME,
    ERROR_PLUGIN,
    CONFLICT,
    BLOCKED,
    OVERWRITE,
    RENAME
}