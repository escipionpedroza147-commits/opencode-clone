package com.opencodejava.skill;

import java.io.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A user-defined skill loaded from configuration.
 * User skills are defined in opencode.json and can be:
 * - Script-based: Execute a shell script with parameters
 * - Prompt-based: Add specific capabilities via system prompt enhancement
 *
 * Configuration format in opencode.json:
 * {
 *   "skills": {
 *     "my-skill": {
 *       "description": "What this skill does",
 *       "type": "script",
 *       "script": "/path/to/script.sh",
 *       "system_prompt": "Additional context for the agent",
 *       "timeout": 60
 *     }
 *   }
 * }
 */
public class UserSkill implements Skill {
    private final String name;
    private final String description;
    private final String type; // "script" or "prompt"
    private final String script;
    private final String systemPromptText;
    private final String workingDirectory;
    private final int timeoutSeconds;

    public UserSkill(String name, String description, String type, String script,
                     String systemPromptText, String workingDirectory, int timeoutSeconds) {
        this.name = name;
        this.description = description;
        this.type = type != null ? type : "prompt";
        this.script = script;
        this.systemPromptText = systemPromptText;
        this.workingDirectory = workingDirectory;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 60;
    }

    @Override
    public String getName() { return name; }

    @Override
    public String getDescription() { return description; }

    @Override
    public SkillResult execute(Map<String, Object> parameters) {
        if ("prompt".equals(type)) {
            return SkillResult.success("Skill '" + name + "' is prompt-based. " +
                    "Its capabilities are available through the system prompt.");
        }

        if (script == null || script.isEmpty()) {
            return SkillResult.failure("No script configured for skill: " + name);
        }

        // Build command with parameters
        StringBuilder command = new StringBuilder(script);
        if (parameters != null) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                command.append(" --").append(entry.getKey())
                        .append("=").append(shellEscape(entry.getValue().toString()));
            }
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", command.toString());
            pb.directory(new File(workingDirectory != null ? workingDirectory : System.getProperty("user.dir")));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return SkillResult.failure("Skill script timed out after " + timeoutSeconds + " seconds");
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();

            if (exitCode == 0) {
                return SkillResult.success(result.isEmpty() ? "(skill completed)" : result);
            } else {
                return SkillResult.failure("Script exited with code " + exitCode + ":\n" + result);
            }
        } catch (Exception e) {
            return SkillResult.failure("Failed to execute skill script: " + e.getMessage());
        }
    }

    @Override
    public String getSystemPromptAddition() {
        return systemPromptText != null ? systemPromptText : "";
    }

    @Override
    public boolean isAvailable() {
        if ("prompt".equals(type)) return true;
        if (script == null) return false;
        return new File(script).exists() || script.startsWith("/");
    }

    private String shellEscape(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
