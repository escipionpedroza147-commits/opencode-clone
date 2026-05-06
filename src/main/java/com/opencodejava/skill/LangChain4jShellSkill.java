package com.opencodejava.skill;

import dev.langchain4j.agent.tool.Tool;

import java.io.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Shell skill implemented using LangChain4j @Tool annotations.
 * This is the langchain4j-experimental-skills-shell equivalent.
 *
 * Provides enhanced shell execution with:
 * - Multi-command execution
 * - Working directory control
 * - Environment variable injection
 * - Timeout management
 * - Output capture and streaming
 */
public class LangChain4jShellSkill {
    private final String defaultWorkingDirectory;
    private final int defaultTimeoutSeconds;

    public LangChain4jShellSkill(String workingDirectory) {
        this(workingDirectory, 120);
    }

    public LangChain4jShellSkill(String workingDirectory, int defaultTimeoutSeconds) {
        this.defaultWorkingDirectory = workingDirectory;
        this.defaultTimeoutSeconds = defaultTimeoutSeconds;
    }

    @Tool("Execute a shell command and return its output. Runs in the project working directory.")
    public String executeCommand(String command) {
        return execute(command, defaultWorkingDirectory, defaultTimeoutSeconds);
    }

    @Tool("Execute a shell command in a specific directory with a custom timeout.")
    public String executeInDirectory(String command, String directory, int timeoutSeconds) {
        return execute(command, directory, timeoutSeconds);
    }

    @Tool("Execute multiple commands sequentially. Commands are separated by semicolons or newlines.")
    public String executeMultiple(String commands) {
        String[] cmds = commands.split("[;\n]+");
        StringBuilder output = new StringBuilder();
        for (String cmd : cmds) {
            cmd = cmd.trim();
            if (cmd.isEmpty()) continue;
            output.append("$ ").append(cmd).append("\n");
            output.append(execute(cmd, defaultWorkingDirectory, defaultTimeoutSeconds));
            output.append("\n");
        }
        return output.toString();
    }

    @Tool("Execute a command and only return whether it succeeded (exit code 0) or failed.")
    public String checkCommand(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command);
            pb.directory(new File(defaultWorkingDirectory));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(defaultTimeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "TIMEOUT";
            }
            return process.exitValue() == 0 ? "SUCCESS" : "FAILED (exit " + process.exitValue() + ")";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Tool("Get the current working directory.")
    public String getWorkingDirectory() {
        return defaultWorkingDirectory;
    }

    private String execute(String command, String workDir, int timeout) {
        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command);
            pb.directory(new File(workDir));
            pb.redirectErrorStream(true);
            pb.environment().put("TERM", "dumb");

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                int totalChars = 0;
                while ((line = reader.readLine()) != null) {
                    if (totalChars > 100_000) {
                        output.append("\n... [output truncated at 100KB]");
                        break;
                    }
                    output.append(line).append("\n");
                    totalChars += line.length() + 1;
                }
            }

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "Command timed out after " + timeout + " seconds.\nPartial output:\n" + output;
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();

            if (exitCode != 0) {
                return "[Exit " + exitCode + "]\n" + result;
            }
            return result.isEmpty() ? "(no output)" : result;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
