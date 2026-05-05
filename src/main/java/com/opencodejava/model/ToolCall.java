package com.opencodejava.model;

import java.util.Map;

public class ToolCall {
    private final String id;
    private final String name;
    private final Map<String, Object> arguments;

    public ToolCall(String id, String name, Map<String, Object> arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Map<String, Object> getArguments() { return arguments; }

    public String getArgumentAsString(String key) {
        Object val = arguments.get(key);
        return val != null ? val.toString() : null;
    }
}
