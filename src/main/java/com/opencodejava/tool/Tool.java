package com.opencodejava.tool;

import com.opencodejava.provider.LLMProvider.ToolDefinition;

import java.util.Map;

public interface Tool {
    /**
     * Get the tool name (used in function calls).
     */
    String getName();

    /**
     * Get the tool description.
     */
    String getDescription();

    /**
     * Execute the tool with given arguments.
     */
    ToolResult execute(Map<String, Object> arguments);

    /**
     * Get the tool definition for LLM function calling.
     */
    ToolDefinition getDefinition();

    /**
     * Whether this tool is read-only (safe for plan agent).
     */
    boolean isReadOnly();

    /**
     * Result of a tool execution.
     */
    record ToolResult(boolean success, String output, String error) {
        public static ToolResult success(String output) {
            return new ToolResult(true, output, null);
        }

        public static ToolResult failure(String error) {
            return new ToolResult(false, null, error);
        }

        public String getContent() {
            return success ? output : "Error: " + error;
        }
    }
}
