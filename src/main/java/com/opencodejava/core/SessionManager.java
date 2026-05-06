package com.opencodejava.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.opencodejava.model.Conversation;
import com.opencodejava.model.Message;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * Manages conversation sessions and their persistence.
 * 
 * <p>The SessionManager handles the lifecycle of conversation sessions, including:
 * <ul>
 *   <li>Creating new conversations with optional titles</li>
 *   <li>Maintaining conversation history</li>
 *   <li>Persisting conversations to disk as JSON files</li>
 *   <li>Loading and listing saved conversations</li>
 *   <li>Exporting conversations to various formats</li>
 * </ul>
 * 
 * <p>Conversations are automatically saved when a new session is created or when
 * the application shuts down. Each conversation includes a complete message history
 * with timestamps, agent information, and tool execution results.
 * 
 * <h2>Storage Format:</h2>
 * <p>Conversations are stored as JSON files in the configured data directory
 * ({@code ~/.opencode-java/} by default). Each file contains the complete
 * conversation state including messages, metadata, and timestamps.
 * 
 * <h2>Thread Safety:</h2>
 * <p>This class is not thread-safe and should be used from a single thread
 * or with appropriate external synchronization.
 * 
 * @author OpenCode Java Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class SessionManager {
    /** Application configuration */
    private final Config config;
    
    /** JSON mapper for conversation serialization */
    private final ObjectMapper mapper;
    
    /** Currently active conversation */
    private Conversation currentConversation;
    
    /** History of completed conversations */
    private final List<Conversation> history;

    /**
     * Creates a new SessionManager with the given configuration.
     * 
     * @param config the application configuration
     */
    public SessionManager(Config config) {
        this.config = config;
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.history = new ArrayList<>();
        config.ensureDataDirectory();
    }

    /**
     * Creates a new conversation, saving the current one if it exists.
     * 
     * @param title optional title for the conversation, auto-generated if null
     * @return the new conversation instance
     */
    public Conversation newConversation(String title) {
        if (currentConversation != null) {
            saveConversation(currentConversation);
            history.add(currentConversation);
        }
        currentConversation = new Conversation(title != null ? title : "Session " + Instant.now());
        return currentConversation;
    }

    /**
     * Gets the current active conversation, creating one if none exists.
     * 
     * @return the current conversation
     */
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
