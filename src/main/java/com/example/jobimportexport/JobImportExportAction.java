package com.example.jobimportexport;

import hudson.Extension;
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
        // 当前对象必须是 Folder
        if (!(item instanceof ItemGroup) || item instanceof Job) {
            return false;
        }

        // 特殊 Folder 不允许
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

    public void doExport(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        item.checkPermission(Item.READ);

        String fileName = item.getFullName().replace("/", "_") + ".xml";

        rsp.setContentType("application/xml");
        rsp.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        Path configFile = Paths.get(item.getRootDir().getAbsolutePath(), "config.xml");
        if (!Files.exists(configFile)) {
            rsp.sendError(404, "配置文件不存在");
            return;
        }

        try (OutputStream out = rsp.getOutputStream()) {
            Files.copy(configFile, out);
        }
    }

    @RequirePOST
    public void doUpdate(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if (item instanceof AccessControlled) {
            if (!((AccessControlled) item).hasPermission(Item.CONFIGURE)) {
                rsp.setContentType("text/html;charset=UTF-8");
                rsp.setStatus(403);
                try (java.io.PrintWriter writer = rsp.getWriter()) {
                    writer.println("<!DOCTYPE html>");
                    writer.println("<html><head><title>权限不足</title>");
                    writer.println("<style>");
                    writer.println("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#f5f5f5;padding:40px;}");
                    writer.println(".card{max-width:600px;margin:0 auto;background:#fff;border:1px solid #dee2e6;border-radius:6px;padding:24px;}");
                    writer.println("h2{color:#d9534f;margin-top:0;}");
                    writer.println(".btn{display:inline-block;padding:8px 16px;background:#337ab7;color:#fff;text-decoration:none;border-radius:4px;}");
                    writer.println(".btn:hover{background:#286090;}");
                    writer.println(".hint{color:#666;font-size:14px;margin-top:16px;}");
                    writer.println("</style></head><body>");
                    writer.println("<div class='card'>");
                    writer.println("<h2>配置更新失败：无权限</h2>");
                    writer.println("<p>当前用户没有更新此任务配置的权限。</p>");
                    writer.println("<p><a href='javascript:history.back()' class='btn'>返回</a></p>");
                    writer.println("<p class='hint'>提示：请更换具有 <b>Item.CONFIGURE</b> 权限的登录用户后重试。</p>");
                    writer.println("</div></body></html>");
                }
                return;
            }
        }

        FileItem fileItem = req.getFileItem("xmlFile");

        if (fileItem == null || fileItem.getSize() == 0) {
            rsp.sendError(400, "请选择 XML 文件");
            return;
        }

        String forceReplace = req.getParameter("forceReplace");

        byte[] fileContent = new byte[(int) fileItem.getSize()];
        try (InputStream is = fileItem.getInputStream()) {
            is.read(fileContent);
        }

        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(fileContent)) {
            try {
                item.updateByXml(new StreamSource(bais));
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().contains("Expecting class")) {
                    if ("true".equals(forceReplace)) {
                        Path configFile = Paths.get(item.getRootDir().getAbsolutePath(), "config.xml");
                        Files.write(configFile, fileContent);
                        rsp.sendRedirect2("../");
                        return;
                    }
                    rsp.setContentType("text/html;charset=UTF-8");
                    rsp.setStatus(400);
                    try (java.io.PrintWriter writer = rsp.getWriter()) {
                        writer.println("<!DOCTYPE html>");
                        writer.println("<html><head><title>配置更新失败</title>");
                        writer.println("<style>");
                        writer.println("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#f5f5f5;padding:40px;}");
                        writer.println(".card{max-width:600px;margin:0 auto;background:#fff;border:1px solid #dee2e6;border-radius:6px;padding:24px;}");
                        writer.println("h2{color:#d9534f;margin-top:0;}");
                        writer.println(".info{background:#f8f9fa;border:1px solid #e9ecef;border-radius:4px;padding:12px;margin:16px 0;font-family:monospace;font-size:13px;}");
                        writer.println(".btn{display:inline-block;padding:8px 16px;background:#337ab7;color:#fff;text-decoration:none;border-radius:4px;}");
                        writer.println(".btn:hover{background:#286090;}");
                        writer.println(".hint{color:#666;font-size:14px;margin-top:16px;}");
                        writer.println("</style></head><body>");
                        writer.println("<div class='card'>");
                        writer.println("<h2>配置更新失败：任务类型不匹配</h2>");
                        writer.println("<p>当前任务的类型与导入的 XML 配置文件不匹配。</p>");
                        writer.println("<div class='info'>");
                        writer.println("当前任务类型：" + item.getClass().getName() + "<br/>");
                        writer.println("错误详情：" + e.getMessage());
                        writer.println("</div>");
                        writer.println("<p><a href='javascript:history.back()' class='btn'>返回重新选择</a></p>");
                        writer.println("<p class='hint'>提示：如果确认要强制替换配置文件，请勾选「强制替换」选项后重试。</p>");
                        writer.println("</div></body></html>");
                    }
                    return;
                }
                throw e;
            }
        }

        rsp.sendRedirect2("../");
    }

    @RequirePOST
    public void doImport(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String jobName = req.getParameter("jobName");

        if (jobName == null || jobName.trim().isEmpty()) {
            rsp.sendError(400, "任务名称不能为空");
            return;
        }

        FileItem fileItem = req.getFileItem("xmlFile");

        if (fileItem == null || fileItem.getSize() == 0) {
            rsp.sendError(400, "请选择 XML 文件");
            return;
        }

        ItemGroup<?> target = getImportTarget();

        if (!(target instanceof ModifiableTopLevelItemGroup)) {
            rsp.sendError(400, "当前目录不支持创建任务");
            return;
        }

        ModifiableTopLevelItemGroup itemGroup = (ModifiableTopLevelItemGroup) target;

        if (itemGroup instanceof AccessControlled) {
            if (!((AccessControlled) itemGroup).hasPermission(Item.CREATE)) {
                rsp.setContentType("text/html;charset=UTF-8");
                rsp.setStatus(403);
                try (java.io.PrintWriter writer = rsp.getWriter()) {
                    writer.println("<!DOCTYPE html>");
                    writer.println("<html><head><title>权限不足</title>");
                    writer.println("<style>");
                    writer.println("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#f5f5f5;padding:40px;}");
                    writer.println(".card{max-width:600px;margin:0 auto;background:#fff;border:1px solid #dee2e6;border-radius:6px;padding:24px;}");
                    writer.println("h2{color:#d9534f;margin-top:0;}");
                    writer.println(".btn{display:inline-block;padding:8px 16px;background:#337ab7;color:#fff;text-decoration:none;border-radius:4px;}");
                    writer.println(".btn:hover{background:#286090;}");
                    writer.println(".hint{color:#666;font-size:14px;margin-top:16px;}");
                    writer.println("</style></head><body>");
                    writer.println("<div class='card'>");
                    writer.println("<h2>任务创建失败：无权限</h2>");
                    writer.println("<p>当前用户没有在该目录创建任务的权限。</p>");
                    writer.println("<p><a href='javascript:history.back()' class='btn'>返回</a></p>");
                    writer.println("<p class='hint'>提示：请更换具有 <b>Item.CREATE</b> 权限的登录用户后重试。</p>");
                    writer.println("</div></body></html>");
                }
                return;
            }
        }

        if (Jenkins.get().getItemByFullName(buildFullName(jobName)) != null) {
            rsp.setContentType("text/html;charset=UTF-8");
            rsp.setStatus(400);
            try (java.io.PrintWriter writer = rsp.getWriter()) {
                writer.println("<!DOCTYPE html>");
                writer.println("<html><head><title>任务创建失败</title>");
                writer.println("<style>");
                writer.println("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:#f5f5f5;padding:40px;}");
                writer.println(".card{max-width:600px;margin:0 auto;background:#fff;border:1px solid #dee2e6;border-radius:6px;padding:24px;}");
                writer.println("h2{color:#d9534f;margin-top:0;}");
                writer.println(".info{background:#f8f9fa;border:1px solid #e9ecef;border-radius:4px;padding:12px;margin:16px 0;font-family:monospace;font-size:13px;}");
                writer.println(".btn{display:inline-block;padding:8px 16px;background:#337ab7;color:#fff;text-decoration:none;border-radius:4px;margin-right:8px;}");
                writer.println(".btn:hover{background:#286090;}");
                writer.println(".hint{color:#666;font-size:14px;margin-top:16px;}");
                writer.println("</style></head><body>");
                writer.println("<div class='card'>");
                writer.println("<h2>任务创建失败：任务名称已存在</h2>");
                writer.println("<p>在该目录下已存在同名任务。</p>");
                writer.println("<div class='info'>");
                writer.println("任务名称：" + jobName + "<br/>");
                writer.println("所属目录：" + (target instanceof AbstractItem ? ((AbstractItem) target).getFullName() : "根目录") + "<br/>");
                writer.println("全路径：" + buildFullName(jobName));
                writer.println("</div>");
                writer.println("<p><a href='javascript:history.back()' class='btn'>返回重新命名</a><a href='" + req.getContextPath() + "/job/" + buildFullName(jobName).replace("/", "/job/") + "/jobImportExport' class='btn'>进入任务更新配置</a></p>");
                writer.println("<p class='hint'>提示：您可以使用新名称重新导入，或者进入已有任务页面更新其配置。</p>");
                writer.println("</div></body></html>");
            }
            return;
        }

        try (InputStream is = fileItem.getInputStream()) {
            TopLevelItem newItem = itemGroup.createProjectFromXML(jobName, is);
            rsp.sendRedirect2(req.getContextPath() + "/" + newItem.getUrl());
        } catch (IllegalArgumentException e) {
            rsp.sendError(400, "XML 配置非法: " + e.getMessage());
        }
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
