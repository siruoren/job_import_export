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
        ItemGroup<?> parent = item.getParent();
        return parent instanceof Jenkins;
    }

    public boolean canImportJobs() {
        return canCreateJob();
    }

    public boolean hasPermission() {
        return item != null && item.hasPermission(Item.CONFIGURE);
    }

    public boolean canCreateJob() {
        if (item == null) {
            return false;
        }

        ItemGroup<?> parent = getCreateTarget();

        if (parent == null) {
            return false;
        }

        if (!(parent instanceof ModifiableTopLevelItemGroup)) {
            return false;
        }

        if (parent instanceof AccessControlled) {
            if (!((AccessControlled) parent).hasPermission(Item.CREATE)) {
                return false;
            }
        }

        for (TopLevelItemDescriptor d : Items.all()) {
            if (d.isApplicableIn(parent)) {
                return true;
            }
        }

        return false;
    }

    private ItemGroup<?> getCreateTarget() {
        if (item == null) {
            return null;
        }

        if (item instanceof Job) {
            return item.getParent();
        }

        if (item instanceof ItemGroup) {
            return (ItemGroup<?>) item;
        }

        return null;
    }

    public List<TopLevelItemDescriptor> getSupportedJobTypes() {
        List<TopLevelItemDescriptor> result = new ArrayList<>();

        ItemGroup<?> parent = getCreateTarget();

        if (parent == null) {
            return result;
        }

        if (!(parent instanceof ModifiableTopLevelItemGroup)) {
            return result;
        }

        for (TopLevelItemDescriptor d : Items.all()) {
            if (d.isApplicableIn(parent)) {
                result.add(d);
            }
        }

        return result;
    }

    public void doExport(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if (item == null) {
            rsp.sendError(404, "未找到任务");
            return;
        }

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
        if (item == null) {
            rsp.sendError(404, "任务不存在");
            return;
        }

        item.checkPermission(Item.CONFIGURE);

        FileItem fileItem = req.getFileItem("xmlFile");

        if (fileItem == null || fileItem.getSize() == 0) {
            rsp.sendError(400, "请选择 XML 文件");
            return;
        }

        // 获取强制替换参数
        String forceReplace = req.getParameter("forceReplace");
        
        // 先将文件内容读取到内存中
        byte[] fileContent = new byte[(int) fileItem.getSize()];
        try (InputStream is = fileItem.getInputStream()) {
            is.read(fileContent);
        }
        
        // 使用内存中的内容进行更新
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(fileContent)) {
            try {
                item.updateByXml(new StreamSource(bais));
            } catch (IOException e) {
                // 捕获类型不匹配错误
                if (e.getMessage() != null && e.getMessage().contains("Expecting class") && "true".equals(forceReplace)) {
                    // 强制替换模式：直接替换 config.xml 文件
                    Path configFile = Paths.get(item.getRootDir().getAbsolutePath(), "config.xml");
                    Files.write(configFile, fileContent);
                    rsp.sendRedirect2("../");
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

        ItemGroup<?> parent = getCreateTarget();

        if (parent == null) {
            rsp.sendError(400, "当前目录不支持创建任务");
            return;
        }

        if (!(parent instanceof ModifiableTopLevelItemGroup)) {
            rsp.sendError(400, "当前目录不可创建任务");
            return;
        }

        ModifiableTopLevelItemGroup group = (ModifiableTopLevelItemGroup) parent;

        if (Jenkins.get().getItemByFullName(buildFullName(parent, jobName)) != null) {
            rsp.sendError(400, "任务已存在");
            return;
        }

        if (group instanceof AccessControlled) {
            ((AccessControlled) group).checkPermission(Item.CREATE);
        }

        try (InputStream is = fileItem.getInputStream()) {
            TopLevelItem newItem = group.createProjectFromXML(jobName, is);

            rsp.sendRedirect2(req.getContextPath() + "/" + newItem.getUrl());

        } catch (IllegalArgumentException e) {
            rsp.sendError(400, "XML 配置非法: " + e.getMessage());
        }
    }

    private String buildFullName(ItemGroup<?> parent, String jobName) {
        if (parent instanceof AbstractItem) {
            String parentName = ((AbstractItem) parent).getFullName();
            return parentName + "/" + jobName;
        }

        return jobName;
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
