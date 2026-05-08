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

    public boolean isFolder() {
        return item != null && item instanceof ItemGroup<?>;
    }

    public boolean isJob() {
        return item != null;
    }

    public boolean hasPermission() {
        return item != null && item.hasPermission(Item.CONFIGURE);
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
    public void doImport(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException, ReactorException {
        FileItem fileItem = req.getFileItem("xmlFile");
        if (fileItem == null || fileItem.getSize() == 0) {
            rsp.sendError(400, "请选择要上传的XML文件");
            return;
        }

        Jenkins jenkins = Jenkins.get();

        if (isJob() && !isFolder()) {
            Path configFile = Paths.get(item.getRootDir().getAbsolutePath(), "config.xml");
            try (InputStream is = fileItem.getInputStream()) {
                Files.copy(is, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
            jenkins.reload();
            rsp.sendRedirect("..");
        } else if (isFolder()) {
            String jobName = req.getParameter("jobName");
            if (jobName == null || jobName.isEmpty()) {
                rsp.sendError(400, "任务名称不能为空");
                return;
            }

            String fullJobName = item.getFullName() + "/" + jobName;
            if (jenkins.getItemByFullName(fullJobName) != null) {
                rsp.sendError(400, "已存在同名任务: " + fullJobName);
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
        } else {
            rsp.sendError(400, "不支持的任务类型");
        }
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
