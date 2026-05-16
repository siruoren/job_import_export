package com.siruoren.jobimportexport;

import com.siruoren.jobimportexport.engine.ImportEngine;
import com.siruoren.jobimportexport.engine.ExportEngine;
import com.siruoren.jobimportexport.engine.model.ImportContext;
import com.siruoren.jobimportexport.engine.model.ImportResult;
import com.siruoren.jobimportexport.engine.model.ExportResult;
import com.siruoren.jobimportexport.engine.model.Status;
import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.*;
import hudson.security.AccessControlled;
import hudson.PluginWrapper;
import hudson.PluginManager;
import jenkins.model.ModifiableTopLevelItemGroup;
import jenkins.model.TransientActionFactory;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.apache.commons.fileupload.FileItem;

import javax.servlet.ServletException;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jvnet.hudson.reactor.ReactorException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;
import java.util.Collections;
import java.util.Collection;
import java.text.SimpleDateFormat;
import java.util.Date;

public class JobImportExportAction implements Action {

    private final AbstractItem item;
    private static final ThreadLocal<Boolean> SKIP_RELOAD = new ThreadLocal<>();

    public JobImportExportAction(AbstractItem item) {
        this.item = item;
    }

    @Override
    public String getIconFileName() {
        return "gear2.png";
    }

    @Override
    public String getDisplayName() {
        return Messages.JobImportExportAction_displayName();
    }

    @Override
    public String getUrlName() {
        return "jobImportExport";
    }

    public AbstractItem getItem() {
        return item;
    }

    public boolean isJob() {
        return item != null;
    }

    public boolean isJobType() {
        return item instanceof Job;
    }

    public boolean isRootLevel() {
        return item.getParent() instanceof Jenkins;
    }

    public boolean hasPermission() {
        if (item instanceof AccessControlled) {
            return ((AccessControlled) item).hasPermission(Item.CONFIGURE);
        }
        return false;
    }

    public boolean isFolder() {
        return item instanceof ItemGroup;
    }

    public boolean canImportJobs() {
        return isFolder();
    }

    public boolean canCreateJob() {
        if (item instanceof AccessControlled) {
            return ((AccessControlled) item).hasPermission(Item.CREATE);
        }
        return false;
    }

    public boolean hasAdminPermission() {
        return Jenkins.get().hasPermission(Jenkins.ADMINISTER);
    }

    public void doExport(StaplerRequest req, StaplerResponse rsp) {
        try {
            item.checkPermission(Item.READ);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String baseName = item.getFullName()
                    .replaceAll("[\\\\/:*?\"<>|]", "_");
            String fileName = baseName + "_" + timestamp + ".xml";

            String encodedFileName = java.net.URLEncoder.encode(
                    fileName,
                    "UTF-8")
                    .replace("+", "%20");

            rsp.setContentType("application/xml;charset=UTF-8");
            rsp.setHeader("Content-Disposition", "attachment; "
                    + "filename=\""
                    + encodedFileName
                    + "\"; "
                    + "filename*=UTF-8''"
                    + encodedFileName);

            Path configFile = Paths.get(item.getRootDir().getAbsolutePath(), "config.xml");
            if (!Files.exists(configFile)) {
                writeJson(rsp, false, Messages.JobImportExportAction_noConfigFile(), null);
                return;
            }

            try (OutputStream out = rsp.getOutputStream()) {
                Files.copy(configFile, out);
            }
        } catch (Exception e) {
            if (!rsp.isCommitted()) {
                try {
                    rsp.reset();
                    rsp.setCharacterEncoding("UTF-8");
                    rsp.setContentType("application/json;charset=UTF-8");
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    rsp.getWriter().write(
                        "{\"success\":false,\"message\":\"" + escapeJson(Messages.JobImportExportAction_exportFailed(msg)) + "\",\"redirect\":null}"
                    );
                } catch (Exception ignored) {
                }
            }
        }
    }

    @RequirePOST
    public void doBatchExport(StaplerRequest req, StaplerResponse rsp) {
        try {
            if (item instanceof AccessControlled) {
                if (!((AccessControlled) item).hasPermission(Item.READ)) {
                    writeJson(rsp, false, Messages.JobImportExportAction_noPermissionRead(), null);
                    return;
                }
            }

            ItemGroup<?> targetGroup;
            if (item instanceof ItemGroup) {
                targetGroup = (ItemGroup<?>) item;
            } else {
                writeJson(rsp, false, Messages.JobImportExportAction_notFolder(), null);
                return;
            }

            ExportEngine exportEngine = new ExportEngine();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean includeCurrentConfig = Boolean.parseBoolean(req.getParameter("includeCurrentConfig"));
            ExportResult summary = exportEngine.exportFromGroup(targetGroup, baos, includeCurrentConfig);
            List<ExportResult> results = exportEngine.getResults();

            byte[] zipData = baos.toByteArray();
            String base64Zip = java.util.Base64.getEncoder().encodeToString(zipData);

            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String zipFileName = item.getName().replaceAll("[\\\\/:*?\"<>|]", "_") + "_" + timestamp + ".zip";

            writeExportJson(rsp, true, summary.message, base64Zip, results, zipFileName);

        } catch (Exception e) {
            if (!rsp.isCommitted()) {
                try {
                    rsp.reset();
                    rsp.setCharacterEncoding("UTF-8");
                    rsp.setContentType("application/json;charset=UTF-8");
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    rsp.getWriter().write(
                        "{\"success\":false,\"message\":\"" + escapeJson(Messages.JobImportExportAction_batchExportFailed(msg)) + "\"}"
                    );
                } catch (Exception ignored) {
                }
            }
        }
    }

    @RequirePOST
    public void doUpdate(StaplerRequest req, StaplerResponse rsp) {
        try {
            if (item instanceof AccessControlled) {
                if (!((AccessControlled) item).hasPermission(Item.CONFIGURE)) {
                    writeJson(rsp, false, Messages.JobImportExportAction_noPermissionUpdate(), null);
                    return;
                }
            }

            FileItem fileItem = req.getFileItem("xmlFile");

            if (fileItem == null || fileItem.getSize() == 0) {
                writeJson(rsp, false, Messages.JobImportExportAction_noFileSelected(), null);
                return;
            }

            byte[] fileContent = readAll(fileItem.getInputStream());

            try {
                try (InputStream safeStream = safeXml(fileContent)) {
                    item.updateByXml(new StreamSource(safeStream));
                }
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("Expecting class")) {
                    writeJson(rsp, false, Messages.JobImportExportAction_typeMismatch(e.getMessage()), null);
                    return;
                } else {
                    writeJson(rsp, false, Messages.JobImportExportAction_xmlParseFailed(e.getMessage()), null);
                    return;
                }
            }

            item.doReload();

            AbstractItem refreshedItem =
                    (AbstractItem) Jenkins.get()
                            .getItemByFullName(item.getFullName());

            String redirectUrl = null;
            if (refreshedItem != null) {
                redirectUrl = Jenkins.get().getRootUrl() + refreshedItem.getUrl();
            }

            writeJson(rsp, true, Messages.JobImportExportAction_updateSuccess(), redirectUrl);
        } catch (Exception e) {
            if (!rsp.isCommitted()) {
                try {
                    rsp.reset();
                    rsp.setCharacterEncoding("UTF-8");
                    rsp.setContentType("application/json;charset=UTF-8");
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    rsp.getWriter().write(
                        "{\"success\":false,\"message\":\"" + escapeJson(Messages.JobImportExportAction_updateFailed(msg)) + "\",\"redirect\":null}"
                    );
                } catch (Exception ignored) {
                }
            }
        }
    }

    @RequirePOST
    public void doBatchImport(StaplerRequest req, StaplerResponse rsp) {
        try {
            req.setCharacterEncoding("UTF-8");
            rsp.setCharacterEncoding("UTF-8");

            FileItem fileItem = req.getFileItem("zipFile");

            if (fileItem == null || fileItem.getSize() == 0) {
                writeJson(rsp, false, Messages.JobImportExportAction_noZipFile(), null);
                return;
            }

            boolean overwrite = Boolean.parseBoolean(req.getParameter("overwrite"));
            boolean rename = Boolean.parseBoolean(req.getParameter("rename"));
            boolean dryRun = Boolean.parseBoolean(req.getParameter("dryRun"));

            ItemGroup<?> target = (item instanceof ItemGroup) ? (ItemGroup<?>) item : item.getParent();

            if (!(target instanceof ModifiableTopLevelItemGroup)) {
                writeJson(rsp, false, Messages.JobImportExportAction_cannotCreateJob(), null);
                return;
            }

            ModifiableTopLevelItemGroup itemGroup = (ModifiableTopLevelItemGroup) target;

            if (itemGroup instanceof AccessControlled) {
                if (!((AccessControlled) itemGroup).hasPermission(Item.CREATE)) {
                    writeJson(rsp, false, Messages.JobImportExportAction_noPermissionCreate(), null);
                    return;
                }
            }

            String batchId = java.util.UUID.randomUUID().toString().substring(0, 8);
            ProgressManager progressManager = ProgressManager.getInstance();
            progressManager.createProgress(batchId, 0);

            Path tempZip = Files.createTempFile("jenkins-import-", ".zip");
            Files.copy(fileItem.getInputStream(), tempZip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            String redirectUrl = (target instanceof Item) ? Jenkins.get().getRootUrl() + ((Item) target).getUrl() : null;
            org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();

            new Thread(() -> {
                org.springframework.security.core.context.SecurityContext securityContext = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
                securityContext.setAuthentication(authentication);
                org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);
                try {
                int successCount = 0;
                int failCount = 0;
                int skipCount = 0;
                List<ImportResult> results = new ArrayList<>();
                boolean importSuccess = false;

                SKIP_RELOAD.set(true);
                try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(tempZip), StandardCharsets.UTF_8)) {
                    ImportContext ctx = new ImportContext(dryRun, overwrite, rename, itemGroup);

                    boolean isSubDirectoryImport = (item instanceof ItemGroup) && !(item.getParent() instanceof Jenkins);
                    ctx.applyRootConfigToCurrentFolder = isSubDirectoryImport;
                    ctx.currentFolderItem = isSubDirectoryImport ? item : null;

                    ImportEngine engine = new ImportEngine();
                    results = engine.importZipWithProgress(zis, ctx, (result, currentIndex, totalCount) -> {
                        if (totalCount > 0) {
                            progressManager.createProgress(batchId, totalCount);
                        }
                        progressManager.updateProgress(batchId, result.finalName, currentIndex, result.status, result.message);
                    });

                    for (ImportResult result : results) {
                        if (result.statusEnum == Status.CREATE_FOLDER || result.statusEnum == Status.CREATE_JOB
                                || result.statusEnum == Status.OVERWRITE_FOLDER || result.statusEnum == Status.OVERWRITE_JOB || result.statusEnum == Status.RENAME_FOLDER || result.statusEnum == Status.RENAME_JOB || result.statusEnum == Status.UPDATE_CONFIG) {
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
                    SKIP_RELOAD.remove();
                    try { Files.deleteIfExists(tempZip); } catch (Exception ignored) {}
                }

                if (importSuccess) {
                    if (!dryRun) {
                        safeReload();
                    }

                    String message = dryRun ? Messages.JobImportExportAction_previewComplete() : Messages.JobImportExportAction_batchImportComplete();
                    progressManager.setResult(batchId, message, successCount, failCount, skipCount, results, dryRun, redirectUrl);
                }

                try { Thread.sleep(30000); } catch (InterruptedException ignored) {}
                progressManager.removeProgress(batchId);
                } finally {
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            }).start();

            rsp.setCharacterEncoding("UTF-8");
            rsp.setContentType("application/json;charset=UTF-8");
            rsp.getWriter().write("{\"success\":true,\"batchId\":\"" + batchId + "\",\"async\":true}");

        } catch (Exception e) {
            if (!rsp.isCommitted()) {
                try {
                    rsp.reset();
                    rsp.setCharacterEncoding("UTF-8");
                    rsp.setContentType("application/json;charset=UTF-8");
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    rsp.getWriter().write(
                        "{\"success\":false,\"message\":\"" + escapeJson(Messages.JobImportExportAction_batchImportFailed(msg)) + "\"}"
                    );
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void writeJson(
            StaplerResponse rsp,
            boolean success,
            String message,
            String redirect) throws IOException {

        rsp.setCharacterEncoding("UTF-8");
        rsp.setContentType("application/json;charset=UTF-8");

        String json = "{"
                + "\"success\":" + success + ","
                + "\"message\":\"" + escapeJson(message) + "\","
                + "\"redirect\":"
                + (redirect == null
                    ? "null"
                    : "\"" + escapeJson(redirect) + "\"")
                + "}";

        rsp.getWriter().write(json);
    }

    public void doProgress(StaplerRequest req, StaplerResponse rsp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        rsp.setContentType("application/json;charset=UTF-8");
        rsp.setCharacterEncoding("UTF-8");

        String batchId = req.getParameter("batchId");
        if (batchId == null || batchId.isEmpty()) {
            rsp.getWriter().write("{\"status\":\"NOT_FOUND\"}");
            return;
        }

        ProgressManager progressManager = ProgressManager.getInstance();
        ImportProgress progress = progressManager.getProgress(batchId);

        if (progress == null) {
            rsp.getWriter().write("{\"status\":\"NOT_FOUND\"}");
            return;
        }

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"batchId\":\"").append(escapeJson(progress.getBatchId())).append("\",");
        json.append("\"currentJob\":\"").append(escapeJson(progress.getCurrentJob() != null ? progress.getCurrentJob() : "")).append("\",");
        json.append("\"currentJobIndex\":").append(progress.getCurrentJobIndex()).append(",");
        json.append("\"totalJobs\":").append(progress.getTotalJobs()).append(",");
        json.append("\"overallProgress\":").append(progress.getOverallProgress()).append(",");
        json.append("\"status\":\"").append(escapeJson(progress.getStatus())).append("\",");
        json.append("\"message\":\"").append(escapeJson(progress.getMessage() != null ? progress.getMessage() : "")).append("\"");

        if (progress.isResultReady()) {
            json.append(",\"resultReady\":true");
            json.append(",\"resultMessage\":\"").append(escapeJson(progress.getResultMessage() != null ? progress.getResultMessage() : "")).append("\"");
            json.append(",\"successCount\":").append(progress.getSuccessCount());
            json.append(",\"failCount\":").append(progress.getFailCount());
            json.append(",\"skipCount\":").append(progress.getSkipCount());
            json.append(",\"dryRun\":").append(progress.isDryRun());
            json.append(",\"redirect\":\"").append(escapeJson(progress.getRedirect() != null ? progress.getRedirect() : "")).append("\"");
            json.append(",\"details\":[");
            List<ImportResult> details = progress.getDetails();
            for (int i = 0; i < details.size(); i++) {
                ImportResult r = details.get(i);
                if (i > 0) json.append(",");
                json.append("{");
                json.append("\"jobPath\":\"").append(escapeJson(r.displayPath != null ? r.displayPath : r.jobName)).append("\",");
                json.append("\"finalName\":\"").append(escapeJson(r.finalName)).append("\",");
                json.append("\"fullPath\":\"").append(escapeJson(r.fullPath != null ? r.fullPath : r.finalName)).append("\",");
                json.append("\"status\":\"").append(escapeJson(r.status)).append("\",");
                json.append("\"message\":\"").append(escapeJson(r.message)).append("\"");
                json.append("}");
            }
            json.append("]");
        }

        json.append("}");

        rsp.getWriter().write(json.toString());
    }

    private void writeBatchJson(
            StaplerResponse rsp,
            boolean success,
            String message,
            int successCount,
            int failCount,
            int skipCount,
            List<ImportResult> results,
            boolean dryRun,
            String batchId) throws IOException {

        rsp.setCharacterEncoding("UTF-8");
        rsp.setContentType("application/json;charset=UTF-8");

        StringBuilder details = new StringBuilder("[");
        boolean first = true;
        for (ImportResult result : results) {
            if (!first) {
                details.append(",");
            }
            first = false;
            details.append("{\"jobPath\":\"")
                   .append(escapeJson(result.displayPath != null ? result.displayPath : result.jobName))
                   .append("\",\"finalName\":\"")
                   .append(escapeJson(result.finalName))
                   .append("\",\"fullPath\":\"")
                   .append(escapeJson(result.fullPath != null ? result.fullPath : result.finalName))
                   .append("\",\"status\":\"")
                   .append(result.status)
                   .append("\",\"message\":\"")
                   .append(escapeJson(result.message))
                   .append("\"");

            if (!result.missingPlugins.isEmpty()) {
                details.append(",\"missingPlugins\":[");
                boolean firstPlugin = true;
                for (String plugin : result.missingPlugins) {
                    if (!firstPlugin) {
                        details.append(",");
                    }
                    firstPlugin = false;
                    details.append("\"")
                           .append(escapeJson(plugin))
                           .append("\"");
                }
                details.append("]");
            }

            details.append("}");
        }
        details.append("]");

        String json = "{"
                + "\"success\":" + success + ","
                + "\"message\":\"" + escapeJson(message) + "\","
                + "\"dryRun\":" + dryRun + ","
                + "\"total\":" + (successCount + failCount + skipCount) + ","
                + "\"successCount\":" + successCount + ","
                + "\"failCount\":" + failCount + ","
                + "\"skipCount\":" + skipCount + ","
                + "\"batchId\":\"" + escapeJson(batchId != null ? batchId : "") + "\","
                + "\"details\":" + details.toString()
                + "}";

        rsp.getWriter().write(json);
    }

    private void writeExportJson(
            StaplerResponse rsp,
            boolean success,
            String message,
            String zipData,
            List<ExportResult> results,
            String zipFileName) throws IOException {

        rsp.setCharacterEncoding("UTF-8");
        rsp.setContentType("application/json;charset=UTF-8");

        StringBuilder details = new StringBuilder("[");
        if (results != null) {
            boolean first = true;
            for (ExportResult result : results) {
                if (!first) {
                    details.append(",");
                }
                first = false;
                details.append("{\"jobPath\":\"")
                       .append(escapeJson(result.jobPath))
                       .append("\",\"fullPath\":\"")
                       .append(escapeJson(result.fullPath))
                       .append("\",\"status\":\"")
                       .append(result.status)
                       .append("\",\"message\":\"")
                       .append(escapeJson(result.message))
                       .append("\"}");
            }
        }
        details.append("]");

        int exported = 0;
        int skipped = 0;
        int errors = 0;
        if (results != null) {
            for (ExportResult r : results) {
                if ("EXPORTED".equals(r.status)) exported++;
                else if ("SKIPPED".equals(r.status)) skipped++;
                else errors++;
            }
        }

        String json = "{"
                + "\"success\":" + success + ","
                + "\"message\":\"" + escapeJson(message) + "\","
                + "\"total\":" + (exported + skipped + errors) + ","
                + "\"successCount\":" + exported + ","
                + "\"skipCount\":" + skipped + ","
                + "\"failCount\":" + errors + ","
                + "\"zipData\":\"" + (zipData != null ? zipData : "") + "\","
                + "\"zipFileName\":\"" + escapeJson(zipFileName != null ? zipFileName : "jenkins-jobs-export.zip") + "\","
                + "\"details\":" + details.toString()
                + "}";

        rsp.getWriter().write(json);
    }

    private String escapeJson(String s) {
        if (s == null) {
            return "";
        }

        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private String buildFullName(ItemGroup<?> target, String name) {
        if (target == null) {
            return name;
        }

        String parentPath = "";

        if (target instanceof AbstractItem) {
            parentPath = ((AbstractItem) target).getFullName();
        }

        if (parentPath == null || parentPath.trim().isEmpty()) {
            return name;
        }

        return parentPath + "/" + name;
    }

    private static boolean isSpecialFolder(AbstractItem item) {
        String name = item.getClass().getName();
        return name.startsWith("jenkins.branch.") || name.contains("ComputedFolder");
    }

    private String sanitizeJobName(String name) {
        if (name == null) {
            return null;
        }
        name = name
                .replace('\u00A0', ' ')
                .replace('\u3000', ' ')
                .trim();
        return Util.fixEmptyAndTrim(name);
    }

    private void validateJobName(String jobName) {
        if (jobName == null || jobName.trim().isEmpty()) {
            throw new IllegalArgumentException(Messages.JobImportExportAction_jobNameEmpty());
        }

        jobName = jobName.trim().replace('\u3000', ' ');

        for (int i = 0; i < jobName.length(); i++) {
            char c = jobName.charAt(i);

            if (Character.isISOControl(c)) {
                throw new IllegalArgumentException(Messages.JobImportExportAction_jobNameControlChars());
            }
        }

        if (jobName.matches(".*[\\\\/:*?\"<>|].*")) {
            throw new IllegalArgumentException(Messages.JobImportExportAction_jobNameIllegalChars());
        }

        if (jobName.length() > 200) {
            throw new IllegalArgumentException(Messages.JobImportExportAction_jobNameTooLong());
        }
    }

    private byte[] readAll(InputStream is) throws IOException {
        return is.readAllBytes();
    }

    private InputStream safeXml(byte[] bytes) {
        String xml = new String(bytes, StandardCharsets.UTF_8);
        xml = xml.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
    }

    private void safeReload() {
        if (!Boolean.TRUE.equals(SKIP_RELOAD.get())) {
            try {
                Jenkins.get().reload();
            } catch (IOException | InterruptedException | ReactorException e) {
            }
        }
    }

    private List<String> checkMissingPlugins(String xml) {
        List<String> missing = new ArrayList<>();

        PluginManager pluginManager = Jenkins.get().getPluginManager();

        Pattern pattern = Pattern.compile(
                "plugin=\"([^\"]+)\""
        );

        Matcher matcher = pattern.matcher(xml);

        Set<String> checked = new HashSet<>();

        while (matcher.find()) {

            String pluginExpr = matcher.group(1);

            String shortName = pluginExpr;

            int idx = pluginExpr.indexOf('@');

            if (idx > 0) {
                shortName = pluginExpr.substring(0, idx);
            }

            if (checked.contains(shortName)) {
                continue;
            }

            checked.add(shortName);

            PluginWrapper plugin =
                    pluginManager.getPlugin(shortName);

            if (plugin == null) {
                missing.add(shortName);
            }
        }

        return missing;
    }

    @Extension
    public static class Factory extends TransientActionFactory<AbstractItem> {

        @Override
        public Collection<? extends Action> createFor(AbstractItem target) {
            return Collections.singleton(new JobImportExportAction(target));
        }

        @Override
        public Class<AbstractItem> type() {
            return AbstractItem.class;
        }
    }
}