package com.siruoren.jobimportexport.engine.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ImportContext 单元测试。
 * 重点关注 Builder 校验和状态方法。
 */
class ImportContextTest {

    @Test
    @DisplayName("Builder 不允许 overwrite 和 autoRename 同时为 true")
    void testBuilderOverwriteAndAutoRenameConflict() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new ImportContext.Builder()
                        .overwrite(true)
                        .autoRename(true)
                        .build());
        assertTrue(exception.getMessage().contains("overwrite and autoRename cannot both be true"),
                "Builder 应拒绝 overwrite 和 autoRename 同时为 true");
    }

    @Test
    @DisplayName("Builder 允许 overwrite=true, autoRename=false")
    void testBuilderOverwriteOnly() {
        ImportContext ctx = new ImportContext.Builder()
                .overwrite(true)
                .autoRename(false)
                .dryRun(false)
                .build();
        assertTrue(ctx.isOverwrite());
        assertFalse(ctx.isAutoRename());
    }

    @Test
    @DisplayName("Builder 允许 autoRename=true, overwrite=false")
    void testBuilderAutoRenameOnly() {
        ImportContext ctx = new ImportContext.Builder()
                .overwrite(false)
                .autoRename(true)
                .build();
        assertTrue(ctx.isAutoRename());
        assertFalse(ctx.isOverwrite());
    }

    @Test
    @DisplayName("Builder 默认值为 false")
    void testBuilderDefaults() {
        ImportContext ctx = new ImportContext.Builder().build();
        assertFalse(ctx.isOverwrite());
        assertFalse(ctx.isAutoRename());
        assertFalse(ctx.isDryRun());
    }

    @Test
    @DisplayName("hasParentTypeError 检测父路径类型错误")
    void testHasParentTypeError() {
        ImportContext ctx = new ImportContext();
        ctx.parentTypeErrors.add("parent/path");

        assertTrue(ctx.hasParentTypeError("parent/path/child"));
        assertFalse(ctx.hasParentTypeError("other/path/child"));
        assertFalse(ctx.hasParentTypeError("parent/path")); // 不包含自身
    }

    @Test
    @DisplayName("hasParentPermissionError 检测父路径权限错误（包含自身）")
    void testHasParentPermissionError() {
        ImportContext ctx = new ImportContext();
        ctx.parentPermissionErrors.add("parent/path");

        assertTrue(ctx.hasParentPermissionError("parent/path/child"));
        assertTrue(ctx.hasParentPermissionError("parent/path")); // 包含自身
        assertFalse(ctx.hasParentPermissionError("other/path/child"));
    }

    @Test
    @DisplayName("getParentTypeErrorPath 返回错误父路径")
    void testGetParentTypeErrorPath() {
        ImportContext ctx = new ImportContext();
        ctx.parentTypeErrors.add("parent/path");

        assertEquals("parent/path", ctx.getParentTypeErrorPath("parent/path/child"));
        assertNull(ctx.getParentTypeErrorPath("other/path/child"));
    }

    @Test
    @DisplayName("isPathBlocked 检测路径和子路径")
    void testIsPathBlocked() {
        ImportContext ctx = new ImportContext();
        ctx.blockedPaths.add("blocked/path");

        assertTrue(ctx.isPathBlocked("blocked/path"));      // 精确匹配
        assertTrue(ctx.isPathBlocked("blocked/path/child")); // 子路径
        assertFalse(ctx.isPathBlocked("other/path"));        // 无关路径
    }

    @Test
    @DisplayName("block 方法设置 blocked 和 blockedReason")
    void testBlock() {
        ImportContext ctx = new ImportContext();
        ctx.block("reason text");
        assertTrue(ctx.isBlocked());
        assertEquals("reason text", ctx.getBlockedReason());
    }

    @Test
    @DisplayName("reset 方法清除 blocked 状态")
    void testReset() {
        ImportContext ctx = new ImportContext();
        ctx.block("reason");
        ctx.reset();
        assertFalse(ctx.isBlocked());
        assertNull(ctx.getBlockedReason());
    }

    @Test
    @DisplayName("旧版构造函数兼容")
    void testLegacyConstructor() {
        ImportContext ctx = new ImportContext(true, false, true, null);
        assertTrue(ctx.isDryRun());
        assertFalse(ctx.isOverwrite());
        assertTrue(ctx.isAutoRename());
    }
}
