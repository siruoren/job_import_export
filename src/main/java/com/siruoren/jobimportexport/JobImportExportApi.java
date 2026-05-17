package com.siruoren.jobimportexport;

import com.siruoren.jobimportexport.engine.ExportEngine;
import com.siruoren.jobimportexport.engine.ImportEngine;
import com.siruoren.jobimportexport.engine.model.ImportContext;
import com.siruoren.jobimportexport.engine.model.ImportResult;
import com.siruoren.jobimportexport.engine.model.ExportResult;
import com.siruoren.jobimportexport.engine.model.Status;
import com.siruoren.jobimportexport.engine.model.LocaleHolder;
import com.siruoren.jobimportexport.util.JsonUtil;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.security.AccessControlled;
import jenkins.model.Jenkins;
import jenkins.model.ModifiableTopLevelItemGroup;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.apache.commons.fileupload.FileItem;

import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipInputStream;

public class JobImportExportApi {

    public Object getIndex() {
        return this;
    }

    @RequirePOST
    public void doExportJob(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setCharacterEncoding("UTF-8");
        rsp.setContentType("application/json;charset=UTF-8");

        try {
            String jobName = req.getParameter("job");
            if (jobName == null || jobName.isEmpty()) {
                writeError(rsp, 400, Messages.JobImportExportApi_missingParam("job"));
                return;
            }

            Jenkins jenkins = Jenkins.get();
            AbstractItem item = jenkins.getItemByFullName(jobName, AbstractItem.class);
            if (item == null) {
                writeError(rsp, 404, Messages.JobImportExportApi_jobNotFound(jobName));
                return;
            }

            item.checkPermission(Item.READ);

            Path configFile = Path.of(item.getRootDir().getAbsolutePath(), "config.xml");
            if (!Files.exists(configFile)) {
                writeError(rsp, 404, Messages.JobImportExportAction_noConfigFile());
                return;
            }

            String xmlContent = new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("job", jobName);
            result.put("fullName", item.getFullName());
            result.put("jobType", item instanceof ItemGroup ? "folder" : "job");
            result.put("jobUrl", Jenkins.get().getRootUrl() + item.getUrl());
            result.put("configXml", xmlContent);
            rsp.getWriter().write(JsonUtil.toJson(result));

        } catch (Exception e) {
            writeError(rsp, 500, Messages.JobImportExportAction_exportFailed(e.getMessage()));
        }
    }

    @RequirePOST
    public void doExportFolder(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setCharacterEncoding("UTF-8");
        rsp.setContentType("application/json;charset=UTF-8");

        try {
            String folderName = req.getParameter("folder");
            if (folderName == null || folderName.isEmpty()) {
                writeError(rsp, 400, Messages.JobImportExportApi_missingParam("folder"));
                return;
            }

            Jenkins jenkins = Jenkins.get();
            AbstractItem item = jenkins.getItemByFullName(folderName, AbstractItem.class);
            if (item == null) {
                writeError(rsp, 404, Messages.JobImportExportApi_folderNotFound(folderName));
                return;
            }

            if (!(item instanceof ItemGroup)) {
                writeError(rsp, 400, Messages.JobImportExportAction_notFolder());
                return;
            }

            item.checkPermission(Item.READ);

            boolean includeCurrentConfig = Boolean.parseBoolean(req.getParameter("includeCurrentConfig"));

            ExportEngine exportEngine = new ExportEngine();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ExportResult summary = exportEngine.exportFromGroup((ItemGroup<?>) item, baos, includeCurrentConfig);
            List<ExportResult> results = exportEngine.getResults();

            byte[] zipData = baos.toByteArray();
            String base64Zip = java.util.Base64.getEncoder().encodeToString(zipData);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String zipFileName = item.getName().replaceAll("[\\\\/:*?\"<>|]", "_") + "_" + timestamp + ".zip";

            writeExportResult(rsp, true, summary.message, base64Zip, results, zipFileName, item.getFullName(), includeCurrentConfig);

        } catch (Exception e) {
            writeError(rsp, 500, Messages.JobImportExportAction_batchExportFailed(e.getMessage()));
        }
    }

    @RequirePOST
    public void doExportAll(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setCharacterEncoding("UTF-8");
        rsp.setContentType("application/json;charset=UTF-8");

        try {
            Jenkins jenkins = Jenkins.get();
            jenkins.checkPermission(Jenkins.ADMINISTER);

            ExportEngine exportEngine = new ExportEngine();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ExportResult summary = exportEngine.exportAll(baos);
            List<ExportResult> results = exportEngine.getResults();

            byte[] zipData = baos.toByteArray();
            String base64Zip = java.util.Base64.getEncoder().encodeToString(zipData);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String zipFileName = "jenkins-all-jobs_" + timestamp + ".zip";

            writeExportResult(rsp, true, summary.message, base64Zip, results, zipFileName, "root", false);

        } catch (Exception e) {
            writeError(rsp, 500, Messages.JobImportExportAction_exportFailed(e.getMessage()));
        }
    }

    @RequirePOST
    public void doImport(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setCharacterEncoding("UTF-8");
        rsp.setContentType("application/json;charset=UTF-8");

        try {
            req.setCharacterEncoding("UTF-8");

            FileItem fileItem = req.getFileItem("zipFile");
            if (fileItem == null || fileItem.getSize() == 0) {
                writeError(rsp, 400, Messages.JobImportExportAction_noZipFile());
                return;
            }

            boolean overwrite = Boolean.parseBoolean(req.getParameter("overwrite"));
            boolean rename = Boolean.parseBoolean(req.getParameter("rename"));
            boolean dryRun = Boolean.parseBoolean(req.getParameter("dryRun"));
            String targetFolder = req.getParameter("targetFolder");

            ItemGroup<?> target;
            if (targetFolder != null && !targetFolder.isEmpty()) {
                AbstractItem folderItem = Jenkins.get().getItemByFullName(targetFolder, AbstractItem.class);
                if (folderItem == null) {
                    writeError(rsp, 404, Messages.JobImportExportApi_folderNotFound(targetFolder));
                    return;
                }
                if (!(folderItem instanceof ItemGroup)) {
                    writeError(rsp, 400, Messages.JobImportExportAction_notFolder());
                    return;
                }
                folderItem.checkPermission(Item.CREATE);
                target = (ItemGroup<?>) folderItem;
            } else {
                Jenkins jenkins = Jenkins.get();
                jenkins.checkPermission(Item.CREATE);
                target = jenkins;
            }

            if (!(target instanceof ModifiableTopLevelItemGroup)) {
                writeError(rsp, 400, Messages.JobImportExportAction_cannotCreateJob());
                return;
            }

            String batchId = java.util.UUID.randomUUID().toString().substring(0, 8);
            ProgressManager progressManager = ProgressManager.getInstance();
            progressManager.createProgress(batchId, 0);

            Path tempZip = Files.createTempFile("jenkins-api-import-", ".zip");
            Files.copy(fileItem.getInputStream(), tempZip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            String redirectUrl = (target instanceof Item) ? Jenkins.get().getRootUrl() + ((Item) target).getUrl() : null;
            org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            Locale capturedLocale = req.getLocale();

            ItemGroup<?> finalTarget = target;
            ImportExecutor executor = ImportExecutor.getInstance();
            boolean accepted = executor.submitTask(() -> {
                org.springframework.security.core.context.SecurityContext securityContext = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                securityContext.setAuthentication(authentication);
                org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);
                LocaleHolder.setLocale(capturedLocale);
                try {
                    int successCount = 0;
                    int failCount = 0;
                    int skipCount = 0;
                    List<ImportResult> results = new ArrayList<>();
                    boolean importSuccess = false;

                    try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(tempZip), StandardCharsets.UTF_8)) {
                        ImportContext ctx = new ImportContext(dryRun, overwrite, rename, (ModifiableTopLevelItemGroup) finalTarget);

                        boolean isSubDirectoryImport = (finalTarget instanceof AbstractItem) && !(((AbstractItem) finalTarget).getParent() instanceof Jenkins);
                        ctx.applyRootConfigToCurrentFolder = isSubDirectoryImport;
                        ctx.currentFolderItem = isSubDirectoryImport ? (Item) finalTarget : null;

                        ImportEngine engine = new ImportEngine();
                        results = engine.importZipWithProgress(zis, ctx, (result, currentIndex, totalCount) -> {
                            if (totalCount > 0) {
                                progressManager.createProgress(batchId, totalCount);
                            }
                            progressManager.updateProgress(batchId, result.fullPath != null ? result.fullPath : result.finalName, currentIndex, result.status, result.message);
                        });

                        for (ImportResult result : results) {
                            if (result.statusEnum == Status.CREATE_FOLDER || result.statusEnum == Status.CREATE_JOB
                                    || result.statusEnum == Status.OVERWRITE_FOLDER || result.statusEnum == Status.OVERWRITE_JOB
                                    || result.statusEnum == Status.RENAME_FOLDER || result.statusEnum == Status.RENAME_JOB
                                    || result.statusEnum == Status.UPDATE_CONFIG) {
                                successCount++;
                            } else if (result.statusEnum == Status.ERROR) {
                                failCount++;
                            } else {
                                skipCount++;
                            }
                        }
                        importSuccess = true;
                    } catch (Exception e) {
                        progressManager.setErrorResult(batchId, e.getMessage(), successCount, failCount, skipCount, results, dryRun, redirectUrl);
                    } finally {
                        try { Files.deleteIfExists(tempZip); } catch (Exception ignored) {}
                    }

                    if (importSuccess) {
                        if (!dryRun) {
                            try { Jenkins.get().reload(); } catch (Exception ignored) {}
                        }
                        String message = dryRun ? Messages.JobImportExportAction_previewComplete() : Messages.JobImportExportAction_batchImportComplete();
                        progressManager.setResult(batchId, message, successCount, failCount, skipCount, results, dryRun, redirectUrl);
                    }
                } finally {
                    LocaleHolder.clear();
                    executor.taskCompleted();
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            });

            if (!accepted) {
                writeError(rsp, 503, Messages.JobImportExportAction_serverBusy());
                return;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("batchId", batchId);
            result.put("async", true);
            result.put("message", Messages.JobImportExportApi_importSubmitted());
            result.put("targetFolder", targetFolder != null ? targetFolder : "");
            result.put("overwrite", overwrite);
            result.put("rename", rename);
            result.put("dryRun", dryRun);
            result.put("progressUrl", Jenkins.get().getRootUrl() + "jobImportExport/api/progress?batchId=" + batchId);
            rsp.getWriter().write(JsonUtil.toJson(result));

        } catch (Exception e) {
            writeError(rsp, 500, Messages.JobImportExportAction_batchImportFailed(e.getMessage()));
        }
    }

    @RequirePOST
    public void doPreview(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setCharacterEncoding("UTF-8");
        rsp.setContentType("application/json;charset=UTF-8");

        try {
            req.setCharacterEncoding("UTF-8");

            FileItem fileItem = req.getFileItem("zipFile");
            if (fileItem == null || fileItem.getSize() == 0) {
                writeError(rsp, 400, Messages.JobImportExportAction_noZipFile());
                return;
            }

            boolean overwrite = Boolean.parseBoolean(req.getParameter("overwrite"));
            boolean rename = Boolean.parseBoolean(req.getParameter("rename"));
            String targetFolder = req.getParameter("targetFolder");

            ItemGroup<?> target;
            if (targetFolder != null && !targetFolder.isEmpty()) {
                AbstractItem folderItem = Jenkins.get().getItemByFullName(targetFolder, AbstractItem.class);
                if (folderItem == null) {
                    writeError(rsp, 404, Messages.JobImportExportApi_folderNotFound(targetFolder));
                    return;
                }
                if (!(folderItem instanceof ItemGroup)) {
                    writeError(rsp, 400, Messages.JobImportExportAction_notFolder());
                    return;
                }
                folderItem.checkPermission(Item.CREATE);
                target = (ItemGroup<?>) folderItem;
            } else {
                Jenkins jenkins = Jenkins.get();
                jenkins.checkPermission(Item.CREATE);
                target = jenkins;
            }

            if (!(target instanceof ModifiableTopLevelItemGroup)) {
                writeError(rsp, 400, Messages.JobImportExportAction_cannotCreateJob());
                return;
            }

            Path tempZip = Files.createTempFile("jenkins-api-preview-", ".zip");
            Files.copy(fileItem.getInputStream(), tempZip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            try {
                ImportContext ctx = new ImportContext(true, overwrite, rename, (ModifiableTopLevelItemGroup) target);
                ImportEngine engine = new ImportEngine();

                try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(tempZip), StandardCharsets.UTF_8)) {
                    List<ImportResult> results = engine.importZip(zis, ctx);

                    int successCount = 0;
                    int failCount = 0;
                    int skipCount = 0;
                    for (ImportResult result : results) {
                        if (result.statusEnum == Status.CREATE_FOLDER || result.statusEnum == Status.CREATE_JOB
                                || result.statusEnum == Status.OVERWRITE_FOLDER || result.statusEnum == Status.OVERWRITE_JOB
                                || result.statusEnum == Status.RENAME_FOLDER || result.statusEnum == Status.RENAME_JOB
                                || result.statusEnum == Status.UPDATE_CONFIG) {
                            successCount++;
                        } else if (result.statusEnum == Status.ERROR) {
                            failCount++;
                        } else {
                            skipCount++;
                        }
                    }

                    Map<String, Object> responseMap = new LinkedHashMap<>();
                    responseMap.put("success", true);
                    responseMap.put("dryRun", true);
                    responseMap.put("message", Messages.JobImportExportAction_previewComplete());
                    responseMap.put("targetFolder", targetFolder != null ? targetFolder : "");
                    responseMap.put("overwrite", overwrite);
                    responseMap.put("rename", rename);
                    responseMap.put("total", results.size());
                    responseMap.put("successCount", successCount);
                    responseMap.put("failCount", failCount);
                    responseMap.put("skipCount", skipCount);
                    responseMap.put("details", buildImportDetails(results));
                    rsp.getWriter().write(JsonUtil.toJson(responseMap));
                }
            } finally {
                Files.deleteIfExists(tempZip);
            }

        } catch (Exception e) {
            writeError(rsp, 500, Messages.JobImportExportAction_batchImportFailed(e.getMessage()));
        }
    }

    @RequirePOST
    public void doUpdateJob(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setCharacterEncoding("UTF-8");
        rsp.setContentType("application/json;charset=UTF-8");

        try {
            String jobName = req.getParameter("job");
            if (jobName == null || jobName.isEmpty()) {
                writeError(rsp, 400, Messages.JobImportExportApi_missingParam("job"));
                return;
            }

            Jenkins jenkins = Jenkins.get();
            AbstractItem item = jenkins.getItemByFullName(jobName, AbstractItem.class);
            if (item == null) {
                writeError(rsp, 404, Messages.JobImportExportApi_jobNotFound(jobName));
                return;
            }

            item.checkPermission(Item.CONFIGURE);

            String configXml = req.getParameter("configXml");
            FileItem fileItem = req.getFileItem("xmlFile");

            byte[] xmlBytes;
            if (fileItem != null && fileItem.getSize() > 0) {
                xmlBytes = readAll(fileItem.getInputStream());
            } else if (configXml != null && !configXml.isEmpty()) {
                xmlBytes = configXml.getBytes(StandardCharsets.UTF_8);
            } else {
                writeError(rsp, 400, Messages.JobImportExportApi_missingConfigXml());
                return;
            }

            try (InputStream safeStream = safeXml(xmlBytes)) {
                item.updateByXml(new StreamSource(safeStream));
            } catch (IOException e) {
                writeError(rsp, 500, Messages.JobImportExportAction_updateFailed(e.getMessage()));
                return;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("message", Messages.JobImportExportAction_updateSuccess());
            result.put("job", jobName);
            result.put("jobType", item instanceof ItemGroup ? "folder" : "job");
            result.put("jobUrl", Jenkins.get().getRootUrl() + item.getUrl());
            result.put("redirect", Jenkins.get().getRootUrl() + item.getUrl());
            rsp.getWriter().write(JsonUtil.toJson(result));

        } catch (Exception e) {
            writeError(rsp, 500, Messages.JobImportExportAction_updateFailed(e.getMessage()));
        }
    }

    public void doProgress(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setCharacterEncoding("UTF-8");
        rsp.setContentType("application/json;charset=UTF-8");

        try {
            String batchId = req.getParameter("batchId");
            if (batchId == null || batchId.isEmpty()) {
                writeError(rsp, 400, Messages.JobImportExportSidebarLink_missingBatchId());
                return;
            }

            ProgressManager progressManager = ProgressManager.getInstance();
            ImportProgress progress = progressManager.getProgress(batchId);

            if (progress == null) {
                writeError(rsp, 404, Messages.JobImportExportApi_progressNotFound(batchId));
                return;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("batchId", batchId);
            result.put("currentJob", progress.getCurrentJob() != null ? progress.getCurrentJob() : "");
            result.put("currentJobIndex", progress.getCurrentJobIndex());
            result.put("totalJobs", progress.getTotalJobs());
            result.put("overallProgress", progress.getOverallProgress());
            result.put("status", progress.getStatus() != null ? progress.getStatus() : "");
            result.put("message", progress.getMessage() != null ? progress.getMessage() : "");

            if (progress.isResultReady()) {
                result.put("resultReady", true);
                result.put("resultMessage", progress.getResultMessage() != null ? progress.getResultMessage() : "");
                result.put("total", progress.getSuccessCount() + progress.getFailCount() + progress.getSkipCount());
                result.put("successCount", progress.getSuccessCount());
                result.put("failCount", progress.getFailCount());
                result.put("skipCount", progress.getSkipCount());
                result.put("dryRun", progress.isDryRun());
                if (progress.getRedirect() != null) {
                    result.put("redirect", progress.getRedirect());
                }
                if (progress.getDetails() != null) {
                    result.put("details", buildImportDetails(progress.getDetails()));
                }
            }

            rsp.getWriter().write(JsonUtil.toJson(result));

        } catch (Exception e) {
            writeError(rsp, 500, e.getMessage());
        }
    }

    public void doList(StaplerRequest req, StaplerResponse rsp) throws IOException {
        rsp.setCharacterEncoding("UTF-8");
        rsp.setContentType("application/json;charset=UTF-8");

        try {
            String folder = req.getParameter("folder");

            ItemGroup<?> target;

            if (folder != null && !folder.isEmpty()) {
                AbstractItem folderItem = Jenkins.get().getItemByFullName(folder, AbstractItem.class);
                if (folderItem == null) {
                    writeError(rsp, 404, Messages.JobImportExportApi_folderNotFound(folder));
                    return;
                }
                if (!(folderItem instanceof ItemGroup)) {
                    writeError(rsp, 400, Messages.JobImportExportAction_notFolder());
                    return;
                }
                folderItem.checkPermission(Item.READ);
                target = (ItemGroup<?>) folderItem;
            } else {
                Jenkins jenkins = Jenkins.get();
                jenkins.checkPermission(Item.READ);
                target = jenkins;
            }

            List<Map<String, Object>> items = new ArrayList<>();
            collectItems(target, items, 0, 10);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("success", true);
            result.put("folder", folder != null ? folder : "root");
            result.put("totalCount", items.size());
            result.put("items", items);
            rsp.getWriter().write(JsonUtil.toJson(result));

        } catch (Exception e) {
            writeError(rsp, 500, e.getMessage());
        }
    }

    private void collectItems(ItemGroup<?> group, List<Map<String, Object>> items, int depth, int maxDepth) {
        if (group == null || depth > maxDepth) return;

        for (Item item : group.getItems()) {
            if (item == null) continue;

            if (item instanceof AccessControlled) {
                if (!((AccessControlled) item).hasPermission(Item.READ)) continue;
            }

            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("name", item.getName());
            itemMap.put("fullName", item.getFullName());
            itemMap.put("url", Jenkins.get().getRootUrl() + item.getUrl());
            itemMap.put("type", item instanceof ItemGroup ? "folder" : "job");
            itemMap.put("className", item.getClass().getSimpleName());

            if (item instanceof ItemGroup) {
                List<Map<String, Object>> children = new ArrayList<>();
                collectItems((ItemGroup<?>) item, children, depth + 1, maxDepth);
                itemMap.put("children", children);
            }

            items.add(itemMap);
        }
    }

    private List<Map<String, Object>> buildImportDetails(List<ImportResult> results) {
        List<Map<String, Object>> details = new ArrayList<>();
        if (results == null) return details;

        for (ImportResult r : results) {
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("jobPath", r.displayPath != null ? r.displayPath : r.jobName);
            detail.put("finalName", r.finalName);
            detail.put("fullPath", r.fullPath != null ? r.fullPath : r.finalName);
            detail.put("status", r.status);
            detail.put("statusCode", r.statusEnum != null ? r.statusEnum.name() : "");
            detail.put("message", r.message);
            if (!r.missingPlugins.isEmpty()) {
                detail.put("missingPlugins", r.missingPlugins);
            }
            details.add(detail);
        }
        return details;
    }

    private void writeExportResult(
            StaplerResponse rsp,
            boolean success,
            String message,
            String base64Zip,
            List<ExportResult> results,
            String zipFileName,
            String sourceFolder,
            boolean includeCurrentConfig) throws IOException {

        int exported = 0;
        int skipped = 0;
        int errors = 0;
        List<Map<String, Object>> detailList = new ArrayList<>();

        if (results != null) {
            for (ExportResult r : results) {
                if ("EXPORTED".equals(r.statusCode)) exported++;
                else if ("SKIPPED".equals(r.statusCode)) skipped++;
                else errors++;

                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("jobPath", r.jobPath);
                detail.put("fullPath", r.fullPath);
                detail.put("status", r.status);
                detail.put("statusCode", r.statusCode);
                detail.put("message", r.message);
                detailList.add(detail);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", success);
        result.put("message", message);
        result.put("sourceFolder", sourceFolder);
        result.put("includeCurrentConfig", includeCurrentConfig);
        result.put("total", exported + skipped + errors);
        result.put("successCount", exported);
        result.put("skipCount", skipped);
        result.put("failCount", errors);
        result.put("zipData", base64Zip);
        result.put("zipFileName", zipFileName);
        result.put("details", detailList);

        rsp.getWriter().write(JsonUtil.toJson(result));
    }

    private void writeError(StaplerResponse rsp, int statusCode, String message) throws IOException {
        rsp.setStatus(statusCode);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("message", message);
        result.put("errorCode", statusCode);
        rsp.getWriter().write(JsonUtil.toJson(result));
    }

    private byte[] readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    private InputStream safeXml(byte[] bytes) {
        String xml = new String(bytes, StandardCharsets.UTF_8);
        xml = xml.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }
}
