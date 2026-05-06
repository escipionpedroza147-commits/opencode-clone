package com.opencodejava.tool;

import com.opencodejava.provider.LLMProvider.ToolDefinition;
import com.opencodejava.ui.DiffRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class FileWriteTool implements Tool {

    @Override
    public String getName() { return "file_write"; }

    @Override
    public String getDescription() {
        return "Write content to a file. Creates the file if it doesn't exist, overwrites if it does. Automatically creates parent directories.";
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String filePath = (String) arguments.get("path");
        String content = (String) arguments.get("content");

        if (filePath == null || filePath.isEmpty()) {
            return ToolResult.failure("Missing required argument: path");
        }
        if (content == null) {
            return ToolResult.failure("Missing required argument: content");
        }

        Path path = Path.of(filePath).toAbsolutePath();

        try {
            // Create parent directories if needed
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            boolean existed = Files.exists(path);
            String oldContent = existed ? Files.readString(path) : "";
            Files.writeString(path, content);

            long size = Files.size(path);
            long lines = content.lines().count();

            String action = existed ? "Updated" : "Created";
            String result = String.format("%s file: %s (%d bytes, %d lines)", action, path, size, lines);

            // Show diff if file was updated
            if (existed && !oldContent.equals(content)) {
                String diff = DiffRenderer.getInstance().renderDiff(oldContent, content, path.toString());
                System.out.println(diff);
            }

            return ToolResult.success(result);
        } catch (IOException e) {
            return ToolResult.failure("Failed to write file: " + e.getMessage());
        }
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("path", Map.of("type", "string", "description", "Path to the file to write"));
        params.put("content", Map.of("type", "string", "description", "Content to write to the file"));
        return new ToolDefinition(getName(), getDescription(), params);
    }

    @Override
    public boolean isReadOnly() { return false; }
}
