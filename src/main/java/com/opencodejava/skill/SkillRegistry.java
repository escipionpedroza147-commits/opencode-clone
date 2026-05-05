package com.opencodejava.skill;

import java.util.*;

/**
 * Registry for managing available skills.
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

    public Skill getSkill(String name) {
        return skills.get(name);
    }

    public boolean hasSkill(String name) {
        return skills.containsKey(name);
    }

    public Collection<Skill> getAllSkills() {
        return skills.values();
    }

    public String getSkillsSystemPrompt() {
        if (skills.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("\n\nAvailable skills:\n");
        for (Skill skill : skills.values()) {
            sb.append("- ").append(skill.getName()).append(": ").append(skill.getDescription()).append("\n");
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
            return Skill.SkillResult.failure("Unknown skill: " + name);
        }
        return skill.execute(parameters);
    }

    public static SkillRegistry createDefault(String workingDirectory) {
        SkillRegistry registry = new SkillRegistry();
        registry.register(new ShellSkill(workingDirectory));
        return registry;
    }
}
