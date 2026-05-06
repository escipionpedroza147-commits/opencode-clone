package com.opencodejava.tool;

import com.opencodejava.provider.LLMProvider.ToolDefinition;

import java.util.Map;

/**
 * Interface for all tools that can be executed by AI agents.
 * 
 * <p>Tools provide specific capabilities to agents such as file operations,
 * bash command execution, web search, git operations, and more. Each tool
 * must define its name, description, parameter schema, and execution logic.
 * 
 * <p>Tools can be classified as read-only (safe for planning agents) or
 * read-write (requiring full access permissions). This classification helps
 * maintain security boundaries between different types of agents.
 * 
 * <h2>Tool Categories:</h2>
 * <ul>
 *   <li><strong>File Operations:</strong> file_read, file_write, file_edit, list_files</li>
 *   <li><strong>System Operations:</strong> bash execution</li>
 *   <li><strong>Version Control:</strong> git operations</li>
 *   <li><strong>Web Operations:</strong> web_search, web_fetch</li>
 *   <li><strong>Code Operations:</strong> search, image generation</li>
 * </ul>
 * 
 * @author OpenCode Java Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface Tool {
    /**
     * Gets the unique name identifier for this tool.
     * 
     * <p>This name is used by the LLM when making function calls and must be
     * unique across all registered tools in the system.
     * 
     * @return the tool name (e.g., "file_read", "bash", "web_search")
     */
    String getName();

    /**
     * Gets a human-readable description of what this tool does.
     * 
     * <p>This description is provided to the LLM to help it understand
     * when and how to use this tool effectively.
     * 
     * @return the tool description
     */
    String getDescription();

    /**
     * Executes the tool with the provided arguments.
     * 
     * <p>This is the main entry point for tool execution. The arguments
     * map contains parameter names as keys and their values as provided
     * by the LLM function call.
     * 
     * @param arguments map of parameter names to values
     * @return the result of tool execution, including success status and output
     * @throws RuntimeException if the tool execution fails critically
     */
    ToolResult execute(Map<String, Object> arguments);

    /**
     * Gets the complete tool definition for LLM function calling.
     * 
     * <p>This definition includes the tool name, description, and parameter
     * schema that the LLM uses to understand how to call this tool properly.
     * 
     * @return the tool definition with parameter schema
     */
    ToolDefinition getDefinition();

    /**
     * Indicates whether this tool is read-only and safe for restricted agents.
     * 
     * <p>Read-only tools (like file_read, list_files, search) can be used by
     * planning agents that should not modify the system state. Read-write tools
     * (like file_write, bash, git) require full access permissions.
     * 
     * @return {@code true} if this tool is read-only, {@code false} otherwise
     */
    boolean isReadOnly();

    /**
     * Represents the result of a tool execution.
     * 
     * <p>This record encapsulates the outcome of tool execution, including
     * success status, output content, and error information if applicable.
     * It provides convenient factory methods for creating success and failure results.
     * 
     * @param success {@code true} if the tool executed successfully, {@code false} otherwise
     * @param output the output content from successful execution, {@code null} if failed
     * @param error the error message from failed execution, {@code null} if successful
     */
    record ToolResult(boolean success, String output, String error) {
        
        /**
         * Creates a successful tool result with output content.
         * 
         * @param output the output content from the tool execution
         * @return a successful ToolResult with the given output
         */
        public static ToolResult success(String output) {
            return new ToolResult(true, output, null);
        }

        /**
         * Creates a failed tool result with error message.
         * 
         * @param error the error message describing the failure
         * @return a failed ToolResult with the given error
         */
        public static ToolResult failure(String error) {
            return new ToolResult(false, null, error);
        }

        /**
         * Gets the content to display, either output or formatted error.
         * 
         * @return the output if successful, or "Error: " + error if failed
         */
        public String getContent() {
            return success ? output : "Error: " + error;
        }
    }
}
