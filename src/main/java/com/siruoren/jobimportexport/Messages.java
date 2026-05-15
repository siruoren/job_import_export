package com.siruoren.jobimportexport;

import org.jvnet.localizer.ResourceBundleHolder;

public class Messages {
    private static final ResourceBundleHolder holder = ResourceBundleHolder.get(Messages.class);

    public static String JobImportExportAction_displayName() {
        return holder.format("JobImportExportAction.displayName");
    }

    public static String JobImportExportAction_noConfigFile() {
        return holder.format("JobImportExportAction.noConfigFile");
    }

    public static String JobImportExportAction_exportFailed(Object arg0) {
        return holder.format("JobImportExportAction.exportFailed", arg0);
    }

    public static String JobImportExportAction_noPermissionRead() {
        return holder.format("JobImportExportAction.noPermissionRead");
    }

    public static String JobImportExportAction_notFolder() {
        return holder.format("JobImportExportAction.notFolder");
    }

    public static String JobImportExportAction_batchExportFailed(Object arg0) {
        return holder.format("JobImportExportAction.batchExportFailed", arg0);
    }

    public static String JobImportExportAction_noPermissionUpdate() {
        return holder.format("JobImportExportAction.noPermissionUpdate");
    }

    public static String JobImportExportAction_noFileSelected() {
        return holder.format("JobImportExportAction.noFileSelected");
    }

    public static String JobImportExportAction_typeMismatch(Object arg0) {
        return holder.format("JobImportExportAction.typeMismatch", arg0);
    }

    public static String JobImportExportAction_xmlParseFailed(Object arg0) {
        return holder.format("JobImportExportAction.xmlParseFailed", arg0);
    }

    public static String JobImportExportAction_updateSuccess() {
        return holder.format("JobImportExportAction.updateSuccess");
    }

    public static String JobImportExportAction_updateFailed(Object arg0) {
        return holder.format("JobImportExportAction.updateFailed", arg0);
    }

    public static String JobImportExportAction_noZipFile() {
        return holder.format("JobImportExportAction.noZipFile");
    }

    public static String JobImportExportAction_cannotCreateJob() {
        return holder.format("JobImportExportAction.cannotCreateJob");
    }

    public static String JobImportExportAction_noPermissionCreate() {
        return holder.format("JobImportExportAction.noPermissionCreate");
    }

    public static String JobImportExportAction_previewComplete() {
        return holder.format("JobImportExportAction.previewComplete");
    }

    public static String JobImportExportAction_batchImportComplete() {
        return holder.format("JobImportExportAction.batchImportComplete");
    }

    public static String JobImportExportAction_batchImportFailed(Object arg0) {
        return holder.format("JobImportExportAction.batchImportFailed", arg0);
    }

    public static String JobImportExportAction_jobNameEmpty() {
        return holder.format("JobImportExportAction.jobNameEmpty");
    }

    public static String JobImportExportAction_jobNameControlChars() {
        return holder.format("JobImportExportAction.jobNameControlChars");
    }

    public static String JobImportExportAction_jobNameIllegalChars() {
        return holder.format("JobImportExportAction.jobNameIllegalChars");
    }

    public static String JobImportExportAction_jobNameTooLong() {
        return holder.format("JobImportExportAction.jobNameTooLong");
    }

    public static String JobImportExportSidebarLink_displayName() {
        return holder.format("JobImportExportSidebarLink.displayName");
    }

    public static String JobImportExportSidebarLink_noPermissionExportAll() {
        return holder.format("JobImportExportSidebarLink.noPermissionExportAll");
    }

    public static String JobImportExportSidebarLink_jobNotFound(Object arg0) {
        return holder.format("JobImportExportSidebarLink.jobNotFound", arg0);
    }

    public static String JobImportExportSidebarLink_parentBlocked(Object arg0) {
        return holder.format("JobImportExportSidebarLink.parentBlocked", arg0);
    }

    public static String JobImportExportSidebarLink_typeConflictJobAsDir(Object arg0) {
        return holder.format("JobImportExportSidebarLink.typeConflictJobAsDir", arg0);
    }

    public static String JobImportExportSidebarLink_typeConflictFolderAsJob(Object arg0) {
        return holder.format("JobImportExportSidebarLink.typeConflictFolderAsJob", arg0);
    }

    public static String JobImportExportSidebarLink_typeConflict(Object arg0, Object arg1, Object arg2) {
        return holder.format("JobImportExportSidebarLink.typeConflict", arg0, arg1, arg2);
    }

    public static String JobImportExportSidebarLink_jobExists() {
        return holder.format("JobImportExportSidebarLink.jobExists");
    }

    public static String JobImportExportSidebarLink_cannotCreateDirHere(Object arg0) {
        return holder.format("JobImportExportSidebarLink.cannotCreateDirHere", arg0);
    }

    public static String JobImportExportSidebarLink_cannotCreateJobHere(Object arg0) {
        return holder.format("JobImportExportSidebarLink.cannotCreateJobHere", arg0);
    }

    public static String JobImportExportSidebarLink_dirNotFound(Object arg0) {
        return holder.format("JobImportExportSidebarLink.dirNotFound", arg0);
    }

    public static String JobImportExportSidebarLink_jobExistsAt(Object arg0) {
        return holder.format("JobImportExportSidebarLink.jobExistsAt", arg0);
    }

    public static String JobImportExportSidebarLink_missingFolderPlugin(Object arg0) {
        return holder.format("JobImportExportSidebarLink.missingFolderPlugin", arg0);
    }

    public static String JobImportExportSidebarLink_dirNotSupportFolder(Object arg0) {
        return holder.format("JobImportExportSidebarLink.dirNotSupportFolder", arg0);
    }

    public static String JobImportExportSidebarLink_pathNotFolder(Object arg0) {
        return holder.format("JobImportExportSidebarLink.pathNotFolder", arg0);
    }

    public static String JobImportExportSidebarLink_upstreamBlocked() {
        return holder.format("JobImportExportSidebarLink.upstreamBlocked");
    }

    public static String JobImportExportSidebarLink_jobNameEmpty() {
        return holder.format("JobImportExportSidebarLink.jobNameEmpty");
    }

    public static String JobImportExportSidebarLink_jobNameInvalid(Object arg0) {
        return holder.format("JobImportExportSidebarLink.jobNameInvalid", arg0);
    }

    public static String JobImportExportSidebarLink_missingPluginDeps(Object arg0) {
        return holder.format("JobImportExportSidebarLink.missingPluginDeps", arg0);
    }

    public static String JobImportExportSidebarLink_dirNotSupportCreateJob() {
        return holder.format("JobImportExportSidebarLink.dirNotSupportCreateJob");
    }

    public static String JobImportExportSidebarLink_dirExistsReuse() {
        return holder.format("JobImportExportSidebarLink.dirExistsReuse");
    }

    public static String JobImportExportSidebarLink_willOverwriteExisting() {
        return holder.format("JobImportExportSidebarLink.willOverwriteExisting");
    }

    public static String JobImportExportSidebarLink_jobExistsWillRename(Object arg0) {
        return holder.format("JobImportExportSidebarLink.jobExistsWillRename", arg0);
    }

    public static String JobImportExportSidebarLink_jobExistsSkipped() {
        return holder.format("JobImportExportSidebarLink.jobExistsSkipped");
    }

    public static String JobImportExportSidebarLink_canImport() {
        return holder.format("JobImportExportSidebarLink.canImport");
    }

    public static String JobImportExportSidebarLink_createDirFailed(Object arg0, Object arg1) {
        return holder.format("JobImportExportSidebarLink.createDirFailed", arg0, arg1);
    }

    public static String JobImportExportSidebarLink_cannotOverwriteDynamicDir() {
        return holder.format("JobImportExportSidebarLink.cannotOverwriteDynamicDir");
    }

    public static String JobImportExportSidebarLink_dirConfigOverwritten() {
        return holder.format("JobImportExportSidebarLink.dirConfigOverwritten");
    }

    public static String JobImportExportSidebarLink_jobConfigOverwritten() {
        return holder.format("JobImportExportSidebarLink.jobConfigOverwritten");
    }

    public static String JobImportExportSidebarLink_dirTypeNotSupported() {
        return holder.format("JobImportExportSidebarLink.dirTypeNotSupported");
    }

    public static String JobImportExportSidebarLink_missingBatchId() {
        return holder.format("JobImportExportSidebarLink.missingBatchId");
    }

    public static String JobImportExportSidebarLink_noFailedJobsToResume() {
        return holder.format("JobImportExportSidebarLink.noFailedJobsToResume");
    }

    public static String JobImportExportSidebarLink_noPermissionCreate() {
        return holder.format("JobImportExportSidebarLink.noPermissionCreate");
    }

    public static String JobImportExportSidebarLink_resumeImportComplete() {
        return holder.format("JobImportExportSidebarLink.resumeImportComplete");
    }

    public static String JobImportExportSidebarLink_missingPluginParam() {
        return holder.format("JobImportExportSidebarLink.missingPluginParam");
    }

    public static String JobImportExportSidebarLink_pluginInstallStarted() {
        return holder.format("JobImportExportSidebarLink.pluginInstallStarted");
    }

    public static String JobImportExportSidebarLink_pluginInstallFailed() {
        return holder.format("JobImportExportSidebarLink.pluginInstallFailed");
    }

    public static String JobImportExportSidebarLink_importComplete() {
        return holder.format("JobImportExportSidebarLink.importComplete");
    }

    public static String FolderService_missingFolderPlugin(Object arg0) {
        return holder.format("FolderService.missingFolderPlugin", arg0);
    }

    public static String FolderService_dirNotSupportFolder(Object arg0) {
        return holder.format("FolderService.dirNotSupportFolder", arg0);
    }

    public static String FolderService_pathNotFolder(Object arg0) {
        return holder.format("FolderService.pathNotFolder", arg0);
    }

    public static String JobService_dirNotSupportCreateJob() {
        return holder.format("JobService.dirNotSupportCreateJob");
    }

    public static String JobService_deleteJobInterrupted(Object arg0) {
        return holder.format("JobService.deleteJobInterrupted", arg0);
    }

    public static String ImportProgress_importComplete() {
        return holder.format("ImportProgress.importComplete");
    }
}