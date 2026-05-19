package com.siruoren.jobimportexport.engine.resolver;

import com.siruoren.jobimportexport.engine.model.ImportContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RenameDAGResolver 单元测试。
 * 重点关注循环检测和边界条件。
 */
class RenameDAGResolverTest {

    private RenameDAGResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RenameDAGResolver();
    }

    // ==================== 正常路径解析 ====================

    @Test
    @DisplayName("空 renameMap 返回原始路径")
    void testEmptyRenameMap() {
        ImportContext ctx = new ImportContext();
        assertEquals("foo/bar", resolver.resolvePath("foo/bar", ctx));
    }

    @Test
    @DisplayName("null renameMap 返回原始路径")
    void testNullRenameMap() {
        ImportContext ctx = new ImportContext();
        ctx.renameMap = null;
        assertEquals("foo/bar", resolver.resolvePath("foo/bar", ctx));
    }

    @Test
    @DisplayName("精确匹配重命名")
    void testExactMatchRename() {
        Map<String, String> renameMap = new HashMap<>();
        renameMap.put("old-name", "new-name");
        assertEquals("new-name", resolver.resolvePath("old-name", renameMap));
    }

    @Test
    @DisplayName("子路径传播：父路径重命名传播到子路径")
    void testSubPathPropagation() {
        Map<String, String> renameMap = new HashMap<>();
        renameMap.put("parent", "renamed-parent");
        assertEquals("renamed-parent/child", resolver.resolvePath("parent/child", renameMap));
    }

    @Test
    @DisplayName("多级传播：A -> B, B -> C")
    void testMultiLevelPropagation() {
        Map<String, String> renameMap = new HashMap<>();
        renameMap.put("A", "B");
        renameMap.put("B", "C");
        assertEquals("C", resolver.resolvePath("A", renameMap));
    }

    @Test
    @DisplayName("无匹配路径保持不变")
    void testNoMatch() {
        Map<String, String> renameMap = new HashMap<>();
        renameMap.put("other", "renamed");
        assertEquals("foo/bar", resolver.resolvePath("foo/bar", renameMap));
    }

    // ==================== 循环检测 ====================

    @Test
    @DisplayName("简单循环 A -> B -> A 应抛出 IllegalStateException")
    void testSimpleCircularMapping() {
        Map<String, String> renameMap = new HashMap<>();
        renameMap.put("A", "B");
        renameMap.put("B", "A");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> resolver.resolvePath("A", renameMap));
        assertTrue(exception.getMessage().contains("Circular rename mapping detected"),
                "异常消息应包含循环映射提示");
    }

    @Test
    @DisplayName("路径前缀循环检测：A -> B/x, B -> A/y")
    void testPrefixCircularMapping() {
        Map<String, String> renameMap = new HashMap<>();
        renameMap.put("A", "B/x");
        renameMap.put("B", "A/y");

        // 解析 "A/child" -> "B/x/child" -> 可能再传播到 "A/y/x/child"
        // 这取决于映射顺序，但应该能检测到循环
        assertThrows(IllegalStateException.class,
                () -> resolver.resolvePath("A/child", renameMap));
    }

    @Test
    @DisplayName("自循环 A -> A 应被检测到")
    void testSelfLoop() {
        Map<String, String> renameMap = new HashMap<>();
        renameMap.put("A", "A");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> resolver.resolvePath("A", renameMap));
        assertTrue(exception.getMessage().contains("Circular rename mapping detected"),
                "自循环应被检测到");
    }

    // ==================== 路径规范化 ====================

    @Test
    @DisplayName("路径规范化：去除前导斜杠")
    void testNormalizeLeadingSlash() {
        ImportContext ctx = new ImportContext();
        assertEquals("foo/bar", resolver.resolvePath("/foo/bar", ctx));
    }

    @Test
    @DisplayName("路径规范化：合并连续斜杠")
    void testNormalizeDoubleSlashes() {
        ImportContext ctx = new ImportContext();
        assertEquals("foo/bar", resolver.resolvePath("foo//bar", ctx));
    }

    // ==================== resolvePathSingle ====================

    @Test
    @DisplayName("resolvePathSingle 只匹配一次不传播")
    void testResolvePathSingleNoPropagation() {
        Map<String, String> renameMap = new HashMap<>();
        renameMap.put("A", "B");
        renameMap.put("B", "C");
        // 只匹配 A -> B，不会再传播到 C
        assertEquals("B", resolver.resolvePathSingle("A", new ImportContext() {{
            this.renameMap = renameMap;
        }}));
    }

    // ==================== Map 参数重载 ====================

    @Test
    @DisplayName("resolvePath 直接接收 Map 参数")
    void testResolvePathWithMapParameter() {
        Map<String, String> renameMap = new HashMap<>();
        renameMap.put("old", "new");
        assertEquals("new", resolver.resolvePath("old", renameMap));
    }

    @Test
    @DisplayName("Map 参数空 Map 返回原始路径")
    void testResolvePathWithEmptyMap() {
        Map<String, String> emptyMap = new HashMap<>();
        assertEquals("foo/bar", resolver.resolvePath("foo/bar", emptyMap));
    }
}
