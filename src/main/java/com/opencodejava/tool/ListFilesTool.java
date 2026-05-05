package com.opencodejava.tool;

import com.opencodejava.provider.LLMProvider.ToolDefinition;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 * Lists files and directories in a tree structure.
 * Essential for understanding project layout before making changes.
 */
public class ListFilesTool implements Tool {
    private final String workingDirectory;
    private static final Set<String> IGNORED_DIRS = Set.of(
            "node_modules", ".git", "target", "build", "dist", ".idea",
            ".vscode", "__pycache__", ".gradle", "vendor", ".next"
    );
    private static final int MAX_FILES = 500;
    private static final int MAX_DEPTH_DEFAULT = 4;

    public ListFilesTool(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String getName() { return "list_files"; }

    @Override
    public String getDescription() {
        return "List files and directories in a tree structure. " +
                "Useful for understanding project layout before reading or editing files. " +
                "Automatically skips common non-essential directories (node_modules, .git, target, etc.)";
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String dirPath = arguments.containsKey("path") ?
                (String) arguments.get("path") : workingDirectory;
        int maxDepth = arguments.containsKey("depth") ?
                parseIntArg(arguments.get("depth")) : MAX_DEPTH_DEFAULT;

        Path startPath = Path.of(dirPath).toAbsolutePath();
        if (!Files.exists(startPath)) {
            return ToolResult.failure("Directory not found: " + startPath);
        }
        if (!Files.isDirectory(startPath)) {
            return ToolResult.failure("Not a directory: " + startPath);
        }

        StringBuilder tree = new StringBuilder();
        tree.append(startPath.getFileName()).append("/\n");
        List<Integer> fileCount = new ArrayList<>();
        fileCount.add(0);

        try {
            buildTree(startPath, tree, "", maxDepth, 0, fileCount);
        } catch (IOException e) {
            return ToolResult.failure("Failed to list files: " + e.getMessage());
        }

        if (fileCount.get(0) >= MAX_FILES) {
            tree.append("\n... (listing capped at ").append(MAX_FILES).append(" entries)");
        }

        tree.append("\n\nTotal: ").append(fileCount.get(0)).append(" files/directories");
        return ToolResult.success(tree.toString());
    }

    private void buildTree(Path dir, StringBuilder tree, String prefix,
                           int maxDepth, int currentDepth, List<Integer> count) throws IOException {
        if (currentDepth >= maxDepth || count.get(0) >= MAX_FILES) return;

        List<Path> entries = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            stream.sorted((a, b) -> {
                // Directories first, then alphabetical
                boolean aDir = Files.isDirectory(a);
                boolean bDir = Files.isDirectory(b);
                if (aDir != bDir) return aDir ? -1 : 1;
                return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
            }).forEach(entries::add);
        }

        for (int i = 0; i < entries.size() && count.get(0) < MAX_FILES; i++) {
            Path entry = entries.get(i);
            boolean isLast = (i == entries.size() - 1);
            String connector = isLast ? "└── " : "├── ";
            String childPrefix = isLast ? "    " : "│   ";
            String name = entry.getFileName().toString();

            if (Files.isDirectory(entry)) {
                if (IGNORED_DIRS.contains(name)) {
                    tree.append(prefix).append(connector).append(name).append("/ (skipped)\n");
                    count.set(0, count.get(0) + 1);
                    continue;
                }
                tree.append(prefix).append(connector).append(name).append("/\n");
                count.set(0, count.get(0) + 1);
                buildTree(entry, tree, prefix + childPrefix, maxDepth, currentDepth + 1, count);
            } else {
                long size = Files.size(entry);
                tree.append(prefix).append(connector).append(name);
                if (size > 100_000) {
                    tree.append(" (").append(formatSize(size)).append(")");
                }
                tree.append("\n");
                count.set(0, count.get(0) + 1);
            }
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
    }

    private int parseIntArg(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(value.toString()); } catch (NumberFormatException e) { return MAX_DEPTH_DEFAULT; }
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("path", Map.of("type", "string",
                "description", "Directory path to list (default: project root)"));
        params.put("depth", Map.of("type", "integer",
                "description", "Maximum depth to traverse (default: 4)"));
        return new ToolDefinition(getName(), getDescription(), params);
    }

    @Override
    public boolean isReadOnly() { return true; }
}
