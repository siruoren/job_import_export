package com.example.jobimportexport;

import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.ItemGroup;
import hudson.model.RootAction;
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
            rsp.sendError(400, "已存在同名任务: " + fullJobName);
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
