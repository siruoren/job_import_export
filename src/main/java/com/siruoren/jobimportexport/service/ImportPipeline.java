package com.siruoren.jobimportexport.service;

import com.siruoren.jobimportexport.model.*;
import hudson.model.AbstractItem;
import hudson.model.ItemGroup;
import jenkins.model.Jenkins;
import jenkins.model.ModifiableTopLevelItemGroup;

import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ImportPipeline {

    private static final int MAX_ENTRIES = 2000;
    private static final long MAX_ZIP_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int MAX_XML_SIZE = 10 * 1024 * 1024; // 10MB
    private static final long MAX_TIMEOUT = 5 * 60_000; // 5 minutes

    private final PluginValidator pluginValidator;
    private final FolderService folderService;
    private final JobService jobService;

    public ImportPipeline() {
        this.pluginValidator = new PluginValidator();
        this.folderService = new FolderService();
        this.jobService = new JobService();
    }

    public List<ImportResult> execute(ZipInputStream zis, ItemGroup<?> baseGroup,
                                      boolean overwrite, boolean rename, boolean dryRun) throws IOException {

        List<ImportResult> results = new ArrayList<>();
        ImportContext ctx = new ImportContext();
        VirtualFsState vfs = new VirtualFsState();

        long startTime = System.currentTimeMillis();
        int entryCount = 0;
        long totalSize = 0;

        ZipEntry entry;
        while ((entry = zis.getNextEntry()) != null) {
            if (System.currentTimeMillis() - startTime > MAX_TIMEOUT) {
                throw new RuntimeException("导入超时");
            }

            if (++entryCount > MAX_ENTRIES) {
                throw new IOException("ZIP 文件条目过多");
            }

            totalSize += entry.getSize();
            if (totalSize > MAX_ZIP_SIZE) {
                throw new IOException("ZIP 文件过大");
            }

            try {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                if (!entryName.endsWith(".xml")) {
                    continue;
                }

                byte[] xmlBytes = readZipEntry(zis);
                if (xmlBytes.length > MAX_XML_SIZE) {
                    throw new IOException("XML 文件过大");
                }

                ImportResult result = processEntry(entryName, xmlBytes, baseGroup,
                        overwrite, rename, dryRun, ctx, vfs);
                results.add(result);

            } catch (Exception e) {
                String errEntryName = entry != null ? entry.getName() : "unknown";
                ImportResult errorResult = new ImportResult(errEntryName);
                errorResult.zipPath = errEntryName;
                errorResult.status = ImportStatus.ERROR;
                errorResult.message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                results.add(errorResult);
            }
        }

        return results;
    }

    private ImportResult processEntry(String entryName, byte[] xmlBytes, ItemGroup<?> baseGroup,
                                      boolean overwrite, boolean rename, boolean dryRun,
                                      ImportContext ctx, VirtualFsState vfs) {
        JobPathInfo pathInfo = parseJobPath(entryName);
        if (pathInfo == null) {
            ImportResult r = new ImportResult(entryName);
            r.zipPath = entryName;
            r.status = ImportStatus.ERROR;
            r.message = "无法解析路径";
            return r;
        }

        String jobName = pathInfo.jobName;
        String folderPath = pathInfo.folderPath;

        if (ctx.isBlocked()) {
            return ImportResult.blocked(jobName, folderPath, ctx.getBlockedReason());
        }

        jobName = folderService.sanitizeJobName(jobName);
        if (jobName == null) {
            return ImportResult.errorInvalidName(pathInfo.jobName, folderPath, "任务名称不能为空");
        }

        List<String> missingPlugins = pluginValidator.checkMissingPlugins(xmlBytes);
        if (!missingPlugins.isEmpty()) {
            return ImportResult.errorPlugin(jobName, folderPath, missingPlugins);
        }

        String fullPath = folderService.buildFullPath(baseGroup, folderPath, jobName);

        try {
            ItemGroup<?> targetGroup = baseGroup;
            if (!dryRun) {
                targetGroup = folderService.ensureFolderPath(baseGroup, folderPath, true, vfs);
            } else {
                folderService.ensureFolderPath(baseGroup, folderPath, false, vfs);
            }

            if (!folderService.isCreatableGroup(targetGroup)) {
                return ImportResult.error(jobName, folderPath, "当前目录不支持创建任务");
            }

            hudson.model.Item existingItem = Jenkins.get().getItemByFullName(fullPath);
            if (existingItem != null) {
                if (overwrite) {
                    return handleOverwrite(jobName, folderPath, fullPath, xmlBytes, existingItem, dryRun);
                } else if (rename) {
                    String newName = jobService.generateUniqueJobName(baseGroup, folderPath, jobName);
                    return ImportResult.rename(jobName, folderPath, newName);
                } else {
                    return ImportResult.skipExists(jobName, folderPath);
                }
            } else {
                ImportResult r = ImportResult.ok(jobName, folderPath);
                r.zipPath = entryName;
                r.fullPath = fullPath;

                if (!dryRun) {
                    try (ByteArrayInputStream is = new ByteArrayInputStream(xmlBytes)) {
                        jobService.createJob(targetGroup, jobName, is);
                    }
                    r.success = true;
                }

                return r;
            }

        } catch (IOException e) {
            return ImportResult.error(jobName, folderPath, "创建失败: " + e.getMessage());
        }
    }

    private ImportResult handleOverwrite(String jobName, String folderPath, String fullPath,
                                         byte[] xmlBytes, hudson.model.Item existingItem, boolean dryRun) {
        ImportResult result = new ImportResult(jobName, folderPath);
        result.fullPath = fullPath;

        if (existingItem instanceof AbstractItem) {
            AbstractItem abstractItem = (AbstractItem) existingItem;

            if (jobService.isSpecialFolder(abstractItem)) {
                result.status = ImportStatus.ERROR;
                result.message = "不允许覆盖动态目录类型任务（如 Multibranch Pipeline）";
                return result;
            }

            if (jobService.isFolderConfig(xmlBytes)) {
                if (!dryRun) {
                    try (InputStream in = new ByteArrayInputStream(xmlBytes)) {
                        abstractItem.updateByXml(new StreamSource(in));
                        abstractItem.save();
                    } catch (IOException e) {
                        result.status = ImportStatus.ERROR;
                        result.message = "更新目录配置失败: " + e.getMessage();
                        return result;
                    }
                }
                result.status = ImportStatus.OVERWRITE;
                result.success = true;
                result.message = "目录配置已覆盖（保留子任务）";
                return result;
            }

            if (!dryRun) {
                try (InputStream in = new ByteArrayInputStream(xmlBytes)) {
                    abstractItem.updateByXml(new StreamSource(in));
                    abstractItem.save();
                } catch (IOException e) {
                    result.status = ImportStatus.ERROR;
                    result.message = "更新任务配置失败: " + e.getMessage();
                    return result;
                }
            }
            result.status = ImportStatus.OVERWRITE;
            result.success = true;
            result.message = "任务配置已覆盖（保留历史记录）";
            return result;
        }

        result.status = ImportStatus.ERROR;
        result.message = "不支持的任务类型";
        return result;
    }

    private byte[] readZipEntry(ZipInputStream zis) throws IOException {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        int len;
        while ((len = zis.read(buffer)) > 0) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    private JobPathInfo parseJobPath(String entryName) {
        if (!entryName.endsWith("/config.xml")) {
            return null;
        }

        String path = entryName.substring(0, entryName.length() - "/config.xml".length());
        if (path.isEmpty()) {
            return new JobPathInfo("", "");
        }

        int lastSlash = path.lastIndexOf('/');
        if (lastSlash == -1) {
            return new JobPathInfo("", path);
        }

        return new JobPathInfo(path.substring(0, lastSlash), path.substring(lastSlash + 1));
    }

    private static class JobPathInfo {
        String folderPath;
        String jobName;

        JobPathInfo(String folderPath, String jobName) {
            this.folderPath = folderPath;
            this.jobName = jobName;
        }
    }
}