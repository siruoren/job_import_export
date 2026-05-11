package com.example.jobimportexport;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import org.jvnet.hudson.reactor.ReactorException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

public class JobImportExportAction implements Action {

    private final AbstractItem item;

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


        byte[] fileContent = new byte[(int) fileItem.getSize()];
        try (InputStream is = fileItem.getInputStream()) {
            is.read(fileContent);
        }

        try {
            try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(fileContent)) {
                item.updateByXml(new StreamSource(bais));
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
        } catch (IllegalArgumentException e) {
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

        Item existingItem = Jenkins.get().getItemByFullName(buildFullName(jobName));
        if (existingItem != null) {
            String fullPath = Jenkins.get().getRootUrl() + existingItem.getUrl() + "jobImportExport";
            writeJson(rsp, false, "任务名称已存在：" + jobName + "\n\n可选操作：\n- 重新命名 — 使用新的任务名称重新导入\n- 进入任务更新配置 — 跳转到已有任务的导入/导出页面，通过「更新配置」功能覆盖其配置", fullPath);
            return;
        }

        try (InputStream is = fileItem.getInputStream()) {
            TopLevelItem newItem = itemGroup.createProjectFromXML(jobName, is);

            newItem.save();

            Jenkins.get().reload();

            String redirectUrl = Jenkins.get().getRootUrl() + newItem.getUrl();

            writeJson(rsp, true, "任务创建成功", redirectUrl);
        } catch (IllegalArgumentException e) {
            writeJson(rsp, false, "XML 配置非法：" + e.getMessage(), null);
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

    private String buildFullName(String jobName) {
        ItemGroup<?> target = getImportTarget();

        if (target instanceof AbstractItem) {
            return ((AbstractItem) target).getFullName() + "/" + jobName;
        }

        return jobName;
    }

    private ItemGroup<?> getImportTarget() {
        if (!canImportJobs()) {
            return null;
        }

        return (ItemGroup<?>) item;
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
        
        if (!isValidUtf8(jobName)) {
            throw new IllegalArgumentException("任务名称包含无效字符（可能是编码问题，请检查输入）");
        }
        
        if (jobName.matches(".*[\\\\/:*?\"<>|].*")) {
            throw new IllegalArgumentException("任务名称包含非法字符：\\ / : * ? \" < > |");
        }
        
        if (jobName.length() > 200) {
            throw new IllegalArgumentException("任务名称过长");
        }
    }

    private boolean isValidUtf8(String str) {
        if (str == null) {
            return false;
        }
        try {
            byte[] bytes = str.getBytes("UTF-8");
            String decoded = new String(bytes, "UTF-8");
            return str.equals(decoded);
        } catch (Exception e) {
            return false;
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