package com.siruoren.jobimportexport.service;

import com.siruoren.jobimportexport.Messages;
import hudson.model.AbstractItem;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TopLevelItem;
import jenkins.model.Jenkins;
import jenkins.model.ModifiableTopLevelItemGroup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public class JobService {

    private static final Pattern INVALID_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_\\-]*$");

    public boolean isFolderConfig(byte[] xmlBytes) {
        String xml = new String(xmlBytes, StandardCharsets.UTF_8);
        return xml.contains("com.cloudbees.hudson.plugins.folder.Folder");
    }

    public boolean isSpecialFolder(AbstractItem item) {
        String className = item.getClass().getName();
        return className.contains("ComputedFolder") ||
               className.contains("MultiBranch") ||
               className.contains("OrganizationFolder");
    }

    public boolean createJob(ItemGroup<?> targetGroup, String jobName, InputStream xmlStream) throws IOException {
        if (!(targetGroup instanceof ModifiableTopLevelItemGroup)) {
            throw new IOException(Messages.JobService_dirNotSupportCreateJob());
        }

        TopLevelItem item = ((ModifiableTopLevelItemGroup) targetGroup)
                .createProjectFromXML(jobName, xmlStream);
        item.save();
        return true;
    }

    public void deleteJob(Item item) throws IOException {
        if (item != null) {
            try {
                item.delete();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(Messages.JobService_deleteJobInterrupted(e.getMessage()), e);
            }
        }
    }

    public String buildTargetFullName(ItemGroup<?> baseGroup, String folderPath, String jobName) {
        StringBuilder sb = new StringBuilder();
        if (baseGroup instanceof AbstractItem) {
            String baseFullName = ((AbstractItem) baseGroup).getFullName();
            if (baseFullName != null && !baseFullName.isEmpty()) {
                sb.append(baseFullName).append("/");
            }
        }
        if (folderPath != null && !folderPath.trim().isEmpty()) {
            sb.append(folderPath.replace("\\", "/").replaceAll("^/+", "").replaceAll("/+", "/"));
        }
        if (jobName != null && !jobName.isEmpty()) {
            if (sb.length() > 0 && !folderPath.endsWith("/")) {
                sb.append("/");
            }
            sb.append(jobName);
        }
        return sb.toString();
    }

    public String generateUniqueJobName(ItemGroup<?> baseGroup, String folderPath, String baseName) {
        String jobName = baseName;
        int counter = 1;
        while (Jenkins.get().getItemByFullName(buildTargetFullName(baseGroup, folderPath, jobName)) != null) {
            jobName = baseName + "_" + counter;
            counter++;
        }
        return jobName;
    }

    public String sanitizeJobName(String name) {
        if (name == null) {
            return null;
        }
        return name.trim();
    }

    public boolean validateJobName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return INVALID_NAME_PATTERN.matcher(name).matches();
    }

    public boolean isCreatableGroup(ItemGroup<?> g) {
        return g instanceof ModifiableTopLevelItemGroup
                && !(g instanceof AbstractItem
                        && g.getClass().getName().contains("ComputedFolder"));
    }
}