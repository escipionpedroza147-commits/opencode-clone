package com.opencodejava.skill;

import java.io.IOException;
import java.nio.file.*;
import java.util.stream.Stream;

/**
 * Loads OpenClaw-style SKILL.md files from a directory
 * and registers them as prompt-based skills.
 *
 * Expects directory structure:
 *   skillsDir/skill-name/SKILL.md
 *
 * Each SKILL.md becomes a prompt-based skill with the full
 * markdown content injected as the system prompt addition.
 */
public class MarkdownSkillLoader {

    /**
     * Load all skills from a directory structure.
     * Scans immediate subdirectories for SKILL.md files.
     */
    public static void loadFromDirectory(Path skillsDir, SkillRegistry registry) {
        if (!Files.isDirectory(skillsDir)) return;

        try (Stream<Path> dirs = Files.list(skillsDir)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                Path skillFile = dir.resolve("SKILL.md");
                if (Files.exists(skillFile)) {
                    try {
                        String content = Files.readString(skillFile);
                        String name = dir.getFileName().toString();
                        String description = extractDescription(content);

                        registry.registerUserSkill(
                            name,
                            description,
                            "prompt",
                            null,
                            content,  // entire SKILL.md becomes the system prompt
                            dir.toString(),
                            120
                        );
                    } catch (IOException e) {
                        System.err.println("Warning: Failed to load skill from " + skillFile + ": " + e.getMessage());
                    }
                }
            });
        } catch (IOException e) {
            System.err.println("Warning: Failed to scan skills directory: " + e.getMessage());
        }
    }

    /**
     * Load skills from multiple directories.
     */
    public static void loadFromDirectories(java.util.List<String> skillsDirs, SkillRegistry registry) {
        if (skillsDirs == null) return;
        for (String dir : skillsDirs) {
            String expanded = dir.replace("~", System.getProperty("user.home"));
            loadFromDirectory(Path.of(expanded), registry);
        }
    }

    /**
     * Extract first non-heading, non-empty line as description.
     */
    private static String extractDescription(String content) {
        String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            return line.length() > 100 ? line.substring(0, 100) + "..." : line;
        }
        return "Markdown-based skill";
    }
}
