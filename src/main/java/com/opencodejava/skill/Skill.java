package com.opencodejava.skill;

import java.util.Map;

/**
 * Represents a skill that can be loaded and used by agents.
 * Skills extend agent capabilities beyond built-in tools.
 */
public interface Skill {
    /**
     * Get the skill name.
     */
    String getName();

    /**
     * Get the skill description.
     */
    String getDescription();

    /**
     * Execute the skill with given parameters.
     */
    SkillResult execute(Map<String, Object> parameters);

    /**
     * Get the skill's system prompt addition.
     */
    String getSystemPromptAddition();

    /**
     * Whether this skill is available in the current environment.
     */
    boolean isAvailable();

    /**
     * Result of skill execution.
     */
    record SkillResult(boolean success, String output, String error) {
        public static SkillResult success(String output) {
            return new SkillResult(true, output, null);
        }

        public static SkillResult failure(String error) {
            return new SkillResult(false, null, error);
        }
    }
}
