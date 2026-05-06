package com.opencodejava.agent;

import com.opencodejava.core.Memory;
import com.opencodejava.core.UsageTracker;
import com.opencodejava.model.AgentConfig;
import com.opencodejava.model.Message;
import com.opencodejava.model.ToolCall;
import com.opencodejava.provider.LLMProvider;
import com.opencodejava.provider.LLMProvider.LLMResponse;
import com.opencodejava.provider.LLMProvider.ToolDefinition;
import com.opencodejava.tool.Tool;
import com.opencodejava.tool.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Core agent that processes messages through an LLM with tool calling.
 * Supports multi-depth subagents, auto-retry on failures, and iterative
 * tool use loops for handling complex multi-step tasks.
 */
public class Agent {
    private final AgentConfig config;
    private final LLMProvider provider;
    private final ToolRegistry toolRegistry;
    private final List<Message> conversationHistory;
    private final List<Agent> subagents;
    private final List<String> memories;
    private final int depth;
    private int consecutiveErrorCount;
    private static final int MAX_DEPTH = 5;
    private static final int MAX_TOOL_ITERATIONS = 50; // Higher limit for complex tasks
    private static final int MAX_RETRIES = 3;
    private static final int HANDOFF_ERROR_THRESHOLD = 3;

    public Agent(AgentConfig config, LLMProvider provider, ToolRegistry toolRegistry) {
        this(config, provider, toolRegistry, 0);
    }

    public Agent(AgentConfig config, LLMProvider provider, ToolRegistry toolRegistry, int depth) {
        this.config = config;
        this.provider = provider;
        this.toolRegistry = toolRegistry;
        this.conversationHistory = new ArrayList<>();
        this.subagents = new ArrayList<>();
        this.memories = new ArrayList<>();
        this.depth = depth;
        this.consecutiveErrorCount = 0;
    }

    /**
     * Process a user message through the LLM with full tool-use loop.
     * The agent will continue making tool calls until it produces a final text response
     * or hits the iteration limit. Failed tool calls are retried with error context.
     */
    public String processMessage(String userMessage, Consumer<String> onToken) {
        conversationHistory.add(new Message(Message.Role.USER, userMessage));

        List<ToolDefinition> tools = getAvailableTools();
        int iterations = 0;
        int consecutiveErrors = 0;

        while (iterations < MAX_TOOL_ITERATIONS) {
            iterations++;

            LLMResponse response;
            try {
                if (onToken != null && iterations == 1) {
                    // Try streaming first response for immediate feedback
                    final LLMResponse[] responseHolder = new LLMResponse[1];
                    provider.chatStream(conversationHistory, tools, buildSystemPrompt(), onToken, r -> {
                        responseHolder[0] = r;
                    });
                    response = responseHolder[0];
                    // If streaming failed, fall back to non-streaming
                    if (response == null) {
                        if (onToken != null) onToken.accept("[retrying without streaming...]\n");
                        response = provider.chat(conversationHistory, tools, buildSystemPrompt());
                    }
                } else {
                    response = provider.chat(conversationHistory, tools, buildSystemPrompt());
                }
            } catch (Exception e) {
                // If streaming throws, fall back to non-streaming
                if (iterations == 1 && onToken != null) {
                    onToken.accept("[stream error, retrying...]\n");
                    try {
                        response = provider.chat(conversationHistory, tools, buildSystemPrompt());
                    } catch (Exception e2) {
                        response = null;
                    }
                } else {
                    response = null;
                }
            }

            if (response == null) {
                // Retry on null response
                if (consecutiveErrors < MAX_RETRIES) {
                    consecutiveErrors++;
                    conversationHistory.add(new Message(Message.Role.SYSTEM,
                            "System: No response received. Please try again."));
                    continue;
                }
                return "Error: Failed to get response from provider after " + MAX_RETRIES + " retries.";
            }

            consecutiveErrors = 0; // Reset on successful response

            // Track usage
            if (response.promptTokens() > 0 || response.completionTokens() > 0) {
                UsageTracker.getInstance().addUsage(
                        provider.getModelName(), response.promptTokens(), response.completionTokens());
            }

            if (response.hasToolCalls()) {
                // Add assistant message WITH tool_calls attached (required by API)
                String assistantContent = response.content() != null ? response.content() : "";
                conversationHistory.add(new Message(Message.Role.ASSISTANT, assistantContent, response.toolCalls()));
                if (!assistantContent.isEmpty() && onToken != null && iterations > 1) {
                    onToken.accept(assistantContent);
                }

                // Execute each tool call
                boolean anyFailed = false;
                for (ToolCall toolCall : response.toolCalls()) {
                    // Check read-only restrictions
                    if (config.isReadOnly() && !isReadOnlyTool(toolCall.getName())) {
                        String denied = "Permission denied: Tool '" + toolCall.getName() +
                                "' is not available in read-only mode. Use file_read or search instead.";
                        conversationHistory.add(new Message(Message.Role.TOOL, denied,
                                toolCall.getId(), toolCall.getName()));
                        if (onToken != null) onToken.accept("\n⚠️ " + denied + "\n");
                        anyFailed = true;
                        continue;
                    }

                    if (onToken != null) {
                        onToken.accept("\n🔧 " + formatToolCall(toolCall) + "\n");
                    }

                    Tool.ToolResult result = executeWithRetry(toolCall);

                    conversationHistory.add(new Message(Message.Role.TOOL, result.getContent(),
                            toolCall.getId(), toolCall.getName()));

                    if (onToken != null) {
                        String preview = result.getContent();
                        if (preview.length() > 500) {
                            preview = preview.substring(0, 500) + "\n... [" +
                                    (result.getContent().length() - 500) + " more chars]";
                        }
                        onToken.accept("   " + (result.success() ? "✓" : "✗") + " " + preview + "\n");
                    }

                    if (!result.success()) {
                        anyFailed = true;
                    }
                }

                // If tools failed, inject a hint to help the agent recover
                if (anyFailed) {
                    consecutiveErrorCount++;
                    conversationHistory.add(new Message(Message.Role.SYSTEM,
                            "System: One or more tool calls failed. Review the errors above and try a different approach. " +
                                    "Read relevant files first if you're unsure about paths or content."));

                    // Agent-to-Agent handoff: if too many consecutive errors, ask plan agent
                    if (consecutiveErrorCount >= HANDOFF_ERROR_THRESHOLD && !config.isReadOnly() && depth < MAX_DEPTH) {
                        String handoffResult = requestPlanAgentHelp(userMessage, onToken);
                        if (handoffResult != null) {
                            conversationHistory.add(new Message(Message.Role.SYSTEM,
                                    "Plan agent advice: " + handoffResult));
                            consecutiveErrorCount = 0;
                        }
                    }
                } else {
                    consecutiveErrorCount = 0;
                }

                // Continue loop — the LLM will process tool results and decide next action
            } else {
                // No tool calls — this is the final response
                String content = response.content();
                if (content == null || content.isEmpty()) {
                    content = "(Agent produced no response)";
                }

                // Check if agent is unsure and should hand off to plan agent
                if (shouldHandoff(content) && !config.isReadOnly() && depth < MAX_DEPTH) {
                    String advice = requestPlanAgentHelp(userMessage, onToken);
                    if (advice != null) {
                        conversationHistory.add(new Message(Message.Role.SYSTEM,
                                "Plan agent advice: " + advice + "\nPlease try again with this guidance."));
                        consecutiveErrorCount = 0;
                        continue; // Re-loop with plan agent's advice
                    }
                }

                conversationHistory.add(new Message(Message.Role.ASSISTANT, content));

                // Stream remaining content if not already streamed
                if (onToken != null && iterations > 1) {
                    onToken.accept(content);
                }

                return content;
            }
        }

        String maxIterMsg = "⚠️ Reached maximum iterations (" + MAX_TOOL_ITERATIONS +
                "). The task may be too complex for a single pass. " +
                "Consider breaking it into smaller subtasks.";
        conversationHistory.add(new Message(Message.Role.ASSISTANT, maxIterMsg));
        return maxIterMsg;
    }

    /**
     * Execute a tool call with automatic retry on transient failures.
     */
    private Tool.ToolResult executeWithRetry(ToolCall toolCall) {
        Tool.ToolResult result = toolRegistry.executeTool(toolCall.getName(), toolCall.getArguments());

        // Retry once on certain transient errors
        if (!result.success() && isRetryableError(result.error())) {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            result = toolRegistry.executeTool(toolCall.getName(), toolCall.getArguments());
        }

        return result;
    }

    private boolean isRetryableError(String error) {
        if (error == null) return false;
        return error.contains("timed out") ||
                error.contains("temporarily unavailable") ||
                error.contains("resource busy");
    }

    /**
     * Build the full system prompt with context about available capabilities.
     */
    private String buildSystemPrompt() {
        StringBuilder prompt = new StringBuilder();
        prompt.append(config.getSystemPrompt());

        // Add working directory context
        prompt.append("\n\n## Environment\n");
        prompt.append("- Working directory: ").append(toolRegistry.getAllTools().iterator().next() != null ?
                System.getProperty("user.dir") : "unknown").append("\n");
        prompt.append("- Agent: ").append(config.getName()).append("\n");
        prompt.append("- Depth: ").append(depth).append("/").append(MAX_DEPTH).append("\n");

        if (depth > 0) {
            prompt.append("\nYou are a subagent. Complete your assigned task fully and return your findings/results.");
        }

        // Add persistent memory context
        String memoryContext = Memory.getInstance().getMemoryContext(10);
        if (!memoryContext.isEmpty()) {
            prompt.append(memoryContext);
        }

        // Add agent session memories
        if (!memories.isEmpty()) {
            prompt.append("\n## Session Notes\n");
            for (String mem : memories) {
                prompt.append("- ").append(mem).append("\n");
            }
        }

        return prompt.toString();
    }

    /**
     * Format a tool call for display.
     */
    private String formatToolCall(ToolCall toolCall) {
        StringBuilder sb = new StringBuilder(toolCall.getName());
        if (toolCall.getArguments() != null && !toolCall.getArguments().isEmpty()) {
            sb.append("(");
            var args = toolCall.getArguments();
            if (args.containsKey("command")) {
                String cmd = args.get("command").toString();
                sb.append(cmd.length() > 80 ? cmd.substring(0, 80) + "..." : cmd);
            } else if (args.containsKey("path")) {
                sb.append(args.get("path"));
            } else if (args.containsKey("pattern")) {
                sb.append("\"").append(args.get("pattern")).append("\"");
            } else {
                sb.append(args.size()).append(" args");
            }
            sb.append(")");
        }
        return sb.toString();
    }

    private List<ToolDefinition> getAvailableTools() {
        if (config.getTools() == null || config.getTools().isEmpty()) {
            return config.isReadOnly() ? toolRegistry.getReadOnlyToolDefinitions()
                    : toolRegistry.getAllToolDefinitions();
        }
        return toolRegistry.getToolDefinitions(config.getTools());
    }

    private boolean isReadOnlyTool(String toolName) {
        Tool tool = toolRegistry.getTool(toolName);
        return tool != null && tool.isReadOnly();
    }

    /**
     * Spawn a subagent at depth+1 for delegating subtasks.
     */
    public Agent spawnSubagent(AgentConfig subConfig) {
        if (depth >= MAX_DEPTH) {
            throw new IllegalStateException("Maximum subagent depth (" + MAX_DEPTH + ") reached. " +
                    "Cannot spawn more subagents. Complete the task at this level.");
        }
        Agent sub = new Agent(subConfig, provider, toolRegistry, depth + 1);
        subagents.add(sub);
        return sub;
    }

    /**
     * Delegate a task to a subagent and return its response.
     */
    public String delegateToSubagent(AgentConfig subConfig, String task, Consumer<String> onToken) {
        Agent sub = spawnSubagent(subConfig);
        return sub.processMessage(task, onToken);
    }

    public AgentConfig getConfig() { return config; }
    public String getName() { return config.getName(); }
    public int getDepth() { return depth; }
    public List<Message> getConversationHistory() { return conversationHistory; }
    public List<Agent> getSubagents() { return subagents; }

    public void clearHistory() {
        conversationHistory.clear();
        subagents.clear();
        memories.clear();
    }

    /**
     * Add a memory to this agent's session memory.
     */
    public void addMemory(String memory) {
        memories.add(memory);
    }

    /**
     * Get this agent's session memories.
     */
    public List<String> getMemories() {
        return memories;
    }

    /**
     * Check if the response indicates the agent is unsure and should hand off.
     */
    private boolean shouldHandoff(String content) {
        if (content == null) return false;
        String lower = content.toLowerCase();
        return lower.contains("i'm not sure") ||
                lower.contains("i am not sure") ||
                lower.contains("i don't know how to") ||
                lower.contains("i cannot determine") ||
                lower.contains("i'm unable to figure out");
    }

    /**
     * Request help from the plan agent when the build agent is stuck.
     */
    private String requestPlanAgentHelp(String originalTask, Consumer<String> onToken) {
        try {
            if (onToken != null) {
                onToken.accept("\n🔄 Consulting plan agent for guidance...\n");
            }

            AgentConfig planConfig = new AgentConfig(
                    "plan-helper",
                    "Analysis helper",
                    "You are an analysis agent. The build agent is stuck on a task. " +
                            "Analyze the problem and provide concrete, actionable advice on how to proceed. " +
                            "Be specific about file paths, function names, and approaches to try.",
                    List.of("file_read", "search", "list_files"),
                    true
            );

            Agent planAgent = new Agent(planConfig, provider, toolRegistry, depth + 1);
            String task = "The build agent is stuck on this task: " + originalTask +
                    "\n\nRecent errors suggest the current approach isn't working. " +
                    "Please analyze and suggest a better approach.";

            return planAgent.processMessage(task, null);
        } catch (Exception e) {
            return null;
        }
    }
}
