package com.opencodejava.agent;

import com.opencodejava.core.Config;
import com.opencodejava.core.SessionManager;
import com.opencodejava.model.AgentConfig;
import com.opencodejava.model.Message;
import com.opencodejava.provider.LLMProvider;
import com.opencodejava.tool.ToolRegistry;

import java.util.*;
import java.util.function.Consumer;

public class AgentManager {
    private final Config config;
    private final LLMProvider provider;
    private final ToolRegistry toolRegistry;
    private final SessionManager sessionManager;
    private final Map<String, Agent> agents;
    private String activeAgentName;

    public AgentManager(Config config, LLMProvider provider, ToolRegistry toolRegistry,
                        SessionManager sessionManager) {
        this.config = config;
        this.provider = provider;
        this.toolRegistry = toolRegistry;
        this.sessionManager = sessionManager;
        this.agents = new LinkedHashMap<>();
        this.activeAgentName = "build";

        initializeAgents();
    }

    private void initializeAgents() {
        for (Map.Entry<String, AgentConfig> entry : config.getAgents().entrySet()) {
            agents.put(entry.getKey(), new Agent(entry.getValue(), provider, toolRegistry));
        }
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

    public String processMessage(String userMessage, Consumer<String> onToken) {
        Agent active = getActiveAgent();
        sessionManager.addMessage(new Message(Message.Role.USER, userMessage));

        String response = active.processMessage(userMessage, onToken);

        sessionManager.addMessage(new Message(Message.Role.ASSISTANT, response));
        return response;
    }

    public String delegateToSubagent(String agentName, String task, Consumer<String> onToken) {
        AgentConfig subConfig = config.getAgentConfig(agentName);
        if (subConfig == null) {
            return "Error: Unknown agent '" + agentName + "'";
        }

        Agent active = getActiveAgent();
        return active.delegateToSubagent(subConfig, task, onToken);
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

    public void registerAgent(AgentConfig agentConfig) {
        agents.put(agentConfig.getName(), new Agent(agentConfig, provider, toolRegistry));
    }

    public void clearActiveAgentHistory() {
        getActiveAgent().clearHistory();
    }
}
