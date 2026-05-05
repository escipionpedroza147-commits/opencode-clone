package com.opencodejava.tool;

import com.opencodejava.provider.LLMProvider.ToolDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

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
