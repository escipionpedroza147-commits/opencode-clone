package com.opencodejava.tool;

import com.opencodejava.core.Config;
import com.opencodejava.provider.LLMProvider.ToolDefinition;

import java.util.*;

public class ToolRegistry {
    private final Map<String, Tool> tools;

    public ToolRegistry(Config config) {
        this.tools = new LinkedHashMap<>();
        registerDefaults(config);
    }

    private void registerDefaults(Config config) {
        register(new FileReadTool());
        register(new FileWriteTool());
        register(new FileEditTool());
        register(new BashTool(config.getWorkingDirectory()));
        register(new SearchTool(config.getWorkingDirectory()));
        register(new ListFilesTool(config.getWorkingDirectory()));
        register(new GitTool(config.getWorkingDirectory()));
    }

    public void register(Tool tool) {
        tools.put(tool.getName(), tool);
    }

    public Tool getTool(String name) {
        return tools.get(name);
    }

    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    public Collection<Tool> getAllTools() {
        return tools.values();
    }

    public List<ToolDefinition> getToolDefinitions(List<String> allowedTools) {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (String toolName : allowedTools) {
            Tool tool = tools.get(toolName);
            if (tool != null) {
                definitions.add(tool.getDefinition());
            }
        }
        return definitions;
    }

    public List<ToolDefinition> getReadOnlyToolDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (Tool tool : tools.values()) {
            if (tool.isReadOnly()) {
                definitions.add(tool.getDefinition());
            }
        }
        return definitions;
    }

    public List<ToolDefinition> getAllToolDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (Tool tool : tools.values()) {
            definitions.add(tool.getDefinition());
        }
        return definitions;
    }

    public Tool.ToolResult executeTool(String name, Map<String, Object> arguments) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return Tool.ToolResult.failure("Unknown tool: " + name);
        }
        return tool.execute(arguments);
    }
}
