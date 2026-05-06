package com.opencodejava.command;

import java.io.*;
import java.util.concurrent.TimeUnit;

/**
 * A user-defined command loaded from configuration.
 * User commands are defined in opencode.json:
 *
 * {
 *   "commands": {
 *     "deploy": {
 *       "description": "Deploy the application",
 *       "script": "./deploy.sh",
 *       "timeout": 120
 *     },
 *     "test": {
 *       "description": "Run tests",
 *       "script": "mvn test",
 *       "timeout": 300
 *     }
 *   }
 * }
 *
 * Usage: /deploy [args] or /test [args]
 */
public class UserCommand implements Command {
    private final String name;
    private final String description;
    private final String script;
    private final String prompt;
    private final String workingDirectory;
    private final int timeoutSeconds;

    public UserCommand(String name, String description, String script, String prompt,
                       String workingDirectory, int timeoutSeconds) {
        this.name = name;
        this.description = description != null ? description : "User command: " + name;
        this.script = script;
        this.prompt = prompt;
        this.workingDirectory = workingDirectory;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 60;
    }

    /** Backward-compatible constructor without prompt. */
    public UserCommand(String name, String description, String script,
                       String workingDirectory, int timeoutSeconds) {
        this(name, description, script, null, workingDirectory, timeoutSeconds);
    }

    @Override
    public String getName() { return name; }

    @Override
    public String getDescription() { return description; }

    @Override
    public String getUsage() { return "/" + name + " [arguments]"; }

    /** Returns true if this is a prompt-based command (sends to the active agent). */
    public boolean isPromptCommand() { return prompt != null && !prompt.isEmpty(); }

    /** Get the prompt text for prompt-based commands. */
    public String getPrompt() { return prompt; }

    @Override
    public String execute(String[] args) {
        // Prompt-based commands return the prompt for the agent to process
        if (isPromptCommand()) {
            StringBuilder fullPrompt = new StringBuilder(prompt);
            for (String arg : args) {
                fullPrompt.append(" ").append(arg);
            }
            return "__PROMPT__" + fullPrompt;
        }

        if (script == null || script.isEmpty()) {
            return "Error: No script or prompt configured for command /" + name;
        }

        // Build command with arguments
        StringBuilder command = new StringBuilder(script);
        for (String arg : args) {
            command.append(" ").append(arg);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command.toString());
            pb.directory(new File(workingDirectory != null ? workingDirectory : System.getProperty("user.dir")));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                int chars = 0;
                while ((line = reader.readLine()) != null) {
                    if (chars > 50_000) {
                        output.append("\n... [output truncated]");
                        break;
                    }
                    output.append(line).append("\n");
                    chars += line.length() + 1;
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "⚠️ Command timed out after " + timeoutSeconds + " seconds.\n" +
                        "Partial output:\n" + output;
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();

            if (exitCode == 0) {
                return result.isEmpty() ? "✓ Command completed successfully." : "✓ " + result;
            } else {
                return "✗ Command failed (exit " + exitCode + "):\n" + result;
            }
        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }
}
