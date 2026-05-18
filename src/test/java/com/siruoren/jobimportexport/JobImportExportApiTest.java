package com.siruoren.jobimportexport;

import org.junit.jupiter.api.Test;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JobImportExportApi REST API 单元测试
 */
public class JobImportExportApiTest {

    @Test
    public void testApiInstantiation() {
        // 验证 API 类可以正常实例化
        JobImportExportApi api = new JobImportExportApi();
        assertNotNull(api);
    }

    @Test
    public void testApiInstance() {
        // 验证通过 SidebarLink 获取的 API 实例
        JobImportExportSidebarLink link = new JobImportExportSidebarLink();
        JobImportExportApi api = link.getApi();
        assertNotNull(api);
    }

    @Test
    public void testApiMethodsExist() throws Exception {
        // 验证所有 API 方法存在且可调用
        JobImportExportApi api = new JobImportExportApi();
        
        // 验证方法存在（通过反射）
        assertNotNull(api.getClass().getMethod("doList", 
            StaplerRequest.class, StaplerResponse.class));
        
        assertNotNull(api.getClass().getMethod("doExportJob", 
            StaplerRequest.class, StaplerResponse.class));
        
        assertNotNull(api.getClass().getMethod("doExportFolder", 
            StaplerRequest.class, StaplerResponse.class));
        
        assertNotNull(api.getClass().getMethod("doExportAll", 
            StaplerRequest.class, StaplerResponse.class));
        
        assertNotNull(api.getClass().getMethod("doProgress", 
            StaplerRequest.class, StaplerResponse.class));
        
        assertNotNull(api.getClass().getMethod("doUpdateJob", 
            StaplerRequest.class, StaplerResponse.class));
        
        assertNotNull(api.getClass().getMethod("doImport", 
            StaplerRequest.class, StaplerResponse.class));
        
        assertNotNull(api.getClass().getMethod("doPreview", 
            StaplerRequest.class, StaplerResponse.class));
    }

    @Test
    public void testSidebarLinkApiMethod() {
        // 验证 SidebarLink 的 getApi 方法
        JobImportExportSidebarLink sidebarLink = new JobImportExportSidebarLink();
        assertNotNull(sidebarLink.getApi());
        assertTrue(sidebarLink.getApi() instanceof JobImportExportApi);
    }

    @Test
    public void testSidebarLinkUrlName() {
        // 验证 SidebarLink URL 名称
        JobImportExportSidebarLink sidebarLink = new JobImportExportSidebarLink();
        assertEquals("jobImportExport", sidebarLink.getUrlName());
    }

    @Test
    public void testApiClassExists() {
        // 验证 API 类存在
        Class<?> apiClass = JobImportExportApi.class;
        assertNotNull(apiClass);
        assertEquals("JobImportExportApi", apiClass.getSimpleName());
    }

    @Test
    public void testSidebarLinkClassExists() {
        // 验证 SidebarLink 类存在
        Class<?> linkClass = JobImportExportSidebarLink.class;
        assertNotNull(linkClass);
        assertEquals("JobImportExportSidebarLink", linkClass.getSimpleName());
    }

    @Test
    public void testGetIndex() {
        // 验证 getIndex 方法返回自身
        JobImportExportApi api = new JobImportExportApi();
        Object result = api.getIndex();
        assertSame(api, result);
    }
}