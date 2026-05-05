package com.opencodejava.command;

import com.opencodejava.core.App;

public class ClearCommand implements Command {
    private final App app;

    public ClearCommand(App app) {
        this.app = app;
    }

    @Override
    public String getName() { return "clear"; }

    @Override
    public String getDescription() { return "Clear the conversation history and start fresh"; }

    @Override
    public String execute(String[] args) {
        app.getAgentManager().clearActiveAgentHistory();
        app.getSessionManager().newConversation(null);
        return "Conversation cleared. Starting fresh.";
    }
}
