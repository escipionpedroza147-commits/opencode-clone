package com.opencodejava.command;

import com.opencodejava.agent.Agent;
import com.opencodejava.core.App;

import java.util.Map;

public class AgentsCommand implements Command {
    private final App app;

    public AgentsCommand(App app) {
        this.app = app;
    }

    @Override
    public String getName() { return "agents"; }

    @Override
    public String getDescription() { return "List available agents or switch to one"; }

    @Override
    public String getUsage() { return "/agents [agent-name]"; }

    @Override
    public String execute(String[] args) {
        if (args.length == 0) {
            StringBuilder sb = new StringBuilder("Available agents:\n\n");
            String active = app.getAgentManager().getActiveAgentName();
            for (Map.Entry<String, Agent> entry : app.getAgentManager().getAllAgents().entrySet()) {
                String marker = entry.getKey().equals(active) ? " ◀ active" : "";
                String readOnly = entry.getValue().getConfig().isReadOnly() ? " [read-only]" : "";
                sb.append(String.format("  %-10s %s%s%s\n",
                        entry.getKey(),
                        entry.getValue().getConfig().getDescription(),
                        readOnly, marker));
            }
            sb.append("\nUse /agents <name> to switch, or press Tab to cycle.");
            return sb.toString();
        }

        String targetAgent = args[0].toLowerCase();
        try {
            app.getAgentManager().setActiveAgent(targetAgent);
            return "Switched to agent: " + targetAgent;
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
    }
}
