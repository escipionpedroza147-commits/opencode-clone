package com.opencodejava.skill;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Adapter that wraps LangChain4j @Tool annotated classes as OpenCode skills.
 * This enables integration with langchain4j-skills and langchain4j-experimental-skills-shell.
 *
 * Usage:
 *   1. Create a class with methods annotated with @Tool
 *   2. Register it: skillRegistry.register(new LangChain4jSkillAdapter(myToolClass))
 *
 * The adapter discovers all @Tool methods and exposes them as executable skills.
 */
public class LangChain4jSkillAdapter implements Skill {
    private final Object toolInstance;
    private final String name;
    private final String description;
    private final Map<String, Method> toolMethods;
    private final List<ToolSpecification> specifications;

    public LangChain4jSkillAdapter(Object toolInstance) {
        this(toolInstance, null, null);
    }

    public LangChain4jSkillAdapter(Object toolInstance, String name, String description) {
        this.toolInstance = toolInstance;
        this.toolMethods = new LinkedHashMap<>();
        this.specifications = new ArrayList<>();

        // Discover @Tool annotated methods
        for (Method method : toolInstance.getClass().getMethods()) {
            Tool toolAnnotation = method.getAnnotation(Tool.class);
            if (toolAnnotation != null) {
                toolMethods.put(method.getName(), method);
            }
        }

        // Build specifications from annotations
        try {
            this.specifications.addAll(ToolSpecifications.toolSpecificationsFrom(toolInstance));
        } catch (Exception e) {
            // Fallback if ToolSpecifications utility isn't available
        }

        // Set name from parameter or class name
        this.name = name != null ? name :
                toolInstance.getClass().getSimpleName()
                        .replaceAll("([A-Z])", "_$1").toLowerCase()
                        .replaceFirst("^_", "");

        // Set description from parameter or generate from class
        this.description = description != null ? description :
                "LangChain4j skill: " + toolInstance.getClass().getSimpleName() +
                        " (" + toolMethods.size() + " tools)";
    }

    @Override
    public String getName() { return name; }

    @Override
    public String getDescription() { return description; }

    @Override
    public SkillResult execute(Map<String, Object> parameters) {
        String methodName = (String) parameters.get("method");
        if (methodName == null) {
            // If only one method, use it by default
            if (toolMethods.size() == 1) {
                methodName = toolMethods.keySet().iterator().next();
            } else {
                return SkillResult.failure("Specify 'method' parameter. Available: " + toolMethods.keySet());
            }
        }

        Method method = toolMethods.get(methodName);
        if (method == null) {
            return SkillResult.failure("Unknown method: " + methodName + ". Available: " + toolMethods.keySet());
        }

        try {
            // Build arguments from parameters
            Object[] args = buildArguments(method, parameters);
            Object result = method.invoke(toolInstance, args);
            return SkillResult.success(result != null ? result.toString() : "(no output)");
        } catch (Exception e) {
            return SkillResult.failure("Skill execution failed: " + e.getMessage());
        }
    }

    private Object[] buildArguments(Method method, Map<String, Object> parameters) {
        var paramTypes = method.getParameterTypes();
        var paramAnnotations = method.getParameters();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            String paramName = paramAnnotations[i].getName();
            // Try to get from parameters map
            Object value = parameters.get(paramName);
            if (value == null) {
                // Try positional: arg0, arg1, etc.
                value = parameters.get("arg" + i);
            }
            if (value == null && paramTypes[i] == String.class) {
                // Try "input" or "command" as generic param names
                value = parameters.get("input");
                if (value == null) value = parameters.get("command");
            }
            args[i] = convertArg(value, paramTypes[i]);
        }
        return args;
    }

    private Object convertArg(Object value, Class<?> targetType) {
        if (value == null) return null;
        if (targetType.isAssignableFrom(value.getClass())) return value;
        if (targetType == String.class) return value.toString();
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(value.toString());
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(value.toString());
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value.toString());
        }
        return value;
    }

    @Override
    public String getSystemPromptAddition() {
        StringBuilder sb = new StringBuilder();
        sb.append("Available methods in '").append(name).append("' skill:\n");
        for (Map.Entry<String, Method> entry : toolMethods.entrySet()) {
            Tool annotation = entry.getValue().getAnnotation(Tool.class);
            String desc = annotation != null ? String.join(" ", annotation.value()) : "No description";
            sb.append("  - ").append(entry.getKey()).append(": ").append(desc).append("\n");
        }
        return sb.toString();
    }

    @Override
    public boolean isAvailable() {
        return !toolMethods.isEmpty();
    }

    public List<ToolSpecification> getSpecifications() {
        return specifications;
    }

    public Map<String, Method> getToolMethods() {
        return Collections.unmodifiableMap(toolMethods);
    }
}
