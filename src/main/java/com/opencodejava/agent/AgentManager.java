package com.opencodejava.agent;

import com.opencodejava.core.Config;
import com.opencodejava.core.SessionManager;
import com.opencodejava.model.AgentConfig;
import com.opencodejava.model.Message;
import com.opencodejava.provider.LLMProvider;
import com.opencodejava.skill.SkillRegistry;
import com.opencodejava.tool.ToolRegistry;

import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manages all agents including built-in and user-defined.
 * Handles agent switching, @agent delegation, and skill integration.
 */
public class AgentManager {
    private final Config config;
    private final LLMProvider provider;
    private final ToolRegistry toolRegistry;
    private final SessionManager sessionManager;
    private final SkillRegistry skillRegistry;
    private final Map<String, Agent> agents;
    private String activeAgentName;

    private static final Pattern AGENT_MENTION = Pattern.compile("^@(\\w+)\\s+(.+)", Pattern.DOTALL);

    public AgentManager(Config config, LLMProvider provider, ToolRegistry toolRegistry,
                        SessionManager sessionManager, SkillRegistry skillRegistry) {
        this.config = config;
        this.provider = provider;
        this.toolRegistry = toolRegistry;
        this.sessionManager = sessionManager;
        this.skillRegistry = skillRegistry;
        this.agents = new LinkedHashMap<>();
        this.activeAgentName = "build";

        initializeAgents();
    }

    /**
     * Initialize all agents: built-in + user-defined from config.
     * Each agent gets skill context injected into its system prompt.
     */
    private void initializeAgents() {
        for (Map.Entry<String, AgentConfig> entry : config.getAgents().entrySet()) {
            AgentConfig agentConfig = entry.getValue();

            // Inject skill information into agent system prompts
            String enhancedPrompt = enhancePromptWithSkills(agentConfig.getSystemPrompt());
            AgentConfig enhanced = new AgentConfig(
                    agentConfig.getName(),
                    agentConfig.getDescription(),
                    enhancedPrompt,
                    agentConfig.getTools(),
                    agentConfig.isReadOnly()
            );

            agents.put(entry.getKey(), new Agent(enhanced, provider, toolRegistry));
        }
    }

    /**
     * Enhance an agent's system prompt with available skills information.
     */
    private String enhancePromptWithSkills(String basePrompt) {
        String skillsPrompt = skillRegistry.getSkillsSystemPrompt();
        if (skillsPrompt.isEmpty()) return basePrompt;

        return basePrompt + "\n" + skillsPrompt +
                "\nTo use a skill, you can invoke it through the bash tool or integrate its capabilities directly.";
    }

    public Agent getActiveAgent() {
        return agents.get(activeAgentName);
    }

    public String getActiveAgentName() {
        return activeAgentName;
    }

    public void setActiveAgent(String name) {
        if (!agents.containsKey(name)) {
            throw new IllegalArgumentException("Unknown agent: " + name + ". Available: " + agents.keySet());
        }
        this.activeAgentName = name;
        sessionManager.getCurrentConversation().setActiveAgentName(name);
    }

    public void cycleAgent() {
        List<String> agentNames = new ArrayList<>(agents.keySet());
        int currentIndex = agentNames.indexOf(activeAgentName);
        int nextIndex = (currentIndex + 1) % agentNames.size();
        setActiveAgent(agentNames.get(nextIndex));
    }

    /**
     * Process a user message. Handles:
     * - @agent delegation: "@general research this topic"
     * - Normal message: sent to active agent
     */
    public String processMessage(String userMessage, Consumer<String> onToken) {
        // Check for @agent mention
        Matcher matcher = AGENT_MENTION.matcher(userMessage.trim());
        if (matcher.matches()) {
            String targetAgent = matcher.group(1).toLowerCase();
            String task = matcher.group(2).trim();
            return delegateToSubagent(targetAgent, task, onToken);
        }

        // Normal processing with active agent
        Agent active = getActiveAgent();
        sessionManager.addMessage(new Message(Message.Role.USER, userMessage));

        String response = active.processMessage(userMessage, onToken);

        sessionManager.addMessage(new Message(Message.Role.ASSISTANT, response));
        return response;
    }

    /**
     * Delegate a task to a named subagent.
     * Supports multi-depth: the subagent can further delegate to other subagents.
     */
    public String delegateToSubagent(String agentName, String task, Consumer<String> onToken) {
        AgentConfig subConfig = config.getAgentConfig(agentName);
        if (subConfig == null) {
            return "Error: Unknown agent '" + agentName + "'. Available agents: " + agents.keySet();
        }

        if (onToken != null) {
            onToken.accept("📋 Delegating to @" + agentName + "...\n\n");
        }

        // Enhance subagent prompt with skills
        String enhancedPrompt = enhancePromptWithSkills(subConfig.getSystemPrompt());
        AgentConfig enhanced = new AgentConfig(
                subConfig.getName(),
                subConfig.getDescription(),
                enhancedPrompt,
                subConfig.getTools(),
                subConfig.isReadOnly()
        );

        Agent active = getActiveAgent();
        String response = active.delegateToSubagent(enhanced, task, onToken);

        // Record in session
        sessionManager.addMessage(new Message(Message.Role.USER, "@" + agentName + " " + task));
        sessionManager.addMessage(new Message(Message.Role.ASSISTANT,
                "[Subagent @" + agentName + " response]:\n" + response));

        return response;
    }

    public Agent getAgent(String name) {
        return agents.get(name);
    }

    public Map<String, Agent> getAllAgents() {
        return Collections.unmodifiableMap(agents);
    }

    public Set<String> getAgentNames() {
        return agents.keySet();
    }

    /**
     * Register a new agent at runtime (user-defined via config or dynamically).
     */
    public void registerAgent(AgentConfig agentConfig) {
        String enhancedPrompt = enhancePromptWithSkills(agentConfig.getSystemPrompt());
        AgentConfig enhanced = new AgentConfig(
                agentConfig.getName(),
                agentConfig.getDescription(),
                enhancedPrompt,
                agentConfig.getTools(),
                agentConfig.isReadOnly()
        );
        agents.put(agentConfig.getName(), new Agent(enhanced, provider, toolRegistry));
    }

    public void clearActiveAgentHistory() {
        getActiveAgent().clearHistory();
    }

    /**
     * Execute multiple agent tasks in parallel.
     * Each entry maps an agent name to its task.
     */
    public Map<String, String> parallelDelegate(Map<String, String> agentTasks, Consumer<String> onProgress) {
        Map<AgentConfig, String> tasks = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : agentTasks.entrySet()) {
            AgentConfig agentConfig = config.getAgentConfig(entry.getKey());
            if (agentConfig != null) {
                String enhancedPrompt = enhancePromptWithSkills(agentConfig.getSystemPrompt());
                AgentConfig enhanced = new AgentConfig(
                        agentConfig.getName(),
                        agentConfig.getDescription(),
                        enhancedPrompt,
                        agentConfig.getTools(),
                        agentConfig.isReadOnly()
                );
                tasks.put(enhanced, entry.getValue());
            }
        }

        ParallelExecutor executor = new ParallelExecutor(provider, toolRegistry);
        try {
            return executor.executeParallel(tasks, onProgress);
        } finally {
            executor.shutdown();
        }
    }

    public SkillRegistry getSkillRegistry() {
        return skillRegistry;
    }
}
