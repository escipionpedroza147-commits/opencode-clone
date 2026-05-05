package com.opencodejava.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.opencodejava.model.Conversation;
import com.opencodejava.model.Message;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public class SessionManager {
    private final Config config;
    private final ObjectMapper mapper;
    private Conversation currentConversation;
    private final List<Conversation> history;

    public SessionManager(Config config) {
        this.config = config;
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.history = new ArrayList<>();
        config.ensureDataDirectory();
    }

    public Conversation newConversation(String title) {
        if (currentConversation != null) {
            saveConversation(currentConversation);
            history.add(currentConversation);
        }
        currentConversation = new Conversation(title != null ? title : "Session " + Instant.now());
        return currentConversation;
    }

    public Conversation getCurrentConversation() {
        if (currentConversation == null) {
            currentConversation = new Conversation("Session " + Instant.now());
        }
        return currentConversation;
    }

    public void addMessage(Message message) {
        getCurrentConversation().addMessage(message);
    }

    public void saveConversation(Conversation conversation) {
        try {
            Path sessionsDir = Path.of(config.getDataDirectory(), "sessions");
            Files.createDirectories(sessionsDir);
            Path sessionFile = sessionsDir.resolve(conversation.getId() + ".json");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", conversation.getId());
            data.put("title", conversation.getTitle());
            data.put("created_at", conversation.getCreatedAt().toString());
            data.put("message_count", conversation.getMessageCount());

            List<Map<String, String>> messages = new ArrayList<>();
            for (Message msg : conversation.getMessages()) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("role", msg.getRole().name().toLowerCase());
                m.put("content", msg.getContent());
                m.put("timestamp", msg.getTimestamp().toString());
                messages.add(m);
            }
            data.put("messages", messages);

            mapper.writeValue(sessionFile.toFile(), data);
        } catch (IOException e) {
            System.err.println("Warning: Failed to save session: " + e.getMessage());
        }
    }

    public void saveCurrentSession() {
        if (currentConversation != null) {
            saveConversation(currentConversation);
        }
    }

    public List<Conversation> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public void compactCurrentConversation() {
        if (currentConversation != null) {
            currentConversation.compact();
        }
    }
}
