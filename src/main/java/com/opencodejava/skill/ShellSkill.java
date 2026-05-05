package com.opencodejava.skill;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Shell skill - provides enhanced shell execution capabilities.
 * Inspired by langchain4j-experimental-skills-shell.
 */
public class ShellSkill implements Skill {
    private final String workingDirectory;

    public ShellSkill(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @Override
    public String getName() { return "shell"; }

    @Override
    public String getDescription() {
        return "Enhanced shell execution with environment control, piping, and multi-command support.";
    }

    @Override
    public SkillResult execute(Map<String, Object> parameters) {
        String command = (String) parameters.get("command");
        if (command == null || command.isEmpty()) {
            return SkillResult.failure("No command provided");
        }

        String cwd = parameters.containsKey("cwd") ?
                (String) parameters.get("cwd") : workingDirectory;
        int timeout = parameters.containsKey("timeout") ?
                Integer.parseInt(parameters.get("timeout").toString()) : 120;

        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command);
            pb.directory(new File(cwd));
            pb.redirectErrorStream(true);

            // Add any environment variables
            if (parameters.containsKey("env") && parameters.get("env") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> envVars = (Map<String, String>) parameters.get("env");
                pb.environment().putAll(envVars);
            }

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return SkillResult.failure("Command timed out after " + timeout + " seconds");
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();

            if (exitCode == 0) {
                return SkillResult.success(result.isEmpty() ? "(command completed with no output)" : result);
            } else {
                return SkillResult.failure("Exit code " + exitCode + ":\n" + result);
            }
        } catch (Exception e) {
            return SkillResult.failure("Shell execution failed: " + e.getMessage());
        }
    }

    @Override
    public String getSystemPromptAddition() {
        return "You have access to an enhanced shell skill that supports environment variables, " +
                "custom working directories, and extended timeouts for long-running commands.";
    }

    @Override
    public boolean isAvailable() {
        return true; // Shell is always available on Unix-like systems
    }
}
