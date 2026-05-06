package com.opencodejava.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MemoryTest {

    private Memory memory;

    @BeforeEach
    void setUp() throws Exception {
        // Reset singleton for testing
        Field instanceField = Memory.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);

        memory = Memory.getInstance();
    }

    @Test
    void testRemember_withKey() {
        memory.remember("test_key", "test_value");
        assertEquals("test_value", memory.get("test_key"));
    }

    @Test
    void testRemember_withoutKey() {
        int sizeBefore = memory.size();
        memory.remember("a note without key");
        assertEquals(sizeBefore + 1, memory.size());
    }

    @Test
    void testGet_nonExistentKey() {
        assertNull(memory.get("nonexistent_key_xyz"));
    }

    @Test
    void testRemove() {
        memory.remember("to_remove", "value");
        assertTrue(memory.remove("to_remove"));
        assertNull(memory.get("to_remove"));
    }

    @Test
    void testRemove_nonExistent() {
        assertFalse(memory.remove("never_existed_12345"));
    }

    @Test
    void testGetRecent() {
        memory.remember("recent_1", "value1");
        memory.remember("recent_2", "value2");

        var recent = memory.getRecent(2);
        assertNotNull(recent);
        assertTrue(recent.size() <= 2);
    }

    @Test
    void testGetMemoryContext_empty() {
        // Clear all entries first
        var all = memory.getAll();
        for (String key : all.keySet().stream().toList()) {
            memory.remove(key);
        }
        assertEquals("", memory.getMemoryContext(5));
    }

    @Test
    void testFormatAll_empty() {
        // Clear all
        var all = memory.getAll();
        for (String key : all.keySet().stream().toList()) {
            memory.remove(key);
        }
        String result = memory.formatAll();
        assertTrue(result.contains("No memories stored"));
    }

    @Test
    void testFormatAll_withEntries() {
        memory.remember("fmt_key", "fmt_value");
        String result = memory.formatAll();
        assertTrue(result.contains("fmt_key"));
        assertTrue(result.contains("fmt_value"));
    }
}
