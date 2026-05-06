package com.opencodejava.tool;

import com.opencodejava.provider.LLMProvider.ToolDefinition;
import com.opencodejava.ui.DiffRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Precise file editing tool that replaces exact text matches.
 * More surgical than file_write — preserves the rest of the file unchanged.
 * Essential for complex refactoring tasks where full file rewrites are risky.
 */
public class FileEditTool implements Tool {

    @Override
    public String getName() { return "file_edit"; }

    @Override
    public String getDescription() {
        return "Edit a file by replacing an exact text match with new text. " +
                "More precise than file_write — only changes the matched section. " +
                "The old_text must match exactly (including whitespace and newlines). " +
                "Use file_read first to see the exact content.";
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String filePath = (String) arguments.get("path");
        String oldText = (String) arguments.get("old_text");
        String newText = (String) arguments.get("new_text");

        if (filePath == null || filePath.isEmpty()) {
            return ToolResult.failure("Missing required argument: path");
        }
        if (oldText == null || oldText.isEmpty()) {
            return ToolResult.failure("Missing required argument: old_text (the exact text to find and replace)");
        }
        if (newText == null) {
            return ToolResult.failure("Missing required argument: new_text (the replacement text)");
        }

        Path path = Path.of(filePath).toAbsolutePath();
        if (!Files.exists(path)) {
            return ToolResult.failure("File not found: " + path);
        }

        try {
            String content = Files.readString(path);

            // Count occurrences
            int count = countOccurrences(content, oldText);

            if (count == 0) {
                // Provide helpful context for debugging
                String suggestion = "The old_text was not found in the file. ";
                if (oldText.contains("\r\n")) {
                    suggestion += "Try using \\n instead of \\r\\n for line endings. ";
                }
                if (oldText.startsWith(" ") || oldText.endsWith(" ")) {
                    suggestion += "Check for trailing/leading whitespace differences. ";
                }
                suggestion += "Use file_read to see the exact file content.";
                return ToolResult.failure(suggestion);
            }

            if (count > 1) {
                return ToolResult.failure("The old_text matches " + count +
                        " locations in the file. Please provide a more unique/longer text snippet " +
                        "that matches exactly one location.");
            }

            // Perform the replacement
            String newContent = content.replace(oldText, newText);
            Files.writeString(path, newContent);

            // Calculate what changed
            int oldLines = oldText.split("\n", -1).length;
            int newLines = newText.split("\n", -1).length;
            int lineDiff = newLines - oldLines;

            String summary = String.format("Edited %s: replaced %d lines with %d lines (%s%d lines)",
                    path.getFileName(), oldLines, newLines,
                    lineDiff >= 0 ? "+" : "", lineDiff);

            // Show colored diff
            String diff = DiffRenderer.getInstance().renderDiff(content, newContent, path.toString());
            System.out.println(diff);

            return ToolResult.success(summary);
        } catch (IOException e) {
            return ToolResult.failure("Failed to edit file: " + e.getMessage());
        }
    }

    private int countOccurrences(String text, String search) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(search, index)) != -1) {
            count++;
            index += search.length();
        }
        return count;
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("path", Map.of("type", "string", "description", "Path to the file to edit"));
        params.put("old_text", Map.of("type", "string",
                "description", "Exact text to find in the file (must match exactly one location)"));
        params.put("new_text", Map.of("type", "string",
                "description", "Text to replace old_text with"));
        return new ToolDefinition(getName(), getDescription(), params);
    }

    @Override
    public boolean isReadOnly() { return false; }
}
