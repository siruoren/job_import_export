package com.example.jobimportexport;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jvnet.hudson.reactor.ReactorException;

@Extension
public class JobImportExportSidebarLink implements RootAction {

    @Override
    public String getIconFileName() {
        return "gear2.png";
    }

    @Override
    public String getDisplayName() {
        return "任务导入/导出";
    }

    @Override
    public String getUrlName() {
        return "jobImportExport";
    }

    public boolean isVisible() {
        return true;
    }

    public boolean canCreateJob() {
        return Jenkins.get().hasPermission(Item.CREATE);
    }

    public void doExport(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String jobName = req.getParameter("job");
        if (jobName == null || jobName.isEmpty()) {
            writeJson(rsp, false, "任务名称不能为空", null);
            return;
        }

        Jenkins jenkins = Jenkins.get();
        AbstractItem item = jenkins.getItemByFullName(jobName, AbstractItem.class);
        if (item == null) {
            writeJson(rsp, false, "未找到任务: " + jobName, null);
            return;
        }

        if (item instanceof AccessControlled) {
            if (!((AccessControlled) item).hasPermission(Item.READ)) {
                writeJson(rsp, false, "无权限：当前用户没有查看此任务配置的权限", null);
                return;
            }
        }

        Path configFile = Paths.get(item.getRootDir().getAbsolutePath(), "config.xml");
        if (!Files.exists(configFile)) {
            writeJson(rsp, false, "配置文件不存在", null);
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
    }

    @RequirePOST
    public void doImport(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException, ReactorException {
        rsp.setCharacterEncoding("UTF-8");

        FileItem fileItem = req.getFileItem("xmlFile");
        if (fileItem == null || fileItem.getSize() == 0) {
            writeJson(rsp, false, "请选择要上传的XML文件", null);
            return;
        }

        String rawName = req.getParameter("jobName");

        String jobName = null;

        if (rawName != null) {
            jobName = new String(
                    rawName.getBytes("ISO-8859-1"),
                    "UTF-8");

            jobName = Util.fixEmptyAndTrim(jobName);
        }

        if (jobName == null) {
            writeJson(rsp, false, "任务名称不能为空", null);
            return;
        }

        Jenkins jenkins = Jenkins.get();

        String parentFolder = null;
        String actualJobName = jobName;

        if (jobName.contains("/")) {
            int idx = jobName.lastIndexOf('/');
            parentFolder = jobName.substring(0, idx);
            actualJobName = jobName.substring(idx + 1);
        }

        String fullJobName = jobName;
        if (jenkins.getItemByFullName(fullJobName) != null) {
            String fullPath = req.getContextPath() + "/job/" + fullJobName.replace("/", "/job/") + "/jobImportExport";
            writeJson(rsp, false, "任务名称已存在：" + jobName + "\n\n可选操作：\n- 重新命名 — 使用新的任务名称重新导入\n- 进入任务更新配置 — 跳转到已有任务的导入/导出页面，通过「更新配置」功能覆盖其配置", fullPath);
            return;
        }

        AbstractItem parent = null;
        if (parentFolder != null) {
            parent = jenkins.getItemByFullName(parentFolder, AbstractItem.class);
            if (parent == null) {
                writeJson(rsp, false, "父文件夹不存在：" + parentFolder, null);
                return;
            }
        }

        ItemGroup<?> target = (parent != null && parent instanceof ItemGroup) ? (ItemGroup<?>) parent : jenkins;
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

        TopLevelItem newItem;
        try (InputStream is = fileItem.getInputStream()) {
            newItem = itemGroup.createProjectFromXML(actualJobName, is);
        }

        newItem.save();

        Jenkins.get().reload();

        String url = newItem.getUrl();
        if (!url.startsWith("/")) {
            url = "/" + url;
        }
        String redirectUrl = url;

        writeJson(rsp, true, "任务创建成功", redirectUrl);
    }

    private void writeJson(
            StaplerResponse rsp,
            boolean success,
            String message,
            String redirect) throws IOException {

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
}