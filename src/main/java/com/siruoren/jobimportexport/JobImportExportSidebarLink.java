package com.siruoren.jobimportexport;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.RootAction;
import hudson.model.TopLevelItem;
import hudson.security.AccessControlled;
import jenkins.model.Jenkins;
import jenkins.model.ModifiableTopLevelItemGroup;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.apache.commons.fileupload.FileItem;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.jvnet.hudson.reactor.ReactorException;
import hudson.PluginWrapper;
import hudson.PluginManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.LinkedHashMap;
import java.util.Locale;
import com.siruoren.jobimportexport.engine.ImportEngine;
import com.siruoren.jobimportexport.engine.ExportEngine;
import com.siruoren.jobimportexport.engine.model.ImportContext;
import com.siruoren.jobimportexport.engine.model.ImportResult;
import com.siruoren.jobimportexport.engine.model.ExportResult;
import com.siruoren.jobimportexport.engine.model.NodeType;
import com.siruoren.jobimportexport.engine.model.Status;
import com.siruoren.jobimportexport.engine.model.LocaleHolder;

@Extension
public class JobImportExportSidebarLink implements RootAction {

    @Override
    public String getIconFileName() {
        return "gear2.png";
    }

    @Override
    public String getDisplayName() {
        return Messages.JobImportExportSidebarLink_displayName();
    }

    @Override
    public String getUrlName() {
        return "jobImportExport";
    }

    public boolean isVisible() {
        Jenkins jenkins = Jenkins.get();
        return jenkins.hasPermission(Item.CREATE) || jenkins.hasPermission(Jenkins.ADMINISTER);
    }

    public Object getIndex() {
        return this;
    }

    public boolean hasPermission() {
        return Jenkins.get().hasPermission(Item.CREATE);
    }

    public boolean hasAdminPermission() {
        return Jenkins.get().hasPermission(Jenkins.ADMINISTER);
    }

    @RequirePOST
    public void doExportAll(StaplerRequest req, StaplerResponse rsp) {
        try {
            Jenkins jenkins = Jenkins.get();
            if (!jenkins.hasPermission(Jenkins.ADMINISTER)) {
                writeJson(rsp, false, Messages.JobImportExportSidebarLink_noPermissionExportAll(), null);
                return;
            }

            ExportEngine exportEngine = new ExportEngine();

            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String fileName = "jenkins-all-jobs_" + timestamp + ".zip";
            String encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");

            rsp.setContentType("application/zip");
            rsp.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);

            ExportResult summary = exportEngine.exportAll(rsp.getOutputStream());
            List<ExportResult> results = exportEngine.getResults();

            rsp.flushBuffer();

        } catch (Exception e) {
            if (!rsp.isCommitted()) {
                try {
                    rsp.reset();
                    rsp.setCharacterEncoding("UTF-8");
                    rsp.setContentType("application/json;charset=UTF-8");
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    rsp.getWriter().write(
                        "{\"success\":false,\"message\":\"" + escapeJson(Messages.JobImportExportAction_exportFailed(msg)) + "\"}"
                    );
                } catch (Exception ignored) {
                }
            }
        }
    }

    @RequirePOST
    public void doExportAllWithResult(StaplerRequest req, StaplerResponse rsp) {
        try {
            Jenkins jenkins = Jenkins.get();
            if (!jenkins.hasPermission(Jenkins.ADMINISTER)) {
                writeExportJson(rsp, false, Messages.JobImportExportSidebarLink_noPermissionExportAll(), null, null, null);
                return;
            }

            ExportEngine exportEngine = new ExportEngine();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ExportResult summary = exportEngine.exportAll(baos);
            List<ExportResult> results = exportEngine.getResults();

            byte[] zipData = baos.toByteArray();
            String base64Zip = java.util.Base64.getEncoder().encodeToString(zipData);

            String exportTimestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            writeExportJson(rsp, true, summary.message, base64Zip, results, "jenkins-all-jobs_" + exportTimestamp + ".zip");

        } catch (Exception e) {
            if (!rsp.isCommitted()) {
                try {
                    rsp.reset();
                    rsp.setCharacterEncoding("UTF-8");
                    rsp.setContentType("application/json;charset=UTF-8");
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    rsp.getWriter().write(
                        "{\"success\":false,\"message\":\"" + escapeJson(Messages.JobImportExportAction_exportFailed(msg)) + "\"}"
                    );
                } catch (Exception ignored) {
                }
            }
        }
    }

    @RequirePOST
    public void doExport(StaplerRequest req, StaplerResponse rsp) {
        try {
            String jobName = req.getParameter("job");
        if (jobName == null || jobName.isEmpty()) {
            writeJson(rsp, false, Messages.JobImportExportAction_jobNameEmpty(), null);
            return;
        }

        Jenkins jenkins = Jenkins.get();
        AbstractItem item = jenkins.getItemByFullName(jobName, AbstractItem.class);
        if (item == null) {
            writeJson(rsp, false, Messages.JobImportExportSidebarLink_jobNotFound(jobName), null);
            return;
        }

        if (item instanceof AccessControlled) {
            if (!((AccessControlled) item).hasPermission(Item.READ)) {
                writeJson(rsp, false, Messages.JobImportExportAction_noPermissionRead(), null);
                return;
            }
        }

        Path configFile = Paths.get(item.getRootDir().getAbsolutePath(), "config.xml");
        if (!Files.exists(configFile)) {
            writeJson(rsp, false, Messages.JobImportExportAction_noConfigFile(), null);
            return;
        }

        String fileName = item.getFullName()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                + ".xml";

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

            Jenkins jenkins = Jenkins.get();

            if (!jenkins.hasPermission(Item.CREATE)) {
                writeJson(rsp, false, Messages.JobImportExportAction_noPermissionCreate(), null);
                return;
            }

            String batchId = java.util.UUID.randomUUID().toString().substring(0, 8);
            ProgressManager progressManager = ProgressManager.getInstance();
            progressManager.createProgress(batchId, 0);

            Path tempZip = Files.createTempFile("jenkins-import-", ".zip");
            Files.copy(fileItem.getInputStream(), tempZip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            Locale capturedLocale = req.getLocale();

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
                        ImportEngine importEngine = new ImportEngine();
                        ImportContext ctx = new ImportContext();
                        ctx.dryRun = dryRun;
                        ctx.overwrite = overwrite;
                        ctx.autoRename = rename;
                        ctx.targetGroup = Jenkins.get();

                        results = importEngine.importZipWithProgress(zis, ctx, (result, currentIndex, totalCount) -> {
                            if (totalCount > 0) {
                                progressManager.createProgress(batchId, totalCount);
                            }
                            progressManager.updateProgress(batchId, result.fullPath != null ? result.fullPath : result.finalName, currentIndex, result.status, result.message);
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
                        progressManager.setErrorResult(batchId, e.getMessage(), successCount, failCount, skipCount, results, dryRun, null);
                    } finally {
                        try { Files.deleteIfExists(tempZip); } catch (Exception ignored) {}
                    }

                    if (importSuccess) {
                        if (!dryRun) {
                            try { Jenkins.get().reload(); } catch (Exception ignored) {}
                        }

                        String message = dryRun ? Messages.JobImportExportAction_previewComplete() : Messages.JobImportExportAction_batchImportComplete();
                        progressManager.setResult(batchId, message, successCount, failCount, skipCount, results, dryRun, null);
                    }
                } finally {
                    LocaleHolder.clear();
                    executor.taskCompleted();
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            });

            if (!accepted) {
                rsp.setCharacterEncoding("UTF-8");
                rsp.setContentType("application/json;charset=UTF-8");
                rsp.getWriter().write("{\"success\":false,\"message\":\"" + Messages.JobImportExportAction_serverBusy() + "\"}");
                return;
            }

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
                   .append(escapeJson(result.status))
                   .append("\",\"statusCode\":\"")
                   .append(escapeJson(result.statusEnum != null ? result.statusEnum.name() : ""))
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
                       .append(escapeJson(result.status))
                       .append("\",\"statusCode\":\"")
                       .append(escapeJson(result.statusCode))
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
                if ("EXPORTED".equals(r.statusCode)) exported++;
                else if ("SKIPPED".equals(r.statusCode)) skipped++;
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

    private boolean containsControlCharacters(byte[] content) {
        if (content == null) {
            return false;
        }
        for (byte b : content) {
            int value = b & 0xFF;
            if (value >= 0 && value <= 31 && value != 9 && value != 10 && value != 13) {
                return true;
            }
        }
        return false;
    }

    private InputStream cleanXml(InputStream is) throws IOException {
        String xml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        
        xml = xml.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        
        return new java.io.ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
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

    private static class ImportNode {
        String name;
        boolean isJob;
        byte[] xml;
        List<ImportNode> children;

        ImportNode(String name) {
            this.name = name;
            this.isJob = false;
            this.xml = null;
            this.children = new ArrayList<>();
        }
    }

    

    private static class VirtualFsState {
        Set<String> existingFolders = new HashSet<>();
        Set<String> createdFolders = new HashSet<>();

        boolean existsFolder(String path) {
            return existingFolders.contains(path) || createdFolders.contains(path);
        }

        void createFolder(String path) {
            createdFolders.add(path);
        }

        void addExistingFolder(String path) {
            existingFolders.add(path);
        }
    }



    private void initializeVirtualFsState(VirtualFsState vfs, ItemGroup<?> baseGroup) {
        if (vfs == null || baseGroup == null) {
            return;
        }

        Jenkins jenkins = Jenkins.get();
        Collection<Item> allItems = jenkins.getAllItems(Item.class);

        for (Item item : allItems) {
            if (item instanceof ItemGroup) {
                String fullName = item.getFullName();
                if (fullName != null && !fullName.isEmpty()) {
                    vfs.addExistingFolder(fullName);
                }
            }
        }
    }

    

    private static class RenameContext {
        Map<String, String> renamedPaths = new HashMap<>();

        void addRename(String oldPath, String newPath) {
            renamedPaths.put(oldPath, newPath);
        }

        String applyRename(String folderPath) {
            if (folderPath == null || folderPath.isEmpty()) {
                return folderPath;
            }

            String resolved = folderPath;
            boolean changed = true;

            while (changed) {
                changed = false;

                List<String> sortedKeys = new ArrayList<>(renamedPaths.keySet());
                sortedKeys.sort((a, b) -> b.length() - a.length());

                for (String oldPath : sortedKeys) {
                    String newPath = renamedPaths.get(oldPath);
                    if (resolved.equals(oldPath)) {
                        resolved = newPath;
                        changed = true;
                        break;
                    }
                    if (resolved.startsWith(oldPath + "/")) {
                        resolved = newPath + resolved.substring(oldPath.length());
                        changed = true;
                        break;
                    }
                }
            }

            return resolved;
        }
    }

    private NodeType detectType(String entryName) {
        if (entryName.endsWith("/config.xml")) {
            return NodeType.JOB;
        }
        return NodeType.FOLDER;
    }

    private boolean isConflict(ImportContext ctx, String path, NodeType newType) {
        NodeType oldType = ctx.typeMap.get(path);

        if (oldType == null) {
            ctx.typeMap.put(path, newType);
            return false;
        }

        return oldType != newType;
    }

    private void importNode(
            ItemGroup<?> base,
            String[] parts,
            int index,
            byte[] xmlBytes,
            boolean dryRun,
            ImportContext ctx,
            List<ImportResult> results,
            String currentPath
    ) throws IOException {

        if (ctx.blocked || ctx.isPathBlocked(currentPath)) {
            ImportResult r = new ImportResult(parts[index]);
            r.setStatusEnum(Status.BLOCKED);
            r.message = Messages.JobImportExportSidebarLink_parentBlocked(ctx.blockedReason);
            results.add(r);
            return;
        }

        String name = parts[index];
        boolean isLast = (index == parts.length - 1);
        String fullPath = currentPath.isEmpty() ? name : currentPath + "/" + name;

        Item item = base.getItem(name);

        NodeType newType = isLast && name.equals("config.xml")
                ? NodeType.JOB
                : NodeType.FOLDER;

        if (isConflict(ctx, fullPath, newType)) {
            ctx.blocked = true;
            ctx.blockedReason = Messages.JobImportExportSidebarLink_typeConflict(fullPath, ctx.typeMap.get(fullPath), newType);
            ctx.blockedPaths.add(fullPath);

            ImportResult r = new ImportResult(name);
            r.setStatusEnum(Status.CONFLICT);
            r.message = ctx.blockedReason;
            results.add(r);
            return;
        }

        if (!isLast && item != null && !(item instanceof ItemGroup)) {
            ctx.blocked = true;
            ctx.blockedReason = Messages.JobImportExportSidebarLink_typeConflictJobAsDir(fullPath);
            ctx.blockedPaths.add(fullPath);

            ImportResult r = new ImportResult(name);
            r.setStatusEnum(Status.CONFLICT);
            r.message = ctx.blockedReason;
            results.add(r);
            return;
        }

        if (isLast && item != null && item instanceof ItemGroup) {
            ctx.blocked = true;
            ctx.blockedReason = Messages.JobImportExportSidebarLink_typeConflictFolderAsJob(fullPath);
            ctx.blockedPaths.add(fullPath);

            ImportResult r = new ImportResult(name);
            r.setStatusEnum(Status.CONFLICT);
            r.message = ctx.blockedReason;
            results.add(r);
            return;
        }

        if (isLast && item != null) {
            ImportResult r = new ImportResult(name);
            r.setStatusEnum(Status.SKIP_EXISTS);
            r.message = Messages.JobImportExportSidebarLink_jobExists();
            results.add(r);
            return;
        }

        if (!isLast) {
            if (item == null) {
                if (!dryRun) {
                    if (!(base instanceof ModifiableTopLevelItemGroup)) {
                        ctx.block(Messages.JobImportExportSidebarLink_cannotCreateDirHere(name));
                        ImportResult r = new ImportResult(name);
                        r.setStatusEnum(Status.ERROR);
                        r.message = ctx.blockedReason;
                        results.add(r);
                        return;
                    }

                    item = ((ModifiableTopLevelItemGroup) base)
                            .createProject(
                                    Jenkins.get().getDescriptorByType(
                                            com.cloudbees.hudson.plugins.folder.Folder.DescriptorImpl.class
                                    ),
                                    name,
                                    false
                            );
                }
            }

            if (item instanceof ItemGroup) {
                importNode((ItemGroup<?>) item,
                        parts,
                        index + 1,
                        xmlBytes,
                        dryRun,
                        ctx,
                        results,
                        fullPath);
            }
            return;
        }

        if (isLast && !dryRun) {
            if (!(base instanceof ModifiableTopLevelItemGroup)) {
                ImportResult r = new ImportResult(name);
                r.setStatusEnum(Status.ERROR);
                r.message = Messages.JobImportExportSidebarLink_cannotCreateJobHere(name);
                results.add(r);
                return;
            }

            String jobName = name.replace(".config.xml", "");
            try (InputStream in = new ByteArrayInputStream(xmlBytes)) {
                ((ModifiableTopLevelItemGroup) base)
                        .createProjectFromXML(jobName, cleanXml(in));
            }
        }

        ImportResult r = new ImportResult(name);
        r.setStatusEnum(Status.OK);
        r.success = true;
        results.add(r);
    }

    private static class JobPathInfo {
        String folderPath;
        String jobName;

        JobPathInfo(String folderPath, String jobName) {
            this.folderPath = folderPath;
            this.jobName = jobName;
        }
    }

    private boolean isCreatableGroup(ItemGroup<?> g) {
        return g instanceof ModifiableTopLevelItemGroup
                && !(g instanceof AbstractItem
                        && g.getClass().getName().contains("ComputedFolder"));
    }

    private boolean checkFolderExists(ItemGroup<?> base, String folderPath, VirtualFsState vfs) {
        ItemGroup<?> current = base;

        if (folderPath == null || folderPath.isEmpty()) {
            return true;
        }

        String[] parts = folderPath.split("/");
        StringBuilder currentPathBuilder = new StringBuilder();

        for (String part : parts) {
            if (currentPathBuilder.length() > 0) {
                currentPathBuilder.append("/");
            }
            currentPathBuilder.append(part);
            String currentPath = currentPathBuilder.toString();

            if (vfs != null && vfs.existsFolder(currentPath)) {
                Item item = Jenkins.get().getItemByFullName(currentPath);
                if (item != null && item instanceof ItemGroup) {
                    current = (ItemGroup<?>) item;
                    continue;
                }
            }

            Item item = current.getItem(part);
            if (item == null) {
                return false;
            }
            if (!(item instanceof ItemGroup)) {
                return false;
            }
            current = (ItemGroup<?>) item;
        }

        return true;
    }

    private static class PrecheckResult {
        boolean ok;
        String reason;
        String path;

        PrecheckResult(boolean ok, String reason, String path) {
            this.ok = ok;
            this.reason = reason;
            this.path = path;
        }
    }

    private PrecheckResult precheck(ItemGroup<?> base, String folderPath, String jobName) {
        if (!checkFolderExists(base, folderPath, null)) {
            return new PrecheckResult(false,
                    Messages.JobImportExportSidebarLink_dirNotFound(folderPath),
                    folderPath);
        }

        String fullName = buildTargetFullName(base, folderPath, jobName);

        Item item = Jenkins.get().getItemByFullName(fullName);

        if (item != null) {
            return new PrecheckResult(false,
                    Messages.JobImportExportSidebarLink_jobExistsAt(fullName),
                    fullName);
        }

        return new PrecheckResult(true, "OK", fullName);
    }

    private JobPathInfo parseJobPath(String entryName) {
        if (entryName == null) {
            return null;
        }

        entryName = entryName
                .replace("\\", "/")
                .replaceAll("^/+", "")
                .replaceAll("/+", "/");

        if (!entryName.endsWith("/config.xml")) {
            if (!entryName.endsWith(".xml")) {
                return null;
            }

            int idx = entryName.lastIndexOf("/");
            String fileName = idx >= 0 ? entryName.substring(idx + 1) : entryName;
            String jobName = fileName.replace(".xml", "");

            return new JobPathInfo("", jobName);
        }

        String fullPath = entryName.substring(0, entryName.lastIndexOf("/config.xml"));
        int lastSlash = fullPath.lastIndexOf("/");

        if (lastSlash < 0) {
            return new JobPathInfo("", fullPath);
        }

        String folderPath = fullPath.substring(0, lastSlash);
        String jobName = fullPath.substring(lastSlash + 1);

        return new JobPathInfo(folderPath, jobName);
    }

    private String buildFullName(
            ItemGroup<?> target,
            String name) {

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

    private String buildFolderFullName(
            ItemGroup<?> base,
            String folderPath) {

        StringBuilder sb = new StringBuilder();

        if (base instanceof AbstractItem) {
            sb.append(((AbstractItem) base).getFullName());
        }

        if (folderPath != null && !folderPath.trim().isEmpty()) {

            if (sb.length() > 0) {
                sb.append("/");
            }

            sb.append(folderPath);
        }

        return sb.toString();
    }

    private String buildTargetFullName(
            ItemGroup<?> baseGroup,
            String folderPath,
            String jobName) {

        StringBuilder sb = new StringBuilder();

        if (baseGroup instanceof AbstractItem) {
            sb.append(((AbstractItem) baseGroup).getFullName());
        }

        if (folderPath != null && !folderPath.trim().isEmpty()) {

            if (sb.length() > 0) {
                sb.append("/");
            }

            sb.append(folderPath
                    .replace("\\", "/")
                    .replaceAll("^/+", "")
                    .replaceAll("/+", "/"));
        }

        if (jobName != null && !jobName.isEmpty()) {

            if (sb.length() > 0) {
                sb.append("/");
            }

            sb.append(jobName);
        }

        return sb.toString();
    }

    private byte[] readZipEntry(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = zis.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    private boolean hasFolderPlugin() {
        return Jenkins.get()
                .getPluginManager()
                .getPlugin("cloudbees-folder") != null;
    }

    private ItemGroup<?> ensureFolderPath(
            ItemGroup<?> baseGroup,
            String folderPath,
            boolean create) throws IOException {
        return ensureFolderPath(baseGroup, folderPath, create, null);
    }

    private ItemGroup<?> ensureFolderPath(
            ItemGroup<?> baseGroup,
            String folderPath,
            boolean create,
            VirtualFsState vfs) throws IOException {

        ItemGroup<?> current = baseGroup;

        if (folderPath == null || folderPath.trim().isEmpty()) {
            return current;
        }

        folderPath = folderPath
                .replace("\\", "/")
                .replaceAll("^/+", "")
                .replaceAll("/+", "/");

        String[] parts = folderPath.split("/");
        StringBuilder currentPathBuilder = new StringBuilder();

        for (String raw : parts) {

            String part = sanitizeJobName(raw);

            if (part == null || part.isEmpty()) {
                continue;
            }

            if (currentPathBuilder.length() > 0) {
                currentPathBuilder.append("/");
            }
            currentPathBuilder.append(part);
            String currentPath = currentPathBuilder.toString();

            Item item = current.getItem(part);

            if (item == null) {
                if (!create) {
                    if (vfs != null) {
                        if (!vfs.existsFolder(currentPath)) {
                            vfs.createFolder(currentPath);
                        }
                    }
                    continue;
                }

                if (vfs != null) {
                    vfs.createFolder(currentPath);
                }

                if (!hasFolderPlugin()) {
                    throw new IOException(
                            Messages.JobImportExportSidebarLink_missingFolderPlugin(part)
                    );
                }

                if (!(current instanceof ModifiableTopLevelItemGroup)) {
                    throw new IOException(
                            Messages.JobImportExportSidebarLink_dirNotSupportFolder(part)
                    );
                }

                TopLevelItem folder =
                        ((ModifiableTopLevelItemGroup) current)
                                .createProject(
                                        Jenkins.get()
                                                .getDescriptorByType(
                                                        com.cloudbees.hudson.plugins.folder.Folder.DescriptorImpl.class
                                                ),
                                        part,
                                        false
                                );

                folder.save();

                item = folder;
            } else {
                if (vfs != null) {
                    vfs.addExistingFolder(currentPath);
                }
            }

            if (!(item instanceof ItemGroup)) {
                throw new IOException(
                        Messages.JobImportExportSidebarLink_pathNotFolder(currentPath)
                );
            }

            current = (ItemGroup<?>) item;
        }

        return current;
    }

    private String generateUniqueJobName(
            ItemGroup<?> baseGroup,
            String folderPath,
            String baseName) {

        String jobName = baseName;
        int counter = 1;

        while (Jenkins.get()
                .getItemByFullName(
                        buildTargetFullName(
                                baseGroup,
                                folderPath,
                                jobName
                        )
                ) != null) {

            jobName = baseName + "_" + counter;
            counter++;
        }

        return jobName;
    }

    

    private ImportResult checkImport(
            String folderPath,
            String jobName,
            byte[] xmlBytes,
            boolean overwrite,
            boolean rename,
            boolean dryRun,
            ItemGroup<?> itemGroup,
            ItemGroup<?> targetGroup) throws IOException {
        return checkImport(folderPath, jobName, xmlBytes, overwrite, rename, dryRun, itemGroup, targetGroup, null, null, null);
    }

    private ImportResult checkImport(
            String folderPath,
            String jobName,
            byte[] xmlBytes,
            boolean overwrite,
            boolean rename,
            boolean dryRun,
            ItemGroup<?> itemGroup,
            ItemGroup<?> targetGroup,
            ImportContext ctx,
            VirtualFsState vfs) throws IOException {
        return checkImport(folderPath, jobName, xmlBytes, overwrite, rename, dryRun, itemGroup, targetGroup, ctx, vfs, null);
    }

    private ImportResult checkImport(
            String folderPath,
            String jobName,
            byte[] xmlBytes,
            boolean overwrite,
            boolean rename,
            boolean dryRun,
            ItemGroup<?> itemGroup,
            ItemGroup<?> targetGroup,
            ImportContext ctx,
            VirtualFsState vfs,
            RenameContext renameCtx) throws IOException {

        ImportResult result = new ImportResult(jobName);

        try {
            if (ctx != null && ctx.blocked) {
                result.setStatus("BLOCKED");
                result.message = Messages.JobImportExportSidebarLink_upstreamBlocked();
                result.blockedBy = ctx.blockedReason;
                result.reason = "parent folder mismatch";
                return result;
            }

            jobName = sanitizeJobName(jobName);

            if (jobName == null) {
                result.setStatus("ERROR_INVALID_NAME");
                result.message = Messages.JobImportExportSidebarLink_jobNameEmpty();
                return result;
            }

            try {
                validateJobName(jobName);
            } catch (Exception e) {
                result.setStatus("ERROR_INVALID_NAME");
                result.message = Messages.JobImportExportSidebarLink_jobNameInvalid(e.getMessage());
                return result;
            }

            String xml = new String(xmlBytes, StandardCharsets.UTF_8);

            List<String> missingPlugins = checkMissingPlugins(xml);
            result.missingPlugins = missingPlugins;

            if (!missingPlugins.isEmpty()) {
                result.setStatus("ERROR_PLUGIN");
                result.message = Messages.JobImportExportSidebarLink_missingPluginDeps(String.join(", ", missingPlugins));
                return result;
            }

            if (!isCreatableGroup(itemGroup)) {
                result.setStatus("ERROR");
                result.message = Messages.JobImportExportSidebarLink_dirNotSupportCreateJob();
                return result;
            }

            String fullName =
                    buildTargetFullName(
                            itemGroup,
                            folderPath,
                            jobName
                    );
            result.fullPath = fullName;

            Item existingItem = Jenkins.get().getItemByFullName(fullName);

            if (existingItem != null) {
                if (overwrite) {
                    if (existingItem instanceof ItemGroup && (xmlBytes == null || xmlBytes.length == 0)) {
                        result.setStatus("REUSE");
                        result.message = Messages.JobImportExportSidebarLink_dirExistsReuse();
                    } else {
                        result.setStatus("OVERWRITE");
                        result.message = Messages.JobImportExportSidebarLink_willOverwriteExisting();
                    }
                } else if (rename) {
                    String newName =
                            generateUniqueJobName(
                                    itemGroup,
                                    folderPath,
                                    jobName
                            );
                    String effectiveFolderPath = renameCtx != null ? renameCtx.applyRename(folderPath) : folderPath;
                    result.finalName = effectiveFolderPath.isEmpty() ? newName : effectiveFolderPath + "/" + newName;
                    result.renamed = true;
                    result.setStatus("RENAME");
                    result.message = Messages.JobImportExportSidebarLink_jobExistsWillRename(result.finalName);

                    if (renameCtx != null) {
                        String oldPath = folderPath.isEmpty() ? jobName : folderPath + "/" + jobName;
                        String newPath = result.finalName;
                        renameCtx.addRename(oldPath, newPath);
                    }
                } else {
                    result.skipped = true;
                    result.setStatus("SKIP_EXISTS");
                    result.message = Messages.JobImportExportSidebarLink_jobExistsSkipped();
                    return result;
                }
            } else {
                result.setStatus("OK");
                result.message = Messages.JobImportExportSidebarLink_canImport();
            }

            String effectiveFolderPath = renameCtx != null ? renameCtx.applyRename(folderPath) : folderPath;
            
            if (result.finalName == null || result.finalName.equals(jobName)) {
                result.finalName = effectiveFolderPath.isEmpty() ? jobName : effectiveFolderPath + "/" + jobName;
            }
            
            if (!checkFolderExists(itemGroup, effectiveFolderPath, vfs)) {
                if (dryRun && vfs != null && !effectiveFolderPath.isEmpty()) {
                    vfs.createFolder(effectiveFolderPath);
                } else if (!dryRun) {
                    try {
                        ensureFolderPath(itemGroup, effectiveFolderPath, true, vfs);
                        if (vfs != null) {
                            vfs.createFolder(effectiveFolderPath);
                        }
                    } catch (IOException e) {
                        result.setStatus("SKIP_FOLDER_MISSING");
                        result.message = Messages.JobImportExportSidebarLink_createDirFailed(effectiveFolderPath, e.getMessage());
                        result.skipped = true;
                        return result;
                    }
                }
            }

            if (dryRun) {
                result.success = true;
                return result;
            }

            if (existingItem != null && overwrite) {
                backupConfig(existingItem);

                if (existingItem instanceof ItemGroup && (xmlBytes == null || xmlBytes.length == 0)) {
                    result.success = true;
                    result.setStatus("REUSE");
                    result.message = Messages.JobImportExportSidebarLink_dirExistsReuse();
                    return result;
                }

                if (existingItem instanceof AbstractItem) {
                    AbstractItem abstractItem = (AbstractItem) existingItem;

                    if (isSpecialFolder(abstractItem)) {
                        result.setStatus("ERROR");
                        result.message = Messages.JobImportExportSidebarLink_cannotOverwriteDynamicDir();
                        return result;
                    }

                    if (existingItem instanceof ItemGroup) {
                        if (isFolderConfig(xmlBytes)) {
                            try (InputStream in = new ByteArrayInputStream(xmlBytes)) {
                                abstractItem.updateByXml(new javax.xml.transform.stream.StreamSource(in));
                                abstractItem.save();
                            }
                            result.setStatus("OVERWRITE");
                            result.success = true;
                            result.message = Messages.JobImportExportSidebarLink_dirConfigOverwritten();
                            return result;
                        } else {
                            existingItem.delete();
                        }
                    } else {
                        try (InputStream in = new ByteArrayInputStream(xmlBytes)) {
                            abstractItem.updateByXml(new javax.xml.transform.stream.StreamSource(in));
                            abstractItem.save();
                        }
                        result.setStatus("OVERWRITE");
                        result.success = true;
                        result.message = Messages.JobImportExportSidebarLink_jobConfigOverwritten();
                        return result;
                    }
                } else if (existingItem instanceof ItemGroup) {
                    if (isFolderConfig(xmlBytes)) {
                        result.setStatus("ERROR");
                        result.message = Messages.JobImportExportSidebarLink_dirTypeNotSupported();
                        return result;
                    } else {
                        existingItem.delete();
                    }
                }
            }

            try (InputStream xmlStream = new ByteArrayInputStream(xmlBytes)) {
                TopLevelItem newItem = ((ModifiableTopLevelItemGroup) targetGroup).createProjectFromXML(
                        result.finalName,
                        cleanXml(xmlStream)
                );
                newItem.save();
            }

            result.success = true;

        } catch (Exception e) {
            result.setStatus("ERROR");
            result.message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }

        return result;
    }

    public void doResumeImport(StaplerRequest req, StaplerResponse rsp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        rsp.setCharacterEncoding("UTF-8");

        String batchId = req.getParameter("batchId");
        if (batchId == null || batchId.isEmpty()) {
            writeJson(rsp, false, Messages.JobImportExportSidebarLink_missingBatchId(), null);
            return;
        }

        CheckpointManager checkpointManager = CheckpointManager.getInstance();
        List<ImportCheckpoint> failedCheckpoints = checkpointManager.getFailedCheckpoints(batchId);

        if (failedCheckpoints.isEmpty()) {
            writeJson(rsp, false, Messages.JobImportExportSidebarLink_noFailedJobsToResume(), null);
            return;
        }

        Jenkins jenkins = Jenkins.get();
        if (!jenkins.hasPermission(Item.CREATE)) {
            writeJson(rsp, false, Messages.JobImportExportSidebarLink_noPermissionCreate(), null);
            return;
        }

        ModifiableTopLevelItemGroup itemGroup = jenkins;

        List<ImportResult> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (ImportCheckpoint checkpoint : failedCheckpoints) {
            try {
                String jobName = checkpoint.getJobName();
                byte[] xmlBytes = checkpoint.getXmlBytes();

                ItemGroup<?> targetGroup = ensureFolderPath(
                        itemGroup,
                        checkpoint.getFolderPath(),
                        true
                );

                ImportResult result = checkImport(checkpoint.getFolderPath(), jobName, xmlBytes, false, true, false, targetGroup, targetGroup);
                results.add(result);

                if (result.success) {
                    successCount++;
                    checkpoint.markRecovered();
                    checkpointManager.updateCheckpoint(checkpoint);
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                ImportResult errorResult = new ImportResult(checkpoint.getJobName());
                errorResult.setStatus("ERROR");
                errorResult.message = e.getMessage();
                results.add(errorResult);
                failCount++;
            }
        }

        writeBatchJson(rsp, successCount > 0, Messages.JobImportExportSidebarLink_resumeImportComplete(), successCount, failCount, 0, results, false, null);
    }

    public void doInstallPlugin(StaplerRequest req, StaplerResponse rsp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        rsp.setCharacterEncoding("UTF-8");

        String pluginShortName = req.getParameter("plugin");
        if (pluginShortName == null || pluginShortName.isEmpty()) {
            writeJson(rsp, false, Messages.JobImportExportSidebarLink_missingPluginParam(), null);
            return;
        }

        PluginSuggestionManager pluginManager = PluginSuggestionManager.getInstance();
        boolean success = pluginManager.installPlugin(pluginShortName);

        if (success) {
            writeJson(rsp, true, Messages.JobImportExportSidebarLink_pluginInstallStarted(), null);
        } else {
            writeJson(rsp, false, Messages.JobImportExportSidebarLink_pluginInstallFailed(), null);
        }
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
                json.append("\"statusCode\":\"").append(escapeJson(r.statusEnum != null ? r.statusEnum.name() : "")).append("\",");
                json.append("\"message\":\"").append(escapeJson(r.message)).append("\"");
                json.append("}");
            }
            json.append("]");
        }

        json.append("}");

        rsp.getWriter().write(json.toString());
    }

    private void backupConfig(Item item) throws IOException {
        if (item instanceof AbstractItem) {
            AbstractItem abstractItem = (AbstractItem) item;
            Path configFile = Paths.get(abstractItem.getRootDir().getAbsolutePath(), "config.xml");
            Path backupFile = Paths.get(abstractItem.getRootDir().getAbsolutePath(), "config.xml.bak");

            if (Files.exists(configFile)) {
                Files.copy(configFile, backupFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private boolean isFolderConfig(byte[] xmlBytes) {
        String xml = new String(xmlBytes, StandardCharsets.UTF_8);
        return xml.contains("com.cloudbees.hudson.plugins.folder.Folder");
    }

    private boolean isSpecialFolder(AbstractItem item) {
        String className = item.getClass().getName();
        return className.contains("ComputedFolder") ||
               className.contains("MultiBranch") ||
               className.contains("OrganizationFolder");
    }
}