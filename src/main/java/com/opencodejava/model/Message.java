package com.opencodejava.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class Message {
    public enum Role {
        USER, ASSISTANT, SYSTEM, TOOL
    }

    private final String id;
    private final Role role;
    private final String content;
    private final Instant timestamp;
    private final String toolCallId;
    private final String toolName;
    private final List<ToolCall> toolCalls; // For assistant messages that invoke tools

    public Message(Role role, String content) {
        this(role, content, null, null, null);
    }

    public Message(Role role, String content, String toolCallId, String toolName) {
        this(role, content, toolCallId, toolName, null);
    }

    /** Assistant message that includes tool calls */
    public Message(Role role, String content, List<ToolCall> toolCalls) {
        this(role, content, null, null, toolCalls);
    }

    private Message(Role role, String content, String toolCallId, String toolName, List<ToolCall> toolCalls) {
        this.id = UUID.randomUUID().toString();
        this.role = role;
        this.content = content;
        this.timestamp = Instant.now();
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.toolCalls = toolCalls;
    }

    public String getId() { return id; }
    public Role getRole() { return role; }
    public String getContent() { return content; }
    public Instant getTimestamp() { return timestamp; }
    public String getToolCallId() { return toolCallId; }
    public String getToolName() { return toolName; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }

    @Override
    public String toString() {
        return String.format("[%s] %s: %s", timestamp, role, content);
    }
}
