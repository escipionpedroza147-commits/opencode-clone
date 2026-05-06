package com.opencodejava.tool;

import com.opencodejava.provider.LLMProvider.ToolDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tool for reading file contents with support for line range selection.
 * 
 * <p>This tool provides safe file reading capabilities with the following features:
 * <ul>
 *   <li>Read entire files or specific line ranges</li>
 *   <li>File size validation (1MB limit for safety)</li>
 *   <li>Path validation and security checks</li>
 *   <li>Comprehensive error handling</li>
 * </ul>
 * 
 * <p>The tool supports three reading modes:
 * <ol>
 *   <li><strong>Full file:</strong> Read entire file contents</li>
 *   <li><strong>Line range:</strong> Read specific lines using start_line and end_line</li>
 *   <li><strong>From line:</strong> Read from start_line to end of file</li>
 * </ol>
 * 
 * <h2>Parameters:</h2>
 * <ul>
 *   <li><code>path</code> (required): File path to read</li>
 *   <li><code>start_line</code> (optional): Starting line number (1-indexed)</li>
 *   <li><code>end_line</code> (optional): Ending line number (inclusive)</li>
 * </ul>
 * 
 * <h2>Safety Features:</h2>
 * <ul>
 *   <li>File size limit of 1MB to prevent memory issues</li>
 *   <li>Path validation to ensure file exists and is readable</li>
 *   <li>Line number validation to prevent invalid ranges</li>
 * </ul>
 * 
 * @author OpenCode Java Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class FileReadTool implements Tool {

    @Override
    public String getName() { return "file_read"; }

    @Override
    public String getDescription() {
        return "Read the contents of a file. Supports reading entire files or specific line ranges.";
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String filePath = (String) arguments.get("path");
        if (filePath == null || filePath.isEmpty()) {
            return ToolResult.failure("Missing required argument: path");
        }

        Path path = Path.of(filePath).toAbsolutePath();
        if (!Files.exists(path)) {
            return ToolResult.failure("File not found: " + path);
        }

        if (!Files.isRegularFile(path)) {
            return ToolResult.failure("Not a regular file: " + path);
        }

        try {
            long fileSize = Files.size(path);
            if (fileSize > 1_000_000) { // 1MB limit
                return ToolResult.failure("File too large (" + fileSize + " bytes). Max 1MB.");
            }

            var lines = Files.readAllLines(path);

            int startLine = 1;
            int endLine = lines.size();

            if (arguments.containsKey("start_line")) {
                startLine = parseIntArg(arguments.get("start_line"));
            }
            if (arguments.containsKey("end_line")) {
                endLine = parseIntArg(arguments.get("end_line"));
            }

            startLine = Math.max(1, startLine);
            endLine = Math.min(lines.size(), endLine);

            StringBuilder result = new StringBuilder();
            result.append(String.format("File: %s (%d lines total)\n", path, lines.size()));

            if (startLine > 1 || endLine < lines.size()) {
                result.append(String.format("Showing lines %d-%d:\n", startLine, endLine));
            }

            result.append("---\n");
            for (int i = startLine - 1; i < endLine; i++) {
                result.append(String.format("%4d | %s\n", i + 1, lines.get(i)));
            }

            return ToolResult.success(result.toString());
        } catch (IOException e) {
            return ToolResult.failure("Failed to read file: " + e.getMessage());
        }
    }

    private int parseIntArg(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(value.toString()); } catch (NumberFormatException e) { return 1; }
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("path", Map.of("type", "string", "description", "Path to the file to read"));
        params.put("start_line", Map.of("type", "integer", "description", "Starting line number (1-indexed, optional)"));
        params.put("end_line", Map.of("type", "integer", "description", "Ending line number (inclusive, optional)"));
        return new ToolDefinition(getName(), getDescription(), params);
    }

    @Override
    public boolean isReadOnly() { return true; }
}
