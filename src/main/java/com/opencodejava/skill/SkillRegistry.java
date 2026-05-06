package com.opencodejava.skill;

import java.util.*;

/**
 * Registry for managing all skills: built-in, LangChain4j, and user-defined.
 */
public class SkillRegistry {
    private final Map<String, Skill> skills;

    public SkillRegistry() {
        this.skills = new LinkedHashMap<>();
    }

    public void register(Skill skill) {
        if (skill.isAvailable()) {
            skills.put(skill.getName(), skill);
        }
    }

    /**
     * Register a LangChain4j @Tool annotated object as a skill.
     */
    public void registerLangChain4j(Object toolInstance) {
        LangChain4jSkillAdapter adapter = new LangChain4jSkillAdapter(toolInstance);
        if (adapter.isAvailable()) {
            skills.put(adapter.getName(), adapter);
        }
    }

    /**
     * Register a LangChain4j @Tool annotated object with a custom name.
     */
    public void registerLangChain4j(Object toolInstance, String name, String description) {
        LangChain4jSkillAdapter adapter = new LangChain4jSkillAdapter(toolInstance, name, description);
        if (adapter.isAvailable()) {
            skills.put(adapter.getName(), adapter);
        }
    }

    /**
     * Register a user-defined skill from config.
     */
    public void registerUserSkill(String name, String description, String type,
                                  String script, String systemPrompt,
                                  String workingDirectory, int timeout) {
        UserSkill skill = new UserSkill(name, description, type, script,
                systemPrompt, workingDirectory, timeout);
        if (skill.isAvailable()) {
            skills.put(name, skill);
        }
    }

    public Skill getSkill(String name) {
        return skills.get(name);
    }

    public boolean hasSkill(String name) {
        return skills.containsKey(name);
    }

    public Collection<Skill> getAllSkills() {
        return skills.values();
    }

    /**
     * Get combined system prompt additions from all registered skills.
     */
    public String getSkillsSystemPrompt() {
        if (skills.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("\n\n## Available Skills\n");
        for (Skill skill : skills.values()) {
            sb.append("- **").append(skill.getName()).append("**: ").append(skill.getDescription()).append("\n");
            String addition = skill.getSystemPromptAddition();
            if (addition != null && !addition.isEmpty()) {
                sb.append("  ").append(addition).append("\n");
            }
        }
        return sb.toString();
    }

    public Skill.SkillResult executeSkill(String name, Map<String, Object> parameters) {
        Skill skill = skills.get(name);
        if (skill == null) {
            return Skill.SkillResult.failure("Unknown skill: " + name + ". Available: " + skills.keySet());
        }
        return skill.execute(parameters);
    }

    /**
     * Create default registry with built-in skills.
     * Includes langchain4j-skills-shell and langchain4j-experimental-skills-shell.
     */
    public static SkillRegistry createDefault(String workingDirectory, Map<String, Map<String, String>> userSkillDefs) {
        SkillRegistry registry = new SkillRegistry();

        // Built-in: langchain4j-experimental-skills-shell
        LangChain4jShellSkill shellSkill = new LangChain4jShellSkill(workingDirectory);
        registry.registerLangChain4j(shellSkill, "langchain4j-shell",
                "LangChain4j experimental shell skill with multi-command execution, " +
                        "directory control, and timeout management");

        // Built-in: basic shell (backward compat)
        registry.register(new ShellSkill(workingDirectory));

        // Load user-defined skills
        if (userSkillDefs != null) {
            for (Map.Entry<String, Map<String, String>> entry : userSkillDefs.entrySet()) {
                String name = entry.getKey();
                Map<String, String> def = entry.getValue();
                int timeout = 60;
                if (def.containsKey("timeout")) {
                    try { timeout = Integer.parseInt(def.get("timeout")); } catch (NumberFormatException ignored) {}
                }
                registry.registerUserSkill(
                        name,
                        def.getOrDefault("description", "User skill: " + name),
                        def.getOrDefault("type", "prompt"),
                        def.get("script"),
                        def.get("system_prompt"),
                        workingDirectory,
                        timeout
                );
            }
        }

        return registry;
    }
}
