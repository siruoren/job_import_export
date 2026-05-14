package com.siruoren.jobimportexport;

import com.siruoren.jobimportexport.engine.ImportEngine;
import com.siruoren.jobimportexport.engine.model.ImportContext;
import com.siruoren.jobimportexport.engine.model.ImportResult;
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

    public boolean hasPermission() {
        if (item instanceof AccessControlled) {
            return ((AccessControlled) item).hasPermission(Item.CONFIGURE);
        }
        return false;
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

            ItemGroup<?> target = (item instanceof ItemGroup) ? (ItemGroup<?>) item : item.getParent();

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

            List<ImportResult> results;
            int successCount = 0;
            int failCount = 0;
            int skipCount = 0;

            SKIP_RELOAD.set(true);

            try (ZipInputStream zis = new ZipInputStream(fileItem.getInputStream(), StandardCharsets.UTF_8)) {
                ImportContext ctx = new ImportContext(dryRun, overwrite, rename, itemGroup);
                ImportEngine engine = new ImportEngine();
                results = engine.importZip(zis, ctx);

                for (ImportResult result : results) {
                    if (result.skipped) {
                        skipCount++;
                    } else if (result.success) {
                        successCount++;
                    } else {
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