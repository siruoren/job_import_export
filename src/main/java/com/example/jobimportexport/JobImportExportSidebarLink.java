package com.example.jobimportexport;

import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.RootAction;
import hudson.security.AccessControlled;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.apache.commons.fileupload.FileItem;

import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.jvnet.hudson.reactor.ReactorException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    public void doExport(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        String jobName = req.getParameter("job");
        if (jobName == null || jobName.isEmpty()) {
            rsp.sendError(400, "任务名称不能为空");
            return;
        }

        Jenkins jenkins = Jenkins.get();
        AbstractItem item = jenkins.getItemByFullName(jobName, AbstractItem.class);
        if (item == null) {
            rsp.sendError(404, "未找到任务: " + jobName);
            return;
        }

        if (item instanceof AccessControlled) {
            if (!((AccessControlled) item).hasPermission(Item.READ)) {
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
                    writer.println("<h2>导出失败：无权限</h2>");
                    writer.println("<p>当前用户没有查看此任务配置的权限。</p>");
                    writer.println("<p><a href='javascript:history.back()' class='btn'>返回</a></p>");
                    writer.println("<p class='hint'>提示：请更换具有 <b>Item.READ</b> 权限的登录用户后重试。</p>");
                    writer.println("</div></body></html>");
                }
                return;
            }
        }

        Path configFile = Paths.get(item.getRootDir().getAbsolutePath(), "config.xml");
        if (!Files.exists(configFile)) {
            rsp.sendError(404, "配置文件不存在");
            return;
        }

        String fileName = jobName.replace("/", "_") + ".xml";
        
        rsp.setContentType("application/xml");
        rsp.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        
        try (OutputStream out = rsp.getOutputStream()) {
            Files.copy(configFile, out);
        }
    }

    @RequirePOST
    public void doImport(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException, ReactorException {
        FileItem fileItem = req.getFileItem("xmlFile");
        if (fileItem == null || fileItem.getSize() == 0) {
            rsp.sendError(400, "请选择要上传的XML文件");
            return;
        }

        String jobName = req.getParameter("jobName");
        if (jobName == null || jobName.isEmpty()) {
            rsp.sendError(400, "任务名称不能为空");
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
                writer.println("任务名称：" + fullJobName + "<br/>");
                writer.println("所属目录：" + (parentFolder != null ? parentFolder : "根目录"));
                writer.println("</div>");
                writer.println("<p><a href='javascript:history.back()' class='btn'>返回重新命名</a><a href='" + req.getContextPath() + "/job/" + fullJobName.replace("/", "/job/") + "/jobImportExport' class='btn'>进入任务更新配置</a></p>");
                writer.println("<p class='hint'>提示：您可以使用新名称重新导入，或者进入已有任务页面更新其配置。</p>");
                writer.println("</div></body></html>");
            }
            return;
        }

        AbstractItem parent = null;
        if (parentFolder != null) {
            parent = jenkins.getItemByFullName(parentFolder, AbstractItem.class);
            if (parent == null) {
                rsp.sendError(404, "父文件夹不存在: " + parentFolder);
                return;
            }
        }

        ItemGroup<?> target = (parent != null && parent instanceof ItemGroup) ? (ItemGroup<?>) parent : jenkins;
        if (target instanceof AccessControlled) {
            if (!((AccessControlled) target).hasPermission(Item.CREATE)) {
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

        Path jobDir;
        if (parent != null && parent instanceof ItemGroup) {
            jobDir = Paths.get(parent.getRootDir().getAbsolutePath(), actualJobName);
        } else {
            jobDir = Paths.get(jenkins.getRootDir().getAbsolutePath(), "jobs", actualJobName);
        }
        
        Files.createDirectories(jobDir);
        
        Path configFile = jobDir.resolve("config.xml");
        try (InputStream is = fileItem.getInputStream()) {
            Files.copy(is, configFile);
        }
        
        jenkins.reload();
        rsp.sendRedirect("/job/" + jobName.replace("/", "/job/"));
    }
}
