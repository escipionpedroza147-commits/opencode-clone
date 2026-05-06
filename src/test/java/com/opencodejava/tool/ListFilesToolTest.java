package com.opencodejava.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ListFilesToolTest {

    @TempDir
    Path tempDir;
    private ListFilesTool tool;

    @BeforeEach
    void setUp() throws IOException {
        tool = new ListFilesTool(tempDir.toString());
        // Create test structure
        Files.createDirectories(tempDir.resolve("src/main"));
        Files.writeString(tempDir.resolve("src/main/App.java"), "class App {}");
        Files.writeString(tempDir.resolve("src/main/Utils.java"), "class Utils {}");
        Files.writeString(tempDir.resolve("README.md"), "# Test");
        Files.writeString(tempDir.resolve("pom.xml"), "<project/>");
    }

    @Test
    void testListFiles_default() {
        Tool.ToolResult result = tool.execute(Map.of("path", tempDir.toString()));
        assertTrue(result.success());
        assertTrue(result.output().contains("src/"));
        assertTrue(result.output().contains("README.md"));
    }

    @Test
    void testListFiles_nonExistentDir() {
        Tool.ToolResult result = tool.execute(Map.of("path", "/nonexistent/dir/xyz"));
        assertFalse(result.success());
        assertTrue(result.getContent().contains("not found"));
    }

    @Test
    void testListFiles_globPattern() {
        Tool.ToolResult result = tool.execute(Map.of(
                "path", tempDir.toString(),
                "glob", "*.java"
        ));
        assertTrue(result.success());
        assertTrue(result.output().contains("App.java"));
        assertTrue(result.output().contains("Utils.java"));
        assertFalse(result.output().contains("README.md"));
    }

    @Test
    void testListFiles_globNoMatch() {
        Tool.ToolResult result = tool.execute(Map.of(
                "path", tempDir.toString(),
                "glob", "*.xyz"
        ));
        assertTrue(result.success());
        assertTrue(result.output().contains("No files matching"));
    }

    @Test
    void testListFiles_isReadOnly() {
        assertTrue(tool.isReadOnly());
    }

    @Test
    void testToolDefinition() {
        var def = tool.getDefinition();
        assertEquals("list_files", def.name());
        assertTrue(def.parameters().containsKey("path"));
        assertTrue(def.parameters().containsKey("depth"));
        assertTrue(def.parameters().containsKey("glob"));
    }
}
