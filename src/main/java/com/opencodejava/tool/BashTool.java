package com.opencodejava.tool;

import com.opencodejava.provider.LLMProvider.ToolDefinition;

import java.io.*;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class BashTool implements Tool {
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int MAX_OUTPUT_CHARS = 50000;
    private static final Set<String> DESTRUCTIVE_COMMANDS = Set.of(
            "rm", "rmdir", "rm -rf", "rm -r", "shred", "mkfs", "dd",
            "format", "fdisk", "wipefs"
    );
    private static final Pattern DESTRUCTIVE_PATTERN = Pattern.compile(
            "\\b(rm\\s+(-[rfRF]+\\s+)?|rmdir\\s+|shred\\s+|mkfs|dd\\s+|format\\s+)");

    private final String workingDirectory;

    public BashTool(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String getName() { return "bash"; }

    @Override
    public String getDescription() {
        return "Execute a bash command and return its output. Commands run in the project working directory. " +
                "Destructive commands (rm, rmdir, etc.) require explicit confirmation.";
    }

    /**
     * Check if a command is potentially destructive.
     */
    public static boolean isDestructiveCommand(String command) {
        return DESTRUCTIVE_PATTERN.matcher(command).find();
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String command = (String) arguments.get("command");
        if (command == null || command.isEmpty()) {
            return ToolResult.failure("Missing required argument: command");
        }

        // Check for destructive commands
        if (isDestructiveCommand(command)) {
            Object confirmed = arguments.get("confirmed");
            if (confirmed == null || !"true".equals(confirmed.toString())) {
                return ToolResult.failure(
                        "⚠️  DESTRUCTIVE COMMAND DETECTED: " + command + "\n" +
                        "This command could permanently delete data. " +
                        "Re-run with confirmed=true to proceed.");
            }
        }

        int timeout = DEFAULT_TIMEOUT_SECONDS;
        if (arguments.containsKey("timeout")) {
            try {
                timeout = Integer.parseInt(arguments.get("timeout").toString());
            } catch (NumberFormatException ignored) {}
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command);
            pb.directory(new File(workingDirectory));
            pb.redirectErrorStream(true);
            pb.environment().put("TERM", "dumb");

            long startTime = System.currentTimeMillis();
            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                int totalChars = 0;
                while ((line = reader.readLine()) != null) {
                    if (totalChars + line.length() > MAX_OUTPUT_CHARS) {
                        output.append("\n... [output truncated at ").append(MAX_OUTPUT_CHARS).append(" chars]");
                        break;
                    }
                    output.append(line).append("\n");
                    totalChars += line.length() + 1;
                }
            }

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;

            if (!finished) {
                process.destroyForcibly();
                return ToolResult.failure("Command timed out after " + timeout + " seconds");
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();

            // Add elapsed time info for long-running commands
            String timeInfo = elapsed > 2 ? " [" + elapsed + "s]" : "";

            if (exitCode == 0) {
                return ToolResult.success((result.isEmpty() ? "(no output)" : result) + timeInfo);
            } else {
                return ToolResult.failure("Exit code " + exitCode + ":\n" + result + timeInfo);
            }
        } catch (IOException e) {
            return ToolResult.failure("Failed to execute command: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ToolResult.failure("Command interrupted");
        }
    }

    @Override
    public ToolDefinition getDefinition() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("command", Map.of("type", "string", "description", "The bash command to execute"));
        params.put("timeout", Map.of("type", "integer", "description", "Timeout in seconds (default: 60)"));
        params.put("confirmed", Map.of("type", "string", "description", "Set to 'true' to confirm destructive commands (rm, rmdir, etc.)"));
        return new ToolDefinition(getName(), getDescription(), params);
    }

    @Override
    public boolean isReadOnly() { return false; }
}
