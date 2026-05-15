package com.siruoren.jobimportexport.engine;

import org.jvnet.localizer.ResourceBundleHolder;

public class Messages {
    private static final ResourceBundleHolder holder = ResourceBundleHolder.get(Messages.class);

    public static String ExportEngine_exportFailed(Object arg0) {
        return holder.format("ExportEngine.exportFailed", arg0);
    }

    public static String ExportEngine_exportedCurrentConfig() {
        return holder.format("ExportEngine.exportedCurrentConfig");
    }

    public static String ExportEngine_noPermissionSkip() {
        return holder.format("ExportEngine.noPermissionSkip");
    }

    public static String ExportEngine_exportJobFailed(Object arg0) {
        return holder.format("ExportEngine.exportJobFailed", arg0);
    }

    public static String ExportEngine_exportedDirConfig() {
        return holder.format("ExportEngine.exportedDirConfig");
    }

    public static String ExportEngine_exportedDir() {
        return holder.format("ExportEngine.exportedDir");
    }

    public static String ExportEngine_exportedJobConfig() {
        return holder.format("ExportEngine.exportedJobConfig");
    }

    public static String ExportEngine_configNotFound() {
        return holder.format("ExportEngine.configNotFound");
    }

    public static String ExportEngine_summary(Object arg0, Object arg1, Object arg2) {
        return holder.format("ExportEngine.summary", arg0, arg1, arg2);
    }

    public static String ExecutionEngine_willUpdateConfig() {
        return holder.format("ExecutionEngine.willUpdateConfig");
    }

    public static String ExecutionEngine_updatedConfig() {
        return holder.format("ExecutionEngine.updatedConfig");
    }

    public static String ExecutionEngine_updateConfigFailed(Object arg0) {
        return holder.format("ExecutionEngine.updateConfigFailed", arg0);
    }

    public static String ExecutionEngine_dirRenamedTo(Object arg0) {
        return holder.format("ExecutionEngine.dirRenamedTo", arg0);
    }

    public static String ExecutionEngine_typeMismatchCannotOverwrite() {
        return holder.format("ExecutionEngine.typeMismatchCannotOverwrite");
    }

    public static String ExecutionEngine_willOverwriteDirConfig() {
        return holder.format("ExecutionEngine.willOverwriteDirConfig");
    }

    public static String ExecutionEngine_dirExistsSkipped() {
        return holder.format("ExecutionEngine.dirExistsSkipped");
    }

    public static String ExecutionEngine_typeMismatchCannotImportAsDir() {
        return holder.format("ExecutionEngine.typeMismatchCannotImportAsDir");
    }

    public static String ExecutionEngine_dirExistsReuse() {
        return holder.format("ExecutionEngine.dirExistsReuse");
    }

    public static String ExecutionEngine_willCreateDirJob() {
        return holder.format("ExecutionEngine.willCreateDirJob");
    }

    public static String ExecutionEngine_willCreateDir() {
        return holder.format("ExecutionEngine.willCreateDir");
    }

    public static String ExecutionEngine_createdDirJob() {
        return holder.format("ExecutionEngine.createdDirJob");
    }

    public static String ExecutionEngine_createdDir() {
        return holder.format("ExecutionEngine.createdDir");
    }

    public static String ExecutionEngine_createDirFailed(Object arg0) {
        return holder.format("ExecutionEngine.createDirFailed", arg0);
    }

    public static String ExecutionEngine_updatedDirConfig() {
        return holder.format("ExecutionEngine.updatedDirConfig");
    }

    public static String ExecutionEngine_dirExistsReuseConfig() {
        return holder.format("ExecutionEngine.dirExistsReuseConfig");
    }

    public static String ExecutionEngine_updateDirFailed(Object arg0) {
        return holder.format("ExecutionEngine.updateDirFailed", arg0);
    }

    public static String ExecutionEngine_parentTypeErrorSkip(Object arg0) {
        return holder.format("ExecutionEngine.parentTypeErrorSkip", arg0);
    }

    public static String ExecutionEngine_parentPermissionErrorSkip(Object arg0) {
        return holder.format("ExecutionEngine.parentPermissionErrorSkip", arg0);
    }

    public static String ExecutionEngine_parentDirExistsSkipped() {
        return holder.format("ExecutionEngine.parentDirExistsSkipped");
    }

    public static String ExecutionEngine_jobExistsSkipped() {
        return holder.format("ExecutionEngine.jobExistsSkipped");
    }

    public static String ExecutionEngine_jobTypeMismatchCannotOverwrite(Object arg0, Object arg1) {
        return holder.format("ExecutionEngine.jobTypeMismatchCannotOverwrite", arg0, arg1);
    }

    public static String ExecutionEngine_overwriteFailed(Object arg0) {
        return holder.format("ExecutionEngine.overwriteFailed", arg0);
    }

    public static String ExecutionEngine_willOverwriteJobConfig() {
        return holder.format("ExecutionEngine.willOverwriteJobConfig");
    }

    public static String ExecutionEngine_overwroteJobConfig() {
        return holder.format("ExecutionEngine.overwroteJobConfig");
    }

    public static String ExecutionEngine_jobRenamedTo(Object arg0) {
        return holder.format("ExecutionEngine.jobRenamedTo", arg0);
    }

    public static String ExecutionEngine_createFailed(Object arg0) {
        return holder.format("ExecutionEngine.createFailed", arg0);
    }

    public static String ExecutionEngine_willCreateJob() {
        return holder.format("ExecutionEngine.willCreateJob");
    }

    public static String ExecutionEngine_createdJob() {
        return holder.format("ExecutionEngine.createdJob");
    }

    public static String ExecutionEngine_noPermissionUpdateDirConfig() {
        return holder.format("ExecutionEngine.noPermissionUpdateDirConfig");
    }

    public static String ExecutionEngine_noPermissionCreateInDir() {
        return holder.format("ExecutionEngine.noPermissionCreateInDir");
    }

    public static String ExecutionEngine_noPermissionUpdateJobConfig() {
        return holder.format("ExecutionEngine.noPermissionUpdateJobConfig");
    }

    public static String ExecutionEngine_noPermissionCreateJob() {
        return holder.format("ExecutionEngine.noPermissionCreateJob");
    }

    public static String DryRunDiffEngine_dirRenamedTo(Object arg0) {
        return holder.format("DryRunDiffEngine.dirRenamedTo", arg0);
    }

    public static String DryRunDiffEngine_dirExistsReuse() {
        return holder.format("DryRunDiffEngine.dirExistsReuse");
    }

    public static String DryRunDiffEngine_pathExistsNotFolder() {
        return holder.format("DryRunDiffEngine.pathExistsNotFolder");
    }

    public static String DryRunDiffEngine_willCreateFolder() {
        return holder.format("DryRunDiffEngine.willCreateFolder");
    }

    public static String DryRunDiffEngine_jobRenamedTo(Object arg0) {
        return holder.format("DryRunDiffEngine.jobRenamedTo", arg0);
    }

    public static String DryRunDiffEngine_willOverwriteJob() {
        return holder.format("DryRunDiffEngine.willOverwriteJob");
    }

    public static String DryRunDiffEngine_willCreateJob() {
        return holder.format("DryRunDiffEngine.willCreateJob");
    }
}