package com.example.jobimportexport;

import hudson.Extension;
import hudson.model.AbstractItem;
import hudson.model.Action;
import hudson.model.Job;
import hudson.model.TopLevelItem;
import hudson.model.ItemGroup;
import hudson.model.Item;
import jenkins.model.TransientActionFactory;
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
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;

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
        // 所有类型都可以更新配置
        return item != null;
    }

    public boolean isJobType() {
        // 判断当前任务是否是 Job 类型（非文件夹类型）
        return item instanceof Job;
    }

    public boolean isRootLevel() {
        // 判断当前任务是否在 Jenkins 根目录
        ItemGroup<?> parent = item.getParent();
        return parent instanceof Jenkins;
    }

    public boolean hasPermission() {
        return item != null && item.hasPermission(Item.CONFIGURE);
    }
    
    public boolean canCreateJob() {
        if (item == null) {
            return false;
        }
        
        // 获取当前任务的父目录（创建新任务的位置）
        ItemGroup<?> parent = item.getParent();
        if (parent == null) {
            return false;
        }
        
        // 检查父目录的创建权限
        // 对于文件夹，检查文件夹是否有创建子任务的权限
        // 对于根目录，检查 Jenkins 是否有创建任务的权限
        try {
            // 使用 ACL 系统检查权限
            if (parent instanceof AbstractItem) {
                return ((AbstractItem) parent).hasPermission(Item.CREATE);
            }
            if (parent instanceof Jenkins) {
                return ((Jenkins) parent).hasPermission(Item.CREATE);
            }
        } catch (Exception e) {
            // 权限检查失败，返回 false
            return false;
        }
        
        return false;
    }

    public void doExport(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if (item == null) {
            rsp.sendError(404, "未找到任务");
            return;
        }

        Path configFile = Paths.get(item.getRootDir().getAbsolutePath(), "config.xml");
        if (!Files.exists(configFile)) {
            rsp.sendError(404, "配置文件不存在");
            return;
        }

        String fileName = item.getFullName().replace("/", "_") + ".xml";
        
        rsp.setContentType("application/xml");
        rsp.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        
        try (OutputStream out = rsp.getOutputStream()) {
            Files.copy(configFile, out);
        }
    }

    @RequirePOST
    public void doUpdate(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException, ReactorException {
        Jenkins jenkins = Jenkins.get();
        
        FileItem fileItem = req.getFileItem("xmlFile");
        if (fileItem == null || fileItem.getSize() == 0) {
            rsp.sendError(400, "Please select an XML file to upload");
            return;
        }
        
        Path configFile = Paths.get(item.getRootDir().getAbsolutePath(), "config.xml");
        try (InputStream is = fileItem.getInputStream()) {
            Files.copy(is, configFile, StandardCopyOption.REPLACE_EXISTING);
        }
        jenkins.reload();
        rsp.sendRedirect("..");
    }

    @RequirePOST
    public void doImport(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException, ReactorException {
        Jenkins jenkins = Jenkins.get();
        
        // 导入创建新任务（在当前目录下）
        String jobName = null;
        
        if (req.getParameter("jobName") != null && !req.getParameter("jobName").trim().isEmpty()) {
            jobName = req.getParameter("jobName").trim();
        } else if (req.getParameterValues("jobName") != null && req.getParameterValues("jobName").length > 0) {
            jobName = req.getParameterValues("jobName")[0].trim();
        }
        
        if (jobName == null || jobName.isEmpty()) {
            rsp.sendError(400, "Job name cannot be empty");
            return;
        }
        
        FileItem fileItem = req.getFileItem("xmlFile");
        if (fileItem == null || fileItem.getSize() == 0) {
            rsp.sendError(400, "Please select an XML file to upload");
            return;
        }

        String fullJobName = item.getFullName() + "/" + jobName;
        if (jenkins.getItemByFullName(fullJobName) != null) {
            rsp.sendError(400, "Job already exists: " + fullJobName);
            return;
        }

        Path jobDir = Paths.get(item.getRootDir().getAbsolutePath(), "jobs", jobName);
        Files.createDirectories(jobDir);
        
        Path configFile = jobDir.resolve("config.xml");
        try (InputStream is = fileItem.getInputStream()) {
            Files.copy(is, configFile);
        }
        
        jenkins.reload();
        rsp.sendRedirect("/job/" + fullJobName.replace("/", "/job/"));
    }

    @RequirePOST
    public void doCreate(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException, ReactorException {
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
        
        ItemGroup<?> parent = item.getParent();
        String parentPath = "";
        if (parent != null && parent instanceof AbstractItem) {
            parentPath = ((AbstractItem) parent).getFullName();
        }
        
        String fullJobName = parentPath.isEmpty() ? jobName : parentPath + "/" + jobName;
        if (jenkins.getItemByFullName(fullJobName) != null) {
            rsp.sendError(400, "已存在同名任务: " + fullJobName);
            return;
        }

        Path jobDir;
        if (parent != null) {
            if (parent instanceof AbstractItem) {
                jobDir = Paths.get(((AbstractItem) parent).getRootDir().getAbsolutePath(), "jobs", jobName);
            } else {
                jobDir = Paths.get(jenkins.getRootDir().getAbsolutePath(), "jobs", jobName);
            }
        } else {
            jobDir = Paths.get(jenkins.getRootDir().getAbsolutePath(), "jobs", jobName);
        }
        
        Files.createDirectories(jobDir);
        
        Path configFile = jobDir.resolve("config.xml");
        try (InputStream is = fileItem.getInputStream()) {
            Files.copy(is, configFile);
        }
        
        jenkins.reload();
        rsp.sendRedirect("..");
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
