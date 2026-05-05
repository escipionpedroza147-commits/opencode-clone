package com.opencodejava.tool;

import com.opencodejava.provider.LLMProvider.ToolDefinition;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Git integration tool for version control operations.
 * Supports status, diff, commit, log, and branch operations.
 */
public class GitTool implements Tool {
    private final String workingDirectory;

    public GitTool(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String getName() { return "git"; }

    @Override
    public String getDescription() {
        return "Execute git commands for version control. Supports: status, diff, log, add, commit, branch, checkout, stash. " +
                "Use this to track changes, review diffs, and manage commits.";
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String subcommand = (String) arguments.get("command");
        if (subcommand == null || subcommand.isEmpty()) {
            return ToolResult.failure("Missing required argument: command (e.g., 'status', 'diff', 'log')");
        }

        // Safety: block destructive operations
        String[] parts = subcommand.trim().split("\\s+");
        String gitCmd = parts[0].toLowerCase();
        if (isDangerous(gitCmd, subcommand)) {
            return ToolResult.failure("Blocked: '" + gitCmd + "' with those flags is potentially destructive. " +
                    "Use bash tool directly if you're sure.");
        }

        String fullCommand = "git " + subcommand;

        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", fullCommand);
            pb.directory(new File(workingDirectory));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                int chars = 0;
                while ((line = reader.readLine()) != null) {
                    if (chars > 100_000) {
                        output.append("\n... [output truncated]");
                        break;
                    }
                    output.append(line).append("\n");
                    chars += line.length() + 1;
                }
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.failure("Git command timed out after 30 seconds");
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();

            if (exitCode == 0) {
                return ToolResult.success(result.isEmpty() ? "(no output)" : result);
            } else {
                return ToolResult.failure("git " + subcommand + " failed (exit " + exitCode + "):\n" + result);
            }
        } catch (IOException e) {
            return ToolResult.failure("Failed to execute git command: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.failure("Git command interrupted");
        }
    }

    private boolean isDangerous(String gitCmd, String fullCommand) {
        // Block force-push, reset --hard, clean -fd without explicit intent
        if (gitCmd.equals("push") && fullCommand.contains("--force")) return true;
        if (gitCmd.equals("reset") && fullCommand.contains("--hard")) return true;
        if (gitCmd.equals("clean") && fullCommand.contains("-f")) return true;
        return false;
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("command", Map.of("type", "string",
                "description", "Git subcommand to run (e.g., 'status', 'diff', 'log --oneline -10', 'add .', 'commit -m \"message\"')"));
        return new ToolDefinition(getName(), getDescription(), params);
    }

    @Override
    public boolean isReadOnly() { return false; }
}
