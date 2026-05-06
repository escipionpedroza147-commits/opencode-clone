package com.opencodejava.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallTest {

    @Test
    void testToolCallCreation() {
        ToolCall tc = new ToolCall("id_1", "bash", Map.of("command", "ls -la"));
        assertEquals("id_1", tc.getId());
        assertEquals("bash", tc.getName());
        assertEquals(Map.of("command", "ls -la"), tc.getArguments());
    }

    @Test
    void testGetArgumentAsString() {
        ToolCall tc = new ToolCall("id_2", "file_write", Map.of("path", "/tmp/test.txt", "content", "hello"));
        assertEquals("/tmp/test.txt", tc.getArgumentAsString("path"));
        assertEquals("hello", tc.getArgumentAsString("content"));
    }

    @Test
    void testGetArgumentAsString_missing() {
        ToolCall tc = new ToolCall("id_3", "bash", Map.of("command", "ls"));
        assertNull(tc.getArgumentAsString("nonexistent"));
    }

    @Test
    void testGetArgumentAsString_numericValue() {
        ToolCall tc = new ToolCall("id_4", "bash", Map.of("timeout", 30));
        assertEquals("30", tc.getArgumentAsString("timeout"));
    }
}
