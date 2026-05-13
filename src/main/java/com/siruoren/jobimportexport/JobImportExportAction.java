package com.siruoren.jobimportexport;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.ItemGroup;
import hudson.model.Item;
import hudson.model.Items;
import hudson.security.AccessControlled;
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
import java.util.Collection;
import org.jvnet.hudson.reactor.ReactorException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import hudson.PluginWrapper;
import hudson.PluginManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.LinkedHashMap;
import java.util.Map;

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
        return "导入/导出配置";
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

    public boolean canImportJobs() {
        if (!(item instanceof ItemGroup) || item instanceof Job) {
            return false;
        }

        if (isSpecialFolder(item)) {
            return false;
        }

        return true;
    }

    public boolean hasPermission() {
        if (item instanceof AccessControlled) {
            return ((AccessControlled) item).hasPermission(Item.CONFIGURE);
        }
        return false;
    }

    public boolean canCreateJob() {
        ItemGroup<?> target = getImportTarget();

        if (target == null) {
            return false;
        }

        if (!(target instanceof ModifiableTopLevelItemGroup)) {
            return false;
        }

        if (target instanceof AccessControlled) {
            if (!((AccessControlled) target).hasPermission(Item.CREATE)) {
                return false;
            }
        }

        for (TopLevelItemDescriptor d : Items.all()) {
            if (d.isApplicableIn(target)) {
                return true;
            }
        }

        return false;
    }

    public List<TopLevelItemDescriptor> getSupportedJobTypes() {
        List<TopLevelItemDescriptor> result = new ArrayList<>();

        ItemGroup<?> target = getImportTarget();

        if (target == null) {
            return result;
        }

        if (!(target instanceof ModifiableTopLevelItemGroup)) {
            return result;
        }

        for (TopLevelItemDescriptor d : Items.all()) {
            if (d.isApplicableIn(target)) {
                result.add(d);
            }
        }

        return result;
    }

    public void doExport(StaplerRequest req, StaplerResponse rsp) {
        try {
            item.checkPermission(Item.READ);

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

        Path configFile = Paths.get(item.getRootDir().getAbsolutePath(), "config.xml");
        if (!Files.exists(configFile)) {
            writeJson(rsp, false, "配置文件不存在", null);
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
                        "{\"success\":false,\"message\":\"" + escapeJson("导出失败：" + msg) + "\",\"redirect\":null}"
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
                writeJson(rsp, false, "无权限：当前用户没有更新此任务配置的权限", null);
                return;
            }
        }

        FileItem fileItem = req.getFileItem("xmlFile");

        if (fileItem == null || fileItem.getSize() == 0) {
            writeJson(rsp, false, "请选择 XML 文件", null);
            return;
        }

        byte[] fileContent = readAll(fileItem.getInputStream());

        try {
            try (InputStream safeStream = safeXml(fileContent)) {
                item.updateByXml(new StreamSource(safeStream));
            }
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Expecting class")) {
   
                writeJson(rsp, false, "任务类型不匹配：" + e.getMessage() + "\n\n请确认任务类型是否匹配后重试。", null);
                return;

            } else {
                writeJson(rsp, false, "XML解析失败：" + e.getMessage(), null);
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

            writeJson(rsp, true, "更新成功", redirectUrl);
        } catch (Exception e) {
            if (!rsp.isCommitted()) {
                try {
                    rsp.reset();
                    rsp.setCharacterEncoding("UTF-8");
                    rsp.setContentType("application/json;charset=UTF-8");
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    rsp.getWriter().write(
                        "{\"success\":false,\"message\":\"" + escapeJson("更新失败：" + msg) + "\",\"redirect\":null}"
                    );
                } catch (Exception ignored) {
                }
            }
        }
    }

    @RequirePOST
    public void doImport(StaplerRequest req, StaplerResponse rsp) {
        try {
            req.setCharacterEncoding("UTF-8");
            rsp.setCharacterEncoding("UTF-8");

            String jobName = req.getParameter("jobName");
            if (jobName != null) {
                try {
                    byte[] bytes = jobName.getBytes("ISO-8859-1");
                    jobName = new String(bytes, "UTF-8");
                } catch (Exception e) {
                }
            }            
            
            jobName = sanitizeJobName(jobName);

        if (jobName == null) {
            writeJson(rsp, false, "任务名称不能为空", null);
            return;
        }

        try {
            validateJobName(jobName);
        } catch (Exception e) {
            writeJson(rsp, false, "任务名称不合法：" + e.getMessage(), null);
            return;
        }

        FileItem fileItem = req.getFileItem("xmlFile");

        if (fileItem == null || fileItem.getSize() == 0) {
            writeJson(rsp, false, "请选择 XML 文件", null);
            return;
        }

        ItemGroup<?> target = getImportTarget();

        if (!(target instanceof ModifiableTopLevelItemGroup)) {
            writeJson(rsp, false, "当前目录不支持创建任务", null);
            return;
        }

        ModifiableTopLevelItemGroup itemGroup = (ModifiableTopLevelItemGroup) target;

        if (itemGroup instanceof AccessControlled) {
            if (!((AccessControlled) itemGroup).hasPermission(Item.CREATE)) {
                writeJson(rsp, false, "无权限：当前用户没有在该目录创建任务的权限", null);
                return;
            }
        }

        Item existingItem = Jenkins.get().getItemByFullName(buildFullName(itemGroup, jobName));
        if (existingItem != null) {
            String fullPath = Jenkins.get().getRootUrl() + existingItem.getUrl() + "jobImportExport";
            writeJson(rsp, false, "任务名称已存在：" + jobName + "\n\n可选操作：\n- 重新命名 — 使用新的任务名称重新导入\n- 进入任务更新配置 — 跳转到已有任务的导入/导出页面，通过「更新配置」功能覆盖其配置", fullPath);
            return;
        }

        byte[] xmlBytes = readAll(fileItem.getInputStream());

        String xml = new String(xmlBytes, StandardCharsets.UTF_8);

        List<String> missingPlugins = checkMissingPlugins(xml);

        if (!missingPlugins.isEmpty()) {
            writeJson(rsp, false, "缺少插件依赖：" + String.join(", ", missingPlugins), null);
            return;
        }

        try {
            TopLevelItem newItem = ((ModifiableTopLevelItemGroup) target).createProjectFromXML(
                    jobName,
                    safeXml(xmlBytes)
            );
            newItem.save();

            safeReload();

            String redirectUrl = Jenkins.get().getRootUrl() + newItem.getUrl();

            writeJson(rsp, true, "任务创建成功", redirectUrl);
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg == null || msg.trim().isEmpty()) {
                    msg = e.getClass().getSimpleName();
                }
                msg = msg.replaceAll("[\\r\\n]", " ");

                writeJson(rsp, false, "导入失败：" + msg, null);
                return;
            }
        } catch (Exception e) {
            if (!rsp.isCommitted()) {
                try {
                    rsp.reset();
                    rsp.setCharacterEncoding("UTF-8");
                    rsp.setContentType("application/json;charset=UTF-8");
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    rsp.getWriter().write(
                        "{\"success\":false,\"message\":\"" + escapeJson("导入失败：" + msg) + "\",\"redirect\":null}"
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
                writeJson(rsp, false, "请选择 ZIP 文件", null);
                return;
            }

            boolean overwrite = Boolean.parseBoolean(req.getParameter("overwrite"));
            boolean rename = Boolean.parseBoolean(req.getParameter("rename"));
            boolean dryRun = Boolean.parseBoolean(req.getParameter("dryRun"));

            ItemGroup<?> target = getImportTarget();

            if (!(target instanceof ModifiableTopLevelItemGroup)) {
                writeJson(rsp, false, "当前目录不支持创建任务", null);
                return;
            }

            ModifiableTopLevelItemGroup itemGroup = (ModifiableTopLevelItemGroup) target;

            if (itemGroup instanceof AccessControlled) {
                if (!((AccessControlled) itemGroup).hasPermission(Item.CREATE)) {
                    writeJson(rsp, false, "无权限：当前用户没有在该目录创建任务的权限", null);
                    return;
                }
            }

            List<ImportResult> results = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;
            int skipCount = 0;

            ImportContext ctx = new ImportContext();
            VirtualFsState vfs = new VirtualFsState();

            initializeVirtualFsState(vfs, itemGroup);

            SKIP_RELOAD.set(true);

            try (ZipInputStream zis = new ZipInputStream(fileItem.getInputStream(), StandardCharsets.UTF_8)) {
                ZipEntry entry;

                while ((entry = zis.getNextEntry()) != null) {
                    try {
                        if (entry.isDirectory()) {
                            continue;
                        }

                        String entryName = entry.getName();

                        if (!entryName.endsWith(".xml")) {
                            continue;
                        }

                        JobPathInfo pathInfo = parseJobPath(entryName);

                        if (pathInfo == null) {
                            continue;
                        }

                        byte[] xmlBytes = readZipEntry(zis);

                        try {
                            String actualFolderPath = ctx.applyRename(pathInfo.folderPath);
                            String actualJobName = pathInfo.jobName;

                            String fullName = buildTargetFullName(itemGroup, actualFolderPath, actualJobName);
                            
                            if (!ctx.existingJobsCache.contains(fullName)) {
                                Item existing = Jenkins.get().getItemByFullName(fullName);
                                if (existing != null) {
                                    ctx.existingJobsCache.add(fullName);
                                }
                            }

                            ItemGroup<?> targetGroup = itemGroup;

                            if (!dryRun) {
                                targetGroup = ensureFolderPath(
                                        itemGroup,
                                        actualFolderPath,
                                        true,
                                        vfs
                                );
                            } else {
                                ensureFolderPath(
                                        itemGroup,
                                        actualFolderPath,
                                        false,
                                        vfs
                                );
                            }

                            ImportResult result = checkImport(
                                    actualFolderPath,
                                    actualJobName,
                                    xmlBytes,
                                    overwrite,
                                    rename,
                                    dryRun,
                                    itemGroup,
                                    targetGroup,
                                    ctx,
                                    vfs
                            );

                            result.zipPath = entryName;
                            result.sourcePath = entryName;
                            result.displayPath = pathInfo.folderPath.isEmpty() ? pathInfo.jobName : pathInfo.folderPath + "/" + pathInfo.jobName;

                            if (!dryRun && result.status.equals("CONFLICT")) {
                                ctx.block(result.message);
                            }

                            if (result.renamed) {
                                String oldPath = pathInfo.folderPath.isEmpty() ? pathInfo.jobName : pathInfo.folderPath + "/" + pathInfo.jobName;
                                String newPath = result.finalName;
                                ctx.renameMap.put(oldPath, newPath);
                            }

                            results.add(result);

                            if (result.skipped) {
                                skipCount++;
                            } else if (result.success) {
                                successCount++;
                            } else {
                                failCount++;
                            }

                            continue;
                        } catch (IOException e) {
                            ImportResult result = new ImportResult(pathInfo.jobName);
                            result.zipPath = entryName;
                            result.sourcePath = entryName;
                            result.displayPath = pathInfo.folderPath.isEmpty() ? pathInfo.jobName : pathInfo.folderPath + "/" + pathInfo.jobName;
                            result.success = false;
                            result.message = "创建目录失败: " + e.getMessage();
                            results.add(result);
                            failCount++;
                            continue;
                        }

                    } catch (Exception e) {
                        ImportResult errorResult = new ImportResult(entry.getName());
                        errorResult.status = "ERROR";
                        errorResult.message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        results.add(errorResult);
                        failCount++;
                    }
                }
            } finally {
                SKIP_RELOAD.remove();
            }

            if (!dryRun) {
                safeReload();
            }

            writeBatchJson(rsp, true, dryRun ? "预演完成" : "批量导入完成", successCount, failCount, skipCount, results, dryRun);

        } catch (Exception e) {
            if (!rsp.isCommitted()) {
                try {
                    rsp.reset();
                    rsp.setCharacterEncoding("UTF-8");
                    rsp.setContentType("application/json;charset=UTF-8");
                    String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    rsp.getWriter().write(
                        "{\"success\":false,\"message\":\"" + escapeJson("批量导入失败：" + msg) + "\"}"
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
            boolean dryRun) throws IOException {

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

    private ItemGroup<?> getImportTarget() {
        if (!canImportJobs()) {
            return null;
        }

        return item instanceof ItemGroup
                ? (ItemGroup<?>) item
                : item.getParent();
    }

    private static boolean isSpecialFolder(AbstractItem item) {
        String name = item.getClass().getName();
        return name.startsWith("jenkins.branch.") || name.contains("ComputedFolder");
    }

    private void reloadItem(AbstractItem target) throws IOException {
        try {
            target.onLoad(
                    target.getParent(),
                    target.getName()
            );
        } catch (Exception e) {
            throw new IOException(
                    "任务 Reload 失败: "
                            + e.getMessage(),
                    e
            );
        }
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
            throw new IllegalArgumentException("任务名称不能为空");
        }
        
        jobName = jobName.trim().replace('\u3000', ' ');
        
        for (int i = 0; i < jobName.length(); i++) {
            char c = jobName.charAt(i);
            
            if (Character.isISOControl(c)) {
                throw new IllegalArgumentException("任务名称包含非法控制字符");
            }
        }
        
        if (jobName.matches(".*[\\\\/:*?\"<>|].*")) {
            throw new IllegalArgumentException("任务名称包含非法字符：\\ / : * ? \" < > |");
        }
        
        if (jobName.length() > 200) {
            throw new IllegalArgumentException("任务名称过长");
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

    private byte[] readZipEntry(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = zis.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    private void safeReload() {
        if (!Boolean.TRUE.equals(SKIP_RELOAD.get())) {
            try {
                Jenkins.get().reload();
            } catch (IOException | InterruptedException | ReactorException e) {
                // 忽略 reload 异常，不影响导入流程
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

    private enum NodeType {
        UNKNOWN,
        FOLDER,
        JOB
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

    private static class VirtualItemGroupWrapper {
        private final ItemGroup<?> parent;
        private final String name;

        VirtualItemGroupWrapper(ItemGroup<?> parent, String name) {
            this.parent = parent;
            this.name = name;
        }

        String getName() {
            return name;
        }

        String getFullName() {
            String parentFullName = parent.getFullName();
            if (parentFullName.isEmpty()) {
                return name;
            }
            return parentFullName + "/" + name;
        }

        Item getItem(String name) {
            return null;
        }

        ItemGroup<?> getParentGroup() {
            return parent;
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

    private static class ImportContext {
        boolean blocked = false;
        String blockedReason;
        Map<String, NodeType> typeMap = new HashMap<>();
        Set<String> blockedPaths = new HashSet<>();
        
        Set<String> processedJobs = new HashSet<>();
        Set<String> existingJobsCache = new HashSet<>();
        Map<String, String> renameMap = new HashMap<>();

        void block(String reason) {
            this.blocked = true;
            this.blockedReason = reason;
        }

        void reset() {
            this.blocked = false;
            this.blockedReason = null;
            this.typeMap.clear();
            this.blockedPaths.clear();
            this.processedJobs.clear();
            this.existingJobsCache.clear();
            this.renameMap.clear();
        }

        boolean isPathBlocked(String path) {
            if (blockedPaths.contains(path)) {
                return true;
            }
            for (String blockedPath : blockedPaths) {
                if (path.startsWith(blockedPath + "/") || path.equals(blockedPath)) {
                    return true;
                }
            }
            return false;
        }

        String applyRename(String path) {
            if (path == null || path.isEmpty()) {
                return path;
            }

            String resolved = path;
            boolean changed = true;

            while (changed) {
                changed = false;

                List<String> sortedKeys = new ArrayList<>(renameMap.keySet());
                sortedKeys.sort((a, b) -> b.length() - a.length());

                for (String oldPath : sortedKeys) {
                    String newPath = renameMap.get(oldPath);
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
            r.status = "BLOCKED";
            r.message = "父级冲突阻断：" + ctx.blockedReason;
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
            ctx.blockedReason =
                    "类型冲突：路径 " + fullPath +
                    " 之前已定义为 " + ctx.typeMap.get(fullPath) +
                    "，当前为 " + newType;
            ctx.blockedPaths.add(fullPath);

            ImportResult r = new ImportResult(name);
            r.status = "CONFLICT";
            r.message = ctx.blockedReason;
            results.add(r);
            return;
        }

        if (!isLast && item != null && !(item instanceof ItemGroup)) {
            ctx.blocked = true;
            ctx.blockedReason = "类型冲突：路径 " + fullPath + " 已是 Job，不允许作为目录";
            ctx.blockedPaths.add(fullPath);

            ImportResult r = new ImportResult(name);
            r.status = "CONFLICT";
            r.message = ctx.blockedReason;
            results.add(r);
            return;
        }

        if (isLast && item != null && item instanceof ItemGroup) {
            ctx.blocked = true;
            ctx.blockedReason = "类型冲突：路径 " + fullPath + " 已是 Folder，不允许作为 Job";
            ctx.blockedPaths.add(fullPath);

            ImportResult r = new ImportResult(name);
            r.status = "CONFLICT";
            r.message = ctx.blockedReason;
            results.add(r);
            return;
        }

        if (isLast && item != null) {
            ImportResult r = new ImportResult(name);
            r.status = "SKIP_EXISTS";
            r.message = "任务已存在";
            results.add(r);
            return;
        }

        if (!isLast) {
            if (item == null) {
                if (!dryRun) {
                    if (!(base instanceof ModifiableTopLevelItemGroup)) {
                        ctx.block("当前层级不可创建目录: " + name);
                        ImportResult r = new ImportResult(name);
                        r.status = "ERROR";
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
                r.status = "ERROR";
                r.message = "当前层级不可创建任务: " + name;
                results.add(r);
                return;
            }

            String jobName = name.replace(".config.xml", "");
            try (InputStream in = safeXml(xmlBytes)) {
                ((ModifiableTopLevelItemGroup) base)
                        .createProjectFromXML(jobName, in);
            }
        }

        ImportResult r = new ImportResult(name);
        r.status = "OK";
        r.success = true;
        results.add(r);
    }

    private Map<String, Object> buildTree(List<String> paths) {
        Map<String, Object> root = new LinkedHashMap<>();

        for (String path : paths) {
            String[] parts = path.split("/");
            Map<String, Object> current = root;

            for (int i = 0; i < parts.length; i++) {
                String p = parts[i];
                boolean isLast = (i == parts.length - 1);

                if (!current.containsKey(p)) {
                    current.put(p, new LinkedHashMap<String, Object>());
                }

                if (isLast && p.endsWith(".xml")) {
                    current.put(p, "JOB");
                }

                if (!isLast) {
                    current = (Map<String, Object>) current.get(p);
                }
            }
        }

        return root;
    }

    private void validateStructure(ItemGroup<?> base, String folderPath) throws IOException {
        if (folderPath == null || folderPath.isEmpty()) {
            return;
        }

        String[] parts = folderPath.split("/");
        ItemGroup<?> current = base;

        for (String part : parts) {
            Item item = current.getItem(part);

            if (item == null) {
                throw new IOException("结构不一致，缺失目录: " + part);
            }

            if (!(item instanceof ItemGroup)) {
                throw new IOException("路径冲突（不是目录）: " + part);
            }

            current = (ItemGroup<?>) item;
        }
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
                    "目录不存在：" + folderPath,
                    folderPath);
        }

        String fullName = buildTargetFullName(base, folderPath, jobName);

        Item item = Jenkins.get().getItemByFullName(fullName);

        if (item != null) {
            return new PrecheckResult(false,
                    "任务已存在：" + fullName,
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
                            "缺少 Folder 插件，无法创建目录: " + part
                    );
                }

                if (!(current instanceof ModifiableTopLevelItemGroup)) {
                    throw new IOException(
                            "目录不支持创建 Folder: " + part
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
                        "路径不是 Folder: " + currentPath
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

    private static class ImportResult {
        String jobName;
        String finalName;
        boolean success;
        boolean skipped;
        boolean renamed;
        String status;
        String message;
        List<String> missingPlugins;
        String zipPath;
        String fullPath;
        String blockedBy;
        String reason;
        String sourcePath;
        String displayPath;

        ImportResult(String jobName) {
            this.jobName = jobName;
            this.finalName = jobName;
            this.missingPlugins = new ArrayList<>();
        }
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
        return checkImport(folderPath, jobName, xmlBytes, overwrite, rename, dryRun, itemGroup, targetGroup, null, null);
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

        ImportResult result = new ImportResult(jobName);

        try {
            if (ctx != null && ctx.blocked) {
                result.status = "BLOCKED";
                result.message = "上游冲突阻断，后续路径禁止创建";
                result.blockedBy = ctx.blockedReason;
                result.reason = "parent folder mismatch";
                return result;
            }

            jobName = sanitizeJobName(jobName);

            if (jobName == null) {
                result.status = "ERROR_INVALID_NAME";
                result.message = "任务名称不能为空";
                return result;
            }

            try {
                validateJobName(jobName);
            } catch (Exception e) {
                result.status = "ERROR_INVALID_NAME";
                result.message = "任务名称不合法：" + e.getMessage();
                return result;
            }

            String xml = new String(xmlBytes, StandardCharsets.UTF_8);

            List<String> missingPlugins = checkMissingPlugins(xml);
            result.missingPlugins = missingPlugins;

            if (!missingPlugins.isEmpty()) {
                result.status = "ERROR_PLUGIN";
                result.message = "缺少插件依赖：" + String.join(", ", missingPlugins);
                return result;
            }

            if (!isCreatableGroup(itemGroup)) {
                result.status = "ERROR";
                result.message = "当前目录不支持创建任务";
                return result;
            }

            String fullName =
                    buildTargetFullName(
                            itemGroup,
                            folderPath,
                            jobName
                    );
            result.fullPath = fullName;

            boolean exists = ctx != null && ctx.existingJobsCache.contains(fullName);
            Item existingItem = null;
            
            if (!exists) {
                existingItem = Jenkins.get().getItemByFullName(fullName);
                exists = existingItem != null;
                if (exists && ctx != null) {
                    ctx.existingJobsCache.add(fullName);
                }
            }

            if (exists) {
                if (overwrite) {
                    result.status = "OVERWRITE";
                    result.message = "将覆盖已存在的任务";
                } else if (rename) {
                    String newName =
                            generateUniqueJobName(
                                    itemGroup,
                                    folderPath,
                                    jobName
                            );
                    String effectiveFolderPath = ctx != null ? ctx.applyRename(folderPath) : folderPath;
                    result.finalName = effectiveFolderPath.isEmpty() ? newName : effectiveFolderPath + "/" + newName;
                    result.renamed = true;
                    result.status = "RENAME";
                    result.message = "任务已存在，将重命名为：" + result.finalName;
                    
                    if (ctx != null) {
                        String oldPath = folderPath.isEmpty() ? jobName : folderPath + "/" + jobName;
                        String newPath = result.finalName;
                        ctx.renameMap.put(oldPath, newPath);
                    }
                } else {
                    result.skipped = true;
                    result.status = "SKIP_EXISTS";
                    result.message = "任务已存在，已跳过";
                    return result;
                }
            } else {
                result.status = "OK";
                result.message = "可以导入";
            }

            String effectiveFolderPath = ctx != null ? ctx.applyRename(folderPath) : folderPath;
            
            if (result.finalName == null || result.finalName.equals(jobName)) {
                result.finalName = effectiveFolderPath.isEmpty() ? jobName : effectiveFolderPath + "/" + jobName;
            }
            
            if (!checkFolderExists(itemGroup, effectiveFolderPath, vfs)) {
                if (dryRun && vfs != null && !effectiveFolderPath.isEmpty()) {
                    vfs.createFolder(effectiveFolderPath);
                } else if (!dryRun) {
                    result.status = "SKIP_FOLDER_MISSING";
                    result.message = "目录不存在：" + effectiveFolderPath;
                    result.skipped = true;
                    return result;
                }
            }

            if (dryRun) {
                result.success = true;
                return result;
            }

            if (existingItem != null && overwrite) {
                backupConfig(existingItem);
                
                if (existingItem instanceof Job) {
                    existingItem.delete();
                } else if (existingItem instanceof ItemGroup) {
                    if (existingItem instanceof AbstractItem) {
                        try (InputStream in = new ByteArrayInputStream(xmlBytes)) {
                            ((AbstractItem) existingItem).updateByXml(new StreamSource(in));
                            ((AbstractItem) existingItem).save();
                        }
                        result.success = true;
                        result.status = "OVERWRITE";
                        result.message = "目录配置已覆盖（保留子任务）";
                        return result;
                    }
                    result.success = false;
                    result.status = "ERROR";
                    result.message = "不支持覆盖此类目录类型";
                    return result;
                }
            }

            try (InputStream xmlStream = safeXml(xmlBytes)) {
                TopLevelItem newItem = ((ModifiableTopLevelItemGroup) targetGroup).createProjectFromXML(
                        result.finalName,
                        xmlStream
                );
                newItem.save();
            }

            result.success = true;

        } catch (Exception e) {
            result.status = "ERROR";
            result.message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }

        return result;
    }

    public void doResumeImport(StaplerRequest req, StaplerResponse rsp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        rsp.setCharacterEncoding("UTF-8");

        String batchId = req.getParameter("batchId");
        if (batchId == null || batchId.isEmpty()) {
            writeJson(rsp, false, "缺少 batchId 参数", null);
            return;
        }

        CheckpointManager checkpointManager = CheckpointManager.getInstance();
        List<ImportCheckpoint> failedCheckpoints = checkpointManager.getFailedCheckpoints(batchId);

        if (failedCheckpoints.isEmpty()) {
            writeJson(rsp, false, "没有需要恢复的失败任务", null);
            return;
        }

        ItemGroup<?> target = getImportTarget();
        if (!(target instanceof ModifiableTopLevelItemGroup)) {
            writeJson(rsp, false, "当前目录不支持创建任务", null);
            return;
        }

        ModifiableTopLevelItemGroup itemGroup = (ModifiableTopLevelItemGroup) target;

        List<ImportResult> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        SKIP_RELOAD.set(true);
        try {
            for (ImportCheckpoint checkpoint : failedCheckpoints) {
                try {
                    String jobName = checkpoint.getJobName();
                    byte[] xmlBytes = checkpoint.getXmlBytes();

                    ItemGroup<?> targetGroup = ensureFolderPath(
                            itemGroup,
                            checkpoint.getFolderPath(),
                            true
                    );

                    ImportResult result = checkImport(checkpoint.getFolderPath(), jobName, xmlBytes, false, true, false, (ModifiableTopLevelItemGroup) targetGroup, targetGroup);
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
                    errorResult.status = "ERROR";
                    errorResult.message = e.getMessage();
                    results.add(errorResult);
                    failCount++;
                }
            }
        } finally {
            SKIP_RELOAD.remove();
        }

        safeReload();

        writeBatchJson(rsp, successCount > 0, "恢复导入完成", successCount, failCount, 0, results, false);
    }

    public void doInstallPlugin(StaplerRequest req, StaplerResponse rsp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        rsp.setCharacterEncoding("UTF-8");

        String pluginShortName = req.getParameter("plugin");
        if (pluginShortName == null || pluginShortName.isEmpty()) {
            writeJson(rsp, false, "缺少 plugin 参数", null);
            return;
        }

        PluginSuggestionManager pluginManager = PluginSuggestionManager.getInstance();
        boolean success = pluginManager.installPlugin(pluginShortName);

        if (success) {
            writeJson(rsp, true, "插件安装已启动，请稍后刷新页面查看安装状态", null);
        } else {
            writeJson(rsp, false, "插件安装失败，请检查插件名称或手动安装", null);
        }
    }

    public void doProgress(StaplerRequest req, StaplerResponse rsp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        rsp.setContentType("text/event-stream");
        rsp.setCharacterEncoding("UTF-8");

        String batchId = req.getParameter("batchId");
        if (batchId == null || batchId.isEmpty()) {
            return;
        }

        ProgressManager progressManager = ProgressManager.getInstance();
        ImportProgress progress = progressManager.getProgress(batchId);

        if (progress == null) {
            return;
        }

        try (java.io.PrintWriter writer = rsp.getWriter()) {
            int lastProgress = -1;
            int timeout = 30000;
            long startTime = System.currentTimeMillis();

            while (System.currentTimeMillis() - startTime < timeout) {
                if (progress.getOverallProgress() != lastProgress) {
                    lastProgress = progress.getOverallProgress();
                    
                    String eventData = String.format(
                        "{\"batchId\":\"%s\",\"currentJob\":\"%s\",\"currentJobIndex\":%d,\"totalJobs\":%d,\"overallProgress\":%d,\"status\":\"%s\",\"message\":\"%s\"}",
                        escapeJson(progress.getBatchId()),
                        escapeJson(progress.getCurrentJob() != null ? progress.getCurrentJob() : ""),
                        progress.getCurrentJobIndex(),
                        progress.getTotalJobs(),
                        progress.getOverallProgress(),
                        escapeJson(progress.getStatus()),
                        escapeJson(progress.getMessage() != null ? progress.getMessage() : "")
                    );
                    
                    writer.write("data: " + eventData + "\n\n");
                    writer.flush();
                }

                if ("DONE".equals(progress.getStatus()) || "ERROR".equals(progress.getStatus())) {
                    break;
                }

                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
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

    @Extension
    public static class Factory extends TransientActionFactory<AbstractItem> {
        @Override
        public Class<AbstractItem> type() {
            return AbstractItem.class;
        }

        @Override
        public Collection<? extends Action> createFor(AbstractItem target) {
            if (target == null) {
                return Collections.emptyList();
            }

            return Collections.singleton(new JobImportExportAction(target));
        }
    }
}