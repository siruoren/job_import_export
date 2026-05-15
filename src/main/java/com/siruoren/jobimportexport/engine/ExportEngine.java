package com.siruoren.jobimportexport.engine;

import com.siruoren.jobimportexport.engine.model.ExportResult;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.security.AccessControlled;
import jenkins.model.Jenkins;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExportEngine {

    private List<ExportResult> results = new ArrayList<>();
    private ZipOutputStream zos;
    private ByteArrayOutputStream baos;

    public ExportResult exportAll(OutputStream outputStream) {
        results.clear();
        baos = null;
        zos = null;

        try {
            baos = new ByteArrayOutputStream();
            zos = new ZipOutputStream(baos);

            Jenkins jenkins = Jenkins.get();
            collectItems(jenkins, "", jenkins);

            zos.finish();
            zos.flush();

            byte[] zipData = baos.toByteArray();
            outputStream.write(zipData);
            outputStream.flush();

            return buildSummary();
        } catch (Exception e) {
            return new ExportResult("", "", "ERROR", "导出失败: " + e.getMessage());
        } finally {
            try {
                if (zos != null) zos.close();
                if (baos != null) baos.close();
            } catch (Exception ignored) {
            }
        }
    }

    public ExportResult exportFromGroup(ItemGroup<?> group, OutputStream outputStream, boolean includeCurrentConfig) {
        results.clear();
        baos = null;
        zos = null;

        try {
            baos = new ByteArrayOutputStream();
            zos = new ZipOutputStream(baos);

            String basePath = "";
            boolean isRoot = (group instanceof Jenkins);
            if (group instanceof AbstractItem) {
                basePath = ((AbstractItem) group).getFullName();
            }

            if (!isRoot && group instanceof AbstractItem && includeCurrentConfig) {
                addCurrentFolderConfigToZipRoot((AbstractItem) group);
                results.add(new ExportResult(
                    ((AbstractItem) group).getName(),
                    ((AbstractItem) group).getFullName(),
                    "EXPORTED",
                    "已导出当前目录任务配置"
                ));
            }

            String exportBasePath = includeCurrentConfig ? basePath : "";
            collectItems(group, exportBasePath, group);

            zos.finish();
            zos.flush();

            byte[] zipData = baos.toByteArray();
            outputStream.write(zipData);
            outputStream.flush();

            return buildSummary();
        } catch (Exception e) {
            return new ExportResult("", "", "ERROR", "导出失败: " + e.getMessage());
        } finally {
            try {
                if (zos != null) zos.close();
                if (baos != null) baos.close();
            } catch (Exception ignored) {
            }
        }
    }

    public ExportResult exportFromGroup(ItemGroup<?> group, OutputStream outputStream) {
        return exportFromGroup(group, outputStream, true);
    }

    private void addCurrentFolderConfigToZipRoot(AbstractItem folderItem) throws Exception {
        Path configFile = Paths.get(folderItem.getRootDir().getAbsolutePath(), "config.xml");
        if (Files.exists(configFile)) {
            byte[] configBytes = Files.readAllBytes(configFile);
            String entryPath = folderItem.getName() + "/config.xml";
            ZipEntry entry = new ZipEntry(entryPath);
            zos.putNextEntry(entry);
            zos.write(configBytes);
            zos.closeEntry();
        }
    }

    private void collectItems(ItemGroup<?> group, String basePath, ItemGroup<?> rootGroup) {
        if (group == null) return;

        for (Item item : group.getItems()) {
            if (item == null) continue;

            String itemName = item.getName();
            String relativePath = basePath.isEmpty() ? itemName : basePath + "/" + itemName;

            if (item instanceof AccessControlled) {
                if (!((AccessControlled) item).hasPermission(Item.READ)) {
                    results.add(new ExportResult(
                        relativePath,
                        item.getFullName(),
                        "SKIPPED",
                        "无权限导出，跳过"
                    ));
                    continue;
                }
            }

            try {
                if (item instanceof ItemGroup) {
                    addFolderToZip(relativePath, item);
                    collectItems((ItemGroup<?>) item, relativePath, rootGroup);
                } else if (item instanceof Job) {
                    addJobToZip(relativePath, item);
                }
            } catch (Exception e) {
                results.add(new ExportResult(
                    relativePath,
                    item.getFullName(),
                    "ERROR",
                    "导出失败: " + e.getMessage()
                ));
            }
        }
    }

    private void addFolderToZip(String relativePath, Item item) throws Exception {
        Path configFile = Paths.get(item.getRootDir().getAbsolutePath(), "config.xml");
        if (Files.exists(configFile)) {
            byte[] configBytes = Files.readAllBytes(configFile);
            String configXml = new String(configBytes, "UTF-8");
            if (configXml.contains("com.cloudbees.hudson.plugins.folder.Folder")) {
                ZipEntry entry = new ZipEntry(relativePath + "/config.xml");
                zos.putNextEntry(entry);
                zos.write(configBytes);
                zos.closeEntry();

                results.add(new ExportResult(
                    relativePath,
                    item.getFullName(),
                    "EXPORTED",
                    "已导出目录任务配置"
                ));
            } else {
                results.add(new ExportResult(
                    relativePath,
                    item.getFullName(),
                    "EXPORTED",
                    "已导出目录"
                ));
            }
        } else {
            results.add(new ExportResult(
                relativePath,
                item.getFullName(),
                "EXPORTED",
                "已导出目录"
            ));
        }
    }

    private void addJobToZip(String relativePath, Item item) throws Exception {
        Path configFile = Paths.get(item.getRootDir().getAbsolutePath(), "config.xml");
        if (Files.exists(configFile)) {
            byte[] configBytes = Files.readAllBytes(configFile);

            ZipEntry entry = new ZipEntry(relativePath + "/config.xml");
            zos.putNextEntry(entry);
            zos.write(configBytes);
            zos.closeEntry();

            results.add(new ExportResult(
                relativePath,
                item.getFullName(),
                "EXPORTED",
                "已导出任务配置"
            ));
        } else {
            results.add(new ExportResult(
                relativePath,
                item.getFullName(),
                "ERROR",
                "配置文件不存在"
            ));
        }
    }

    private ExportResult buildSummary() {
        int exported = 0;
        int skipped = 0;
        int errors = 0;
        for (ExportResult r : results) {
            if ("EXPORTED".equals(r.status)) exported++;
            else if ("SKIPPED".equals(r.status)) skipped++;
            else errors++;
        }
        return new ExportResult("", "", "SUMMARY",
            "导出完成: 成功 " + exported + " 个, 跳过 " + skipped + " 个, 失败 " + errors + " 个");
    }

    public List<ExportResult> getResults() {
        return results;
    }
}