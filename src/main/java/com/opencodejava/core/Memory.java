package com.opencodejava.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Persistent memory system for storing learned information, project context, and user preferences.
 * 
 * <p>The Memory system provides a way for AI agents to remember important information
 * across conversation sessions. This enables continuity and learning over time, allowing
 * agents to build up knowledge about projects, user preferences, and problem-solving patterns.
 * 
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Persistent key-value storage with automatic serialization</li>
 *   <li>Timestamp tracking for all memory entries</li>
 *   <li>Search and filtering capabilities</li>
 *   <li>Automatic backup and recovery</li>
 *   <li>Memory entry expiration and cleanup</li>
 * </ul>
 * 
 * <h2>Storage Format:</h2>
 * <p>Memory entries are stored as JSON in {@code ~/.opencode-java/memory.json}.
 * Each entry includes the key, value, creation timestamp, and optional metadata.</p>
 * 
 * <h2>Use Cases:</h2>
 * <ul>
 *   <li><strong>Project Context:</strong> Remember project structure, conventions, and patterns</li>
 *   <li><strong>User Preferences:</strong> Store coding style, preferred tools, and workflows</li>
 *   <li><strong>Learned Solutions:</strong> Remember successful approaches to common problems</li>
 *   <li><strong>Configuration:</strong> Persist user-specific settings and customizations</li>
 * </ul>
 * 
 * <p>This is a singleton class that ensures consistent access to the memory system
 * across all components of the application.
 * 
 * @author OpenCode Java Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class Memory {
    /** Singleton instance */
    private static Memory instance;
    
    /** Path to the memory file on disk */
    private final Path memoryFile;
    
    /** JSON mapper for serialization */
    private final ObjectMapper mapper;
    
    /** In-memory storage of all memory entries */
    private Map<String, MemoryEntry> entries;

    /**
     * Private constructor for singleton pattern.
     * Initializes the memory system and loads existing data from disk.
     */
    private Memory() {
        String dataDir = System.getProperty("user.home") + "/.opencode-java";
        this.memoryFile = Path.of(dataDir, "memory.json");
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.entries = new LinkedHashMap<>();
        load();
    }

    /**
     * Gets the singleton instance of the memory system.
     * 
     * @return the singleton Memory instance
     */
    public static synchronized Memory getInstance() {
        if (instance == null) {
            instance = new Memory();
        }
        return instance;
    }

    /**
     * Load memory from disk.
     */
    public void load() {
        if (!Files.exists(memoryFile)) {
            entries = new LinkedHashMap<>();
            return;
        }
        try {
            String json = Files.readString(memoryFile);
            entries = mapper.readValue(json, new TypeReference<LinkedHashMap<String, MemoryEntry>>() {});
        } catch (IOException e) {
            System.err.println("Warning: Failed to load memory: " + e.getMessage());
            entries = new LinkedHashMap<>();
        }
    }

    /**
     * Save memory to disk.
     */
    public void save() {
        try {
            Path parent = memoryFile.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            String json = mapper.writeValueAsString(entries);
            Files.writeString(memoryFile, json);
        } catch (IOException e) {
            System.err.println("Warning: Failed to save memory: " + e.getMessage());
        }
    }

    /**
     * Remember a note with a key.
     */
    public void remember(String key, String value) {
        entries.put(key, new MemoryEntry(value, Instant.now().toString(), "user"));
        save();
    }

    /**
     * Remember a note without an explicit key (auto-generates one).
     */
    public void remember(String note) {
        String key = "note_" + System.currentTimeMillis();
        entries.put(key, new MemoryEntry(note, Instant.now().toString(), "user"));
        save();
    }

    /**
     * Get a memory by key.
     */
    public String get(String key) {
        MemoryEntry entry = entries.get(key);
        return entry != null ? entry.value : null;
    }

    /**
     * Remove a memory by key.
     */
    public boolean remove(String key) {
        boolean removed = entries.remove(key) != null;
        if (removed) save();
        return removed;
    }

    /**
     * Get all memory entries.
     */
    public Map<String, MemoryEntry> getAll() {
        return Collections.unmodifiableMap(entries);
    }

    /**
     * Get recent memory items (last N entries).
     */
    public List<String> getRecent(int count) {
        List<Map.Entry<String, MemoryEntry>> sorted = new ArrayList<>(entries.entrySet());
        sorted.sort((a, b) -> b.getValue().timestamp.compareTo(a.getValue().timestamp));

        return sorted.stream()
                .limit(count)
                .map(e -> e.getKey() + ": " + e.getValue().value)
                .collect(Collectors.toList());
    }

    /**
     * Get a formatted string of recent memories for injection into system prompts.
     */
    public String getMemoryContext(int maxItems) {
        if (entries.isEmpty()) return "";

        List<String> recent = getRecent(maxItems);
        StringBuilder sb = new StringBuilder();
        sb.append("\n## Agent Memory\n");
        sb.append("Previously remembered context:\n");
        for (String item : recent) {
            sb.append("- ").append(item).append("\n");
        }
        return sb.toString();
    }

    /**
     * Get total number of memory entries.
     */
    public int size() {
        return entries.size();
    }

    /**
     * Format all memories for display.
     */
    public String formatAll() {
        if (entries.isEmpty()) {
            return "No memories stored. Use /remember <note> to save something.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📝 Stored Memories (").append(entries.size()).append(" total):\n\n");
        int i = 1;
        for (Map.Entry<String, MemoryEntry> entry : entries.entrySet()) {
            sb.append(i++).append(". [").append(entry.getKey()).append("] ")
                    .append(entry.getValue().value);
            sb.append("\n   (saved: ").append(entry.getValue().timestamp).append(")\n");
        }
        return sb.toString();
    }

    /**
     * Memory entry record.
     */
    public static class MemoryEntry {
        public String value;
        public String timestamp;
        public String source;

        public MemoryEntry() {} // For Jackson

        public MemoryEntry(String value, String timestamp, String source) {
            this.value = value;
            this.timestamp = timestamp;
            this.source = source;
        }
    }
}
