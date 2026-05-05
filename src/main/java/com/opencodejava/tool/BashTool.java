package com.opencodejava.tool;

import com.opencodejava.provider.LLMProvider.ToolDefinition;

import java.io.*;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BashTool implements Tool {
    private static final int DEFAULT_TIMEOUT_SECONDS = 60;
    private static final int MAX_OUTPUT_CHARS = 50000;

    private final String workingDirectory;

    public BashTool(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String getName() { return "bash"; }

    @Override
    public String getDescription() {
        return "Execute a bash command and return its output. Commands run in the project working directory.";
    }

    @Override
    public ToolResult execute(Map<String, Object> arguments) {
        String command = (String) arguments.get("command");
        if (command == null || command.isEmpty()) {
            return ToolResult.failure("Missing required argument: command");
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
            if (!finished) {
                process.destroyForcibly();
                return ToolResult.failure("Command timed out after " + timeout + " seconds");
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();

            if (exitCode == 0) {
                return ToolResult.success(result.isEmpty() ? "(no output)" : result);
            } else {
                return ToolResult.failure("Exit code " + exitCode + ":\n" + result);
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
        return new ToolDefinition(getName(), getDescription(), params);
    }

    @Override
    public boolean isReadOnly() { return false; }
}
