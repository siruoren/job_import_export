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

    private final ItemGroup<?> group;

    public JobImportExportAction(ItemGroup<?> group) {
        this.group = group;
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

    public ItemGroup<?> getGroup() {
        return group;
    }

    public boolean isJob() {
        return group != null;
    }

    public boolean isJobType() {
        return group instanceof Job;
    }

    public boolean isRootLevel() {
        return group instanceof Jenkins;
    }

    public boolean canImportJobs() {
        // 如果当前是文件夹（ItemGroup），允许在其内部导入创建新任务
        if (group instanceof ItemGroup) {
            return true;
        }
        return canCreateJob();
    }

    public boolean hasPermission() {
        if (group instanceof AccessControlled) {
            return ((AccessControlled) group).hasPermission(Item.CONFIGURE);
        }
        return false;
    }

    public boolean canCreateJob() {
        if (group == null) {
            return false;
        }

        if (!(group instanceof ModifiableTopLevelItemGroup)) {
            return false;
        }

        if (group instanceof AccessControlled) {
            if (!((AccessControlled) group).hasPermission(Item.CREATE)) {
                return false;
            }
        }

        for (TopLevelItemDescriptor d : Items.all()) {
            if (d.isApplicableIn(group)) {
                return true;
            }
        }

        return false;
    }

    public List<TopLevelItemDescriptor> getSupportedJobTypes() {
        List<TopLevelItemDescriptor> result = new ArrayList<>();

        if (group == null) {
            return result;
        }

        if (!(group instanceof ModifiableTopLevelItemGroup)) {
            return result;
        }

        for (TopLevelItemDescriptor d : Items.all()) {
            if (d.isApplicableIn(group)) {
                result.add(d);
            }
        }

        return result;
    }

    public void doExport(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        if (!(group instanceof AbstractItem)) {
            rsp.sendError(404, "未找到任务");
            return;
        }

        AbstractItem item = (AbstractItem) group;
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
        if (!(group instanceof AbstractItem)) {
            rsp.sendError(404, "任务不存在");
            return;
        }

        AbstractItem item = (AbstractItem) group;
        item.checkPermission(Item.CONFIGURE);

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
                if (e.getMessage() != null && e.getMessage().contains("Expecting class") && "true".equals(forceReplace)) {
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

        if (!(group instanceof ModifiableTopLevelItemGroup)) {
            rsp.sendError(400, "当前目录不支持创建任务");
            return;
        }

        ModifiableTopLevelItemGroup itemGroup = (ModifiableTopLevelItemGroup) group;

        if (itemGroup instanceof AccessControlled) {
            ((AccessControlled) itemGroup).checkPermission(Item.CREATE);
        }

        if (Jenkins.get().getItemByFullName(buildFullName(jobName)) != null) {
            rsp.sendError(400, "任务已存在");
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
        if (group instanceof AbstractItem) {
            String parentName = ((AbstractItem) group).getFullName();
            return parentName + "/" + jobName;
        }
        return jobName;
    }

    @Extension
    public static class Factory extends TransientActionFactory<ItemGroup> {
        @Override
        public Class<ItemGroup> type() {
            return ItemGroup.class;
        }

        @Override
        public Collection<? extends Action> createFor(ItemGroup group) {
            System.out.println("[JobImportExport] Factory called: " + group.getClass().getName());

            if (group == null) {
                return Collections.emptyList();
            }

            // 排除 Jenkins 根目录
            if (group instanceof Jenkins) {
                System.out.println("[JobImportExport] Filtered out: Jenkins root");
                return Collections.emptyList();
            }

            // 排除特殊 Folder（Multibranch / Org / Computed）
            if (isSpecialFolder(group)) {
                System.out.println("[JobImportExport] Filtered out: Special folder");
                return Collections.emptyList();
            }

            System.out.println("[JobImportExport] Creating action for: " + group.getClass().getName());
            return Collections.singleton(new JobImportExportAction(group));
        }

        private static boolean isSpecialFolder(ItemGroup group) {
            String name = group.getClass().getName();
            return name.startsWith("jenkins.branch.") || name.contains("ComputedFolder");
        }
    }
}
