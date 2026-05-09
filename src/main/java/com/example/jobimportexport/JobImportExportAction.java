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
import java.lang.reflect.Method;

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

    private boolean isSpecialFolder(ItemGroup<?> folder) {
        // 检查文件夹是否是特殊类型（如 OrganizationFolder、MultiBranchProject）
        // 这些特殊文件夹的子任务不是通过文件系统目录管理的
        if (folder == null) {
            return false;
        }
        
        Class<?> folderClass = folder.getClass();
        String className = folderClass.getName();
        
        // 检查是否是特殊类型的文件夹
        // OrganizationFolder 和 MultiBranchProject 是常见的自动管理子任务的文件夹类型
        return className.startsWith("jenkins.branch.") ||
               className.contains("MultiBranch") ||
               className.contains("Organization");
    }

    public boolean canImportJobs() {
        // 判断是否可以在当前位置导入任务
        // 只有普通文件夹类型才能导入任务
        // Job 类型任务、特殊类型文件夹（如 OrganizationFolder、MultiBranchProject）都不能导入任务
        
        if (item instanceof Job) {
            // Job 类型任务，不能导入任务
            return false;
        } else if (item instanceof ItemGroup) {
            // 文件夹类型，检查是否是特殊类型
            return !isSpecialFolder((ItemGroup<?>) item);
        } else {
            // 其他非文件夹类型，不能导入任务
            return false;
        }
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
        
        // 检查任务是否存在
        if (item == null) {
            rsp.sendError(404, "任务不存在");
            return;
        }
        
        // 检查是否有权限更新配置
        if (!item.hasPermission(Item.CONFIGURE)) {
            rsp.sendError(403, "没有权限更新此任务的配置");
            return;
        }
        
        // 检查文件是否上传
        FileItem fileItem = req.getFileItem("xmlFile");
        if (fileItem == null || fileItem.getSize() == 0) {
            rsp.sendError(400, "请选择要上传的 XML 文件");
            return;
        }
        
        // 检查根目录是否存在
        if (item.getRootDir() == null) {
            rsp.sendError(400, "当前任务没有配置目录");
            return;
        }
        
        // 更新配置文件
        // 确保路径正确，支持嵌套子任务
        // Jenkins 中嵌套子任务的路径格式: jobs/父任务名/jobs/子任务名/jobs/孙子任务名/config.xml
        String fullName = item.getFullName();
        String[] pathParts = fullName.split("/");
        
        // 构建配置文件路径
        Path jobsDir = Paths.get(jenkins.getRootDir().getAbsolutePath(), "jobs");
        Path jobDir = jobsDir;
        
        for (String part : pathParts) {
            if (!part.isEmpty()) {
                jobDir = jobDir.resolve(part);
                jobDir = jobDir.resolve("jobs");
            }
        }
        
        // 移除末尾多余的 "/jobs"，然后添加 config.xml
        Path configFile = jobDir.getParent().resolve("config.xml");
        
        // 确保目录存在
        Files.createDirectories(configFile.getParent());
        
        // 写入配置文件
        try (InputStream is = fileItem.getInputStream()) {
            Files.copy(is, configFile, StandardCopyOption.REPLACE_EXISTING);
        }
        
        // 尝试重新加载当前任务及其父文件夹
        reloadItemAndParents(item);
        
        // 等待重新加载完成
        Thread.sleep(2000);
        
        // 重定向回当前任务页面
        rsp.sendRedirect("..");
    }

    private void reloadItemAndParents(AbstractItem item) {
        // 尝试使用反射重新加载当前任务及其所有父文件夹
        try {
            // 首先尝试重新加载当前任务
            try {
                Method reloadMethod = item.getClass().getMethod("reload");
                reloadMethod.invoke(item);
            } catch (NoSuchMethodException e) {
                try {
                    Method doReloadMethod = item.getClass().getMethod("doReload");
                    doReloadMethod.invoke(item);
                } catch (NoSuchMethodException e2) {
                    // 忽略
                }
            }
            
            // 然后递归重新加载父文件夹
            ItemGroup<?> parent = item.getParent();
            while (parent != null && parent instanceof AbstractItem && !(parent instanceof Jenkins)) {
                AbstractItem parentItem = (AbstractItem) parent;
                try {
                    Method reloadMethod = parentItem.getClass().getMethod("reload");
                    reloadMethod.invoke(parentItem);
                } catch (NoSuchMethodException e) {
                    try {
                        Method doReloadMethod = parentItem.getClass().getMethod("doReload");
                        doReloadMethod.invoke(parentItem);
                    } catch (NoSuchMethodException e2) {
                        // 忽略
                    }
                }
                parent = parentItem.getParent();
            }
        } catch (Exception e) {
            // 忽略异常
        }
    }

    private void reloadParentFolder() {
        // 尝试使用反射重新加载父文件夹，确保子任务配置正确更新
        try {
            ItemGroup<?> parent = item.getParent();
            if (parent != null && parent instanceof AbstractItem) {
                AbstractItem parentItem = (AbstractItem) parent;
                // 尝试调用父文件夹的 reload 方法
                try {
                    Method reloadMethod = parentItem.getClass().getMethod("reload");
                    reloadMethod.invoke(parentItem);
                } catch (NoSuchMethodException e) {
                    // 如果没有 reload 方法，尝试调用其他可能的方法
                    try {
                        Method doReloadMethod = parentItem.getClass().getMethod("doReload");
                        doReloadMethod.invoke(parentItem);
                    } catch (NoSuchMethodException e2) {
                        // 忽略，继续执行全局 reload
                    }
                }
                // 递归重新加载更高层的父文件夹
                reloadParentFolderRecursively(parent);
            }
        } catch (Exception e) {
            // 忽略异常，继续执行全局 reload
        }
    }

    private void reloadParentFolderRecursively(ItemGroup<?> folder) {
        // 递归重新加载父文件夹
        try {
            if (folder != null && folder instanceof AbstractItem) {
                AbstractItem parentItem = (AbstractItem) folder;
                ItemGroup<?> grandParent = parentItem.getParent();
                if (grandParent != null && grandParent instanceof AbstractItem && !(grandParent instanceof Jenkins)) {
                    // 尝试调用父文件夹的 reload 方法
                    try {
                        Method reloadMethod = grandParent.getClass().getMethod("reload");
                        reloadMethod.invoke(grandParent);
                    } catch (NoSuchMethodException e) {
                        // 如果没有 reload 方法，尝试调用其他可能的方法
                        try {
                            Method doReloadMethod = grandParent.getClass().getMethod("doReload");
                            doReloadMethod.invoke(grandParent);
                        } catch (NoSuchMethodException e2) {
                            // 忽略
                        }
                    }
                    // 继续递归
                    reloadParentFolderRecursively(grandParent);
                }
            }
        } catch (Exception e) {
            // 忽略异常
        }
    }

    @RequirePOST
    public void doImport(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException, InterruptedException, ReactorException {
        Jenkins jenkins = Jenkins.get();
        
        // 导入创建新任务
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

        // 获取正确的父目录和完整任务名
        ItemGroup<?> parent = item.getParent();
        String parentFullName = "";
        Path jobDir;
        
        // 确定实际的父容器（用于创建任务的容器）
        ItemGroup<?> actualParent;
        if (item instanceof Job) {
            // 普通任务类型，在其父目录下创建新任务
            actualParent = parent;
        } else {
            // 文件夹类型，在当前目录下创建新任务
            if (item instanceof ItemGroup) {
                actualParent = (ItemGroup<?>) item;
            } else {
                rsp.sendError(400, "Current item is not a valid container");
                return;
            }
        }
        
        // 检查父容器是否是特殊类型的文件夹（如 OrganizationFolder、MultiBranchProject）
        // 这些特殊文件夹的子任务不是通过文件系统目录管理的
        if (actualParent != null && isSpecialFolder(actualParent)) {
            rsp.sendError(400, "Cannot import jobs into this type of folder. This folder manages its children automatically.");
            return;
        }
        
        if (item instanceof Job) {
            // 普通任务类型，在其父目录下创建新任务
            if (parent != null) {
                if (parent instanceof AbstractItem) {
                    parentFullName = ((AbstractItem) parent).getFullName();
                    jobDir = Paths.get(((AbstractItem) parent).getRootDir().getAbsolutePath(), "jobs", jobName);
                } else if (parent instanceof Jenkins) {
                    parentFullName = "";
                    jobDir = Paths.get(jenkins.getRootDir().getAbsolutePath(), "jobs", jobName);
                } else {
                    rsp.sendError(400, "Invalid parent type");
                    return;
                }
            } else {
                rsp.sendError(400, "Parent not found");
                return;
            }
        } else {
            // 文件夹类型，在当前目录下创建新任务
            if (item.getRootDir() == null) {
                rsp.sendError(400, "Current item has no root directory");
                return;
            }
            parentFullName = item.getFullName();
            jobDir = Paths.get(item.getRootDir().getAbsolutePath(), "jobs", jobName);
        }
        
        String fullJobName = parentFullName.isEmpty() ? jobName : parentFullName + "/" + jobName;
        
        if (jenkins.getItemByFullName(fullJobName) != null) {
            rsp.sendError(400, "Job already exists: " + fullJobName);
            return;
        }

        Files.createDirectories(jobDir);
        
        Path configFile = jobDir.resolve("config.xml");
        try (InputStream is = fileItem.getInputStream()) {
            Files.copy(is, configFile, StandardCopyOption.REPLACE_EXISTING);
        }
        
        jenkins.reload();
        
        // 等待 Jenkins 重新加载完成
        Thread.sleep(1000);
        
        // 构建新任务的 URL
        String redirectUrl = "/job/" + fullJobName.replace("/", "/job/");
        rsp.sendRedirect(redirectUrl);
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
