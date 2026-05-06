package com.opencodejava.tool;

import com.opencodejava.core.Config;
import com.opencodejava.provider.LLMProvider.ToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry(Config.getInstance());
    }

    @Test
    void testDefaultToolsRegistered() {
        assertTrue(registry.hasTool("bash"));
        assertTrue(registry.hasTool("file_read"));
        assertTrue(registry.hasTool("file_write"));
        assertTrue(registry.hasTool("file_edit"));
        assertTrue(registry.hasTool("search"));
        assertTrue(registry.hasTool("list_files"));
        assertTrue(registry.hasTool("git"));
    }

    @Test
    void testGetTool_existing() {
        Tool tool = registry.getTool("bash");
        assertNotNull(tool);
        assertEquals("bash", tool.getName());
    }

    @Test
    void testGetTool_nonExistent() {
        Tool tool = registry.getTool("nonexistent_tool");
        assertNull(tool);
    }

    @Test
    void testHasTool() {
        assertTrue(registry.hasTool("bash"));
        assertFalse(registry.hasTool("nonexistent"));
    }

    @Test
    void testGetAllTools() {
        var tools = registry.getAllTools();
        assertNotNull(tools);
        assertTrue(tools.size() >= 7);
    }

    @Test
    void testGetToolDefinitions() {
        List<ToolDefinition> defs = registry.getToolDefinitions(List.of("bash", "file_read"));
        assertEquals(2, defs.size());
        assertEquals("bash", defs.get(0).name());
        assertEquals("file_read", defs.get(1).name());
    }

    @Test
    void testGetReadOnlyToolDefinitions() {
        List<ToolDefinition> readOnly = registry.getReadOnlyToolDefinitions();
        assertNotNull(readOnly);
        // file_read, search, list_files should be read-only
        assertTrue(readOnly.stream().anyMatch(d -> d.name().equals("file_read")));
        assertTrue(readOnly.stream().anyMatch(d -> d.name().equals("list_files")));
    }

    @Test
    void testExecuteTool_unknownTool() {
        Tool.ToolResult result = registry.executeTool("fake_tool", Map.of());
        assertFalse(result.success());
        assertTrue(result.getContent().contains("Unknown tool"));
    }

    @Test
    void testRegisterCustomTool() {
        Tool customTool = new Tool() {
            @Override public String getName() { return "custom_test"; }
            @Override public String getDescription() { return "Test tool"; }
            @Override public ToolResult execute(Map<String, Object> arguments) { return ToolResult.success("ok"); }
            @Override public ToolDefinition getDefinition() { return new ToolDefinition("custom_test", "Test", Map.of()); }
            @Override public boolean isReadOnly() { return true; }
        };
        registry.register(customTool);
        assertTrue(registry.hasTool("custom_test"));
        assertEquals("ok", registry.executeTool("custom_test", Map.of()).output());
    }
}
