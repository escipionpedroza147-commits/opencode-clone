package com.opencodejava.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Conversation {
    private final String id;
    private final String title;
    private final Instant createdAt;
    private final List<Message> messages;
    private String activeAgentName;

    public Conversation(String title) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.createdAt = Instant.now();
        this.messages = new ArrayList<>();
        this.activeAgentName = "build";
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public Instant getCreatedAt() { return createdAt; }

    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public void addMessage(Message message) {
        messages.add(message);
    }

    public String getActiveAgentName() { return activeAgentName; }
    public void setActiveAgentName(String name) { this.activeAgentName = name; }

    public int getMessageCount() { return messages.size(); }

    public void removeLastMessage() {
        if (!messages.isEmpty()) {
            messages.remove(messages.size() - 1);
        }
    }

    public List<Message> getLastMessages(int count) {
        int start = Math.max(0, messages.size() - count);
        return messages.subList(start, messages.size());
    }

    public void compact() {
        if (messages.size() <= 4) return;
        StringBuilder summary = new StringBuilder("Previous conversation summary:\n");
        List<Message> toSummarize = messages.subList(0, messages.size() - 2);
        for (Message msg : toSummarize) {
            summary.append(String.format("- %s: %s\n", msg.getRole(),
                    msg.getContent().length() > 100 ?
                            msg.getContent().substring(0, 100) + "..." : msg.getContent()));
        }
        Message summaryMsg = new Message(Message.Role.SYSTEM, summary.toString());
        List<Message> kept = new ArrayList<>(messages.subList(messages.size() - 2, messages.size()));
        messages.clear();
        messages.add(summaryMsg);
        messages.addAll(kept);
    }
}
