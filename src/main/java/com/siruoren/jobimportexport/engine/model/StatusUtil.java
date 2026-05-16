package com.siruoren.jobimportexport.engine.model;

import com.siruoren.jobimportexport.engine.Messages;

public class StatusUtil {
    
    private StatusUtil() {
    }
    
    public static String getLocalizedStatus(String statusCode) {
        if (statusCode == null || statusCode.isEmpty()) {
            return statusCode;
        }
        
        try {
            if ("CREATE_FOLDER".equals(statusCode)) {
                return Messages.Status_CREATE_FOLDER();
            } else if ("CREATE_JOB".equals(statusCode)) {
                return Messages.Status_CREATE_JOB();
            } else if ("OVERWRITE_JOB".equals(statusCode)) {
                return Messages.Status_OVERWRITE_JOB();
            } else if ("OVERWRITE_FOLDER".equals(statusCode)) {
                return Messages.Status_OVERWRITE_FOLDER();
            } else if ("SKIP_EXISTS".equals(statusCode)) {
                return Messages.Status_SKIP_EXISTS();
            } else if ("SKIP_EMPTY".equals(statusCode)) {
                return Messages.Status_SKIP_EMPTY();
            } else if ("RENAME_JOB".equals(statusCode)) {
                return Messages.Status_RENAME_JOB();
            } else if ("RENAME_FOLDER".equals(statusCode)) {
                return Messages.Status_RENAME_FOLDER();
            } else if ("UPDATE_CONFIG".equals(statusCode)) {
                return Messages.Status_UPDATE_CONFIG();
            } else if ("REUSE_FOLDER".equals(statusCode)) {
                return Messages.Status_REUSE_FOLDER();
            } else if ("ERROR".equals(statusCode)) {
                return Messages.Status_ERROR();
            } else if ("BLOCKED".equals(statusCode)) {
                return Messages.Status_BLOCKED();
            } else if ("CONFLICT".equals(statusCode)) {
                return Messages.Status_CONFLICT();
            } else if ("OK".equals(statusCode)) {
                return Messages.Status_OK();
            } else if ("EXPORTED".equals(statusCode)) {
                return Messages.Status_EXPORTED();
            } else if ("SKIPPED".equals(statusCode)) {
                return Messages.Status_SKIPPED();
            } else if ("ERROR_INVALID_NAME".equals(statusCode)) {
                return Messages.Status_ERROR_INVALID_NAME();
            } else if ("ERROR_PLUGIN".equals(statusCode)) {
                return Messages.Status_ERROR_PLUGIN();
            } else if ("REUSE".equals(statusCode)) {
                return Messages.Status_REUSE();
            } else if ("SKIP_FOLDER_MISSING".equals(statusCode)) {
                return Messages.Status_SKIP_FOLDER_MISSING();
            }
        } catch (Exception e) {
            // ignore
        }
        return statusCode;
    }
    
    public static String getLocalizedStatus(Status status) {
        if (status == null) {
            return "";
        }
        return getLocalizedStatus(status.name());
    }
    
    public static String getLocalizedAction(Action action) {
        if (action == null) {
            return "";
        }
        return getLocalizedAction(action.name());
    }
    
    public static String getLocalizedAction(String actionCode) {
        if (actionCode == null || actionCode.isEmpty()) {
            return actionCode;
        }
        
        try {
            if ("CREATE_JOB".equals(actionCode)) {
                return Messages.Action_CREATE_JOB();
            } else if ("CREATE_FOLDER".equals(actionCode)) {
                return Messages.Action_CREATE_FOLDER();
            } else if ("SKIP_NO_CONFIG".equals(actionCode)) {
                return Messages.Action_SKIP_NO_CONFIG();
            } else if ("SKIP_EMPTY_TREE".equals(actionCode)) {
                return Messages.Action_SKIP_EMPTY_TREE();
            } else if ("OVERWRITE".equals(actionCode)) {
                return Messages.Action_OVERWRITE();
            } else if ("RENAME".equals(actionCode)) {
                return Messages.Action_RENAME();
            } else if ("REUSE".equals(actionCode)) {
                return Messages.Action_REUSE();
            } else if ("UPDATE_CONFIG".equals(actionCode)) {
                return Messages.Action_UPDATE_CONFIG();
            }
        } catch (Exception e) {
            // ignore
        }
        return actionCode;
    }
}
