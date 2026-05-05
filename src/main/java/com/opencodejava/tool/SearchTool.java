package com.opencodejava.tool;

import com.opencodejava.provider.LLMProvider.ToolDefinition;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class SearchTool implements Tool {
    private static final int MAX_RESULTS = 50;
    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", "node_modules", "target", "build", ".idea", ".vscode",
            "__pycache__", ".gradle", "dist", "vendor", ".next"
    );

    private final String workingDirectory;

    public SearchTool(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String getName() { return "search"; }

    @Override
    public String getDescription() {
        return "Search for a pattern in files within the project. Uses regex matching. Returns matching lines with file paths and line numbers.";
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String patternStr = (String) arguments.get("pattern");
        if (patternStr == null || patternStr.isEmpty()) {
            return ToolResult.failure("Missing required argument: pattern");
        }

        String searchPath = arguments.containsKey("path") ?
                (String) arguments.get("path") : workingDirectory;
        String filePattern = arguments.containsKey("file_pattern") ?
                (String) arguments.get("file_pattern") : null;

        Pattern pattern;
        try {
            pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            return ToolResult.failure("Invalid regex pattern: " + e.getMessage());
        }

        Pattern fileFilter = null;
        if (filePattern != null) {
            try {
                String regex = filePattern.replace(".", "\\.").replace("*", ".*");
                fileFilter = Pattern.compile(regex);
            } catch (PatternSyntaxException e) {
                return ToolResult.failure("Invalid file pattern: " + e.getMessage());
            }
        }

        Path startPath = Path.of(searchPath).toAbsolutePath();
        if (!Files.exists(startPath)) {
            return ToolResult.failure("Path does not exist: " + startPath);
        }

        List<String> results = new ArrayList<>();
        Pattern finalFileFilter = fileFilter;

        try {
            Files.walkFileTree(startPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();
                    if (IGNORED_DIRS.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (results.size() >= MAX_RESULTS) {
                        return FileVisitResult.TERMINATE;
                    }

                    if (!Files.isRegularFile(file)) return FileVisitResult.CONTINUE;

                    String fileName = file.getFileName().toString();
                    if (finalFileFilter != null && !finalFileFilter.matcher(fileName).matches()) {
                        return FileVisitResult.CONTINUE;
                    }

                    // Skip binary files
                    if (isBinaryExtension(fileName)) return FileVisitResult.CONTINUE;

                    try {
                        if (Files.size(file) > 500_000) return FileVisitResult.CONTINUE; // Skip large files

                        List<String> lines = Files.readAllLines(file);
                        for (int i = 0; i < lines.size() && results.size() < MAX_RESULTS; i++) {
                            Matcher matcher = pattern.matcher(lines.get(i));
                            if (matcher.find()) {
                                String relativePath = startPath.relativize(file).toString();
                                String line = lines.get(i).trim();
                                if (line.length() > 200) line = line.substring(0, 200) + "...";
                                results.add(String.format("%s:%d: %s", relativePath, i + 1, line));
                            }
                        }
                    } catch (IOException e) {
                        // Skip unreadable files
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return ToolResult.failure("Search failed: " + e.getMessage());
        }

        if (results.isEmpty()) {
            return ToolResult.success("No matches found for pattern: " + patternStr);
        }

        StringBuilder output = new StringBuilder();
        output.append(String.format("Found %d match%s for '%s':\n\n",
                results.size(), results.size() == 1 ? "" : "es", patternStr));
        for (String result : results) {
            output.append(result).append("\n");
        }
        if (results.size() >= MAX_RESULTS) {
            output.append("\n... (results capped at ").append(MAX_RESULTS).append(")");
        }

        return ToolResult.success(output.toString());
    }

    private boolean isBinaryExtension(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".class") || lower.endsWith(".jar") || lower.endsWith(".zip")
                || lower.endsWith(".tar") || lower.endsWith(".gz") || lower.endsWith(".png")
                || lower.endsWith(".jpg") || lower.endsWith(".gif") || lower.endsWith(".pdf")
                || lower.endsWith(".exe") || lower.endsWith(".dll") || lower.endsWith(".so")
                || lower.endsWith(".o") || lower.endsWith(".a") || lower.endsWith(".ico");
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("pattern", Map.of("type", "string", "description", "Regex pattern to search for"));
        params.put("path", Map.of("type", "string", "description", "Directory to search in (default: project root)"));
        params.put("file_pattern", Map.of("type", "string", "description", "File name pattern filter (e.g., *.java)"));
        return new ToolDefinition(getName(), getDescription(), params);
    }

    @Override
    public boolean isReadOnly() { return true; }
}
