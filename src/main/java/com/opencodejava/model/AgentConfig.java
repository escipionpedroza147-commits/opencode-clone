package com.opencodejava.model;

import java.util.List;
import java.util.Map;

public class AgentConfig {
    private String name;
    private String description;
    private String systemPrompt;
    private List<String> tools;
    private boolean readOnly;
    private Map<String, Object> extra;

    public AgentConfig() {}

    public AgentConfig(String name, String description, String systemPrompt, List<String> tools, boolean readOnly) {
        this.name = name;
        this.description = description;
        this.systemPrompt = systemPrompt;
        this.tools = tools;
        this.readOnly = readOnly;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }

    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools; }

    public boolean isReadOnly() { return readOnly; }
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }

    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }
}
