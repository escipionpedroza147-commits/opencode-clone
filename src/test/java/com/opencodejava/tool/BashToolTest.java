package com.opencodejava.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BashToolTest {

    private BashTool bashTool;

    @BeforeEach
    void setUp() {
        bashTool = new BashTool(System.getProperty("user.dir"));
    }

    @Test
    void testExecute_simpleCommand() {
        Tool.ToolResult result = bashTool.execute(Map.of("command", "echo hello"));
        assertTrue(result.success());
        assertTrue(result.output().contains("hello"));
    }

    @Test
    void testExecute_missingCommand() {
        Tool.ToolResult result = bashTool.execute(Map.of());
        assertFalse(result.success());
        assertTrue(result.getContent().contains("Missing required argument"));
    }

    @Test
    void testExecute_failingCommand() {
        Tool.ToolResult result = bashTool.execute(Map.of("command", "false"));
        assertFalse(result.success());
    }

    @Test
    void testIsDestructiveCommand_rm() {
        assertTrue(BashTool.isDestructiveCommand("rm -rf /tmp/test"));
        assertTrue(BashTool.isDestructiveCommand("rm file.txt"));
        assertTrue(BashTool.isDestructiveCommand("rmdir mydir"));
    }

    @Test
    void testIsDestructiveCommand_safe() {
        assertFalse(BashTool.isDestructiveCommand("ls -la"));
        assertFalse(BashTool.isDestructiveCommand("echo hello"));
        assertFalse(BashTool.isDestructiveCommand("cat file.txt"));
    }

    @Test
    void testDestructiveCommand_blocked() {
        Tool.ToolResult result = bashTool.execute(Map.of("command", "rm -rf /tmp/test_dir_xyz"));
        assertFalse(result.success());
        assertTrue(result.getContent().contains("DESTRUCTIVE"));
    }

    @Test
    void testDestructiveCommand_confirmedAllowed() {
        // This won't actually delete since directory doesn't exist, but it should attempt to run
        Tool.ToolResult result = bashTool.execute(Map.of(
                "command", "rm -f /tmp/nonexistent_file_xyz_12345",
                "confirmed", "true"
        ));
        // The command itself succeeds (rm -f doesn't error on nonexistent)
        assertTrue(result.success());
    }

    @Test
    void testToolDefinition() {
        var def = bashTool.getDefinition();
        assertEquals("bash", def.name());
        assertNotNull(def.description());
        assertTrue(def.parameters().containsKey("command"));
        assertTrue(def.parameters().containsKey("timeout"));
        assertTrue(def.parameters().containsKey("confirmed"));
    }

    @Test
    void testIsNotReadOnly() {
        assertFalse(bashTool.isReadOnly());
    }
}
