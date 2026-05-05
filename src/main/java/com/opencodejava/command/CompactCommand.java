package com.opencodejava.command;

import com.opencodejava.core.App;

public class CompactCommand implements Command {
    private final App app;

    public CompactCommand(App app) {
        this.app = app;
    }

    @Override
    public String getName() { return "compact"; }

    @Override
    public String getDescription() { return "Compact the conversation history to save context"; }

    @Override
    public String execute(String[] args) {
        app.getSessionManager().compactCurrentConversation();
        int remaining = app.getSessionManager().getCurrentConversation().getMessageCount();
        return "Conversation compacted. " + remaining + " messages remaining.";
    }
}
