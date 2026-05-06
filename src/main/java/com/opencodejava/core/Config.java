package com.opencodejava.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencodejava.model.AgentConfig;
import com.opencodejava.model.ProviderConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Configuration manager for the OpenCode Java application.
 * 
 * <p>This singleton class manages all application configuration including:
 * <ul>
 *   <li>LLM provider settings (API keys, endpoints, models)</li>
 *   <li>Agent configurations (build, plan, general agents)</li>
 *   <li>User-defined commands and skills</li>
 *   <li>Working and data directories</li>
 *   <li>Skills directories for loading custom capabilities</li>
 * </ul>
 * 
 * <p>Configuration is loaded from JSON files in the following order:
 * <ol>
 *   <li>Default configuration from resources</li>
 *   <li>Project-specific opencode.json file</li>
 *   <li>User home directory ~/.opencode-java/opencode.json</li>
 * </ol>
 * 
 * <p>The configuration supports environment variable substitution using the
 * {@code ${VARIABLE_NAME}} syntax in JSON values.
 * 
 * @author OpenCode Java Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class Config {
    /** Singleton instance */
    private static Config instance;
    
    /** JSON object mapper for configuration parsing */
    private final ObjectMapper mapper = new ObjectMapper();

    /** LLM provider configuration (API keys, endpoints, models) */
    private ProviderConfig providerConfig;
    
    /** Map of agent name to agent configuration */
    private Map<String, AgentConfig> agents;
    
    /** User-defined custom commands */
    private Map<String, Map<String, String>> userCommands;
    
    /** User-defined custom skills */
    private Map<String, Map<String, String>> userSkills;
    
    /** List of directories to search for skills */
    private List<String> skillsDirs;
    
    /** Current working directory */
    private String workingDirectory;
    
    /** Data directory for persistent storage */
    private String dataDirectory;
    
    /** Currently active LLM provider */
    private String activeProvider;
    
    /** Currently active model */
    private String activeModel;

    /**
     * Private constructor for singleton pattern.
     * Initializes default values and loads default configuration.
     */
    private Config() {
        this.agents = new LinkedHashMap<>();
        this.userCommands = new LinkedHashMap<>();
        this.userSkills = new LinkedHashMap<>();
        this.skillsDirs = new ArrayList<>();
        this.workingDirectory = System.getProperty("user.dir");
        this.dataDirectory = System.getProperty("user.home") + "/.opencode-java";
        loadDefaults();
    }

    /**
     * Gets the singleton instance of the configuration manager.
     * 
     * @return the singleton Config instance
     */
    public static synchronized Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

    /**
     * Loads default configuration values including provider settings and built-in agents.
     * 
     * <p>Sets up:
     * <ul>
     *   <li>Default OpenRouter provider configuration</li>
     *   <li>Built-in agents: build, plan, and general</li>
     *   <li>Environment variable checking for API keys</li>
     * </ul>
     */
    private void loadDefaults() {
        // Load default provider config
        providerConfig = new ProviderConfig();
        providerConfig.setType("openrouter");
        providerConfig.setBaseUrl("https://openrouter.ai/api/v1");
        providerConfig.setModel("anthropic/claude-sonnet-4");
        providerConfig.setTemperature(0.7);
        providerConfig.setMaxTokens(16384);

        // Check environment for API key
        String orKey = System.getenv("OPENROUTER_API_KEY");
        if (orKey != null && !orKey.isEmpty()) {
            providerConfig.setApiKey(orKey);
        }

        // Default agents
        agents.put("build", new AgentConfig(
                "build",
                "Full-access coding agent with all tools",
                loadPrompt("build-agent.txt"),
                List.of("file_read", "file_write", "file_edit", "bash", "search", "list_files", "git",
                        "web_search", "web_fetch", "image_gen"),
                false
        ));

        agents.put("plan", new AgentConfig(
                "plan",
                "Read-only analysis and planning agent",
                loadPrompt("plan-agent.txt"),
                List.of("file_read", "search", "list_files", "web_search", "web_fetch"),
                true
        ));

        agents.put("general", new AgentConfig(
                "general",
                "General-purpose subagent for complex searches and multistep tasks",
                loadPrompt("general-agent.txt"),
                List.of("file_read", "bash", "search", "list_files", "git", "web_search", "web_fetch"),
                false
        ));

        activeProvider = "openrouter";
        activeModel = providerConfig.getModel();
    }

    private String loadPrompt(String filename) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("prompts/" + filename)) {
            if (is != null) {
                return new String(is.readAllBytes());
            }
        } catch (IOException e) {
            // fall through
        }
        return getDefaultPrompt(filename);
    }

    private String getDefaultPrompt(String filename) {
        return switch (filename) {
            case "build-agent.txt" -> """
                    You are a coding assistant with full access to the filesystem and shell.
                    You can read files, write files, execute commands, and search the codebase.
                    Help the user build, debug, and modify their code.
                    Always explain what you're doing and why.
                    When writing code, write complete implementations - no TODOs or stubs.
                    """;
            case "plan-agent.txt" -> """
                    You are an analysis and planning agent. You can read files and search the codebase,
                    but you cannot modify files or execute commands.
                    Help the user understand code, plan changes, and think through approaches.
                    Be thorough in your analysis and suggest concrete implementation steps.
                    """;
            case "general-agent.txt" -> """
                    You are a general-purpose assistant for complex research and multistep tasks.
                    You can read files, execute commands, and search the codebase.
                    Focus on thorough investigation and providing comprehensive answers.
                    """;
            default -> "You are a helpful coding assistant.";
        };
    }

    /**
     * Interpolate environment variables in a string.
     * Supports ${VAR_NAME} syntax.
     */
    public static String interpolateEnvVars(String value) {
        if (value == null || !value.contains("${")) return value;
        Pattern pattern = Pattern.compile("\\$\\{([^}]+)}");
        Matcher matcher = pattern.matcher(value);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String envName = matcher.group(1);
            String envValue = System.getenv(envName);
            matcher.appendReplacement(result, envValue != null ? Matcher.quoteReplacement(envValue) : "");
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public void loadFromFile(Path configPath) throws IOException {
        if (!Files.exists(configPath)) return;

        // Read and interpolate environment variables
        String rawJson = Files.readString(configPath);
        String interpolated = interpolateEnvVars(rawJson);
        JsonNode root = mapper.readTree(interpolated);

        if (root.has("provider")) {
            JsonNode prov = root.get("provider");
            if (prov.has("type")) providerConfig.setType(prov.get("type").asText());
            if (prov.has("api_key")) providerConfig.setApiKey(prov.get("api_key").asText());
            if (prov.has("base_url")) providerConfig.setBaseUrl(prov.get("base_url").asText());
            if (prov.has("model")) providerConfig.setModel(prov.get("model").asText());
            if (prov.has("temperature")) providerConfig.setTemperature(prov.get("temperature").asDouble());
            if (prov.has("max_tokens")) providerConfig.setMaxTokens(prov.get("max_tokens").asInt());
        }

        if (root.has("agents")) {
            JsonNode agentsNode = root.get("agents");
            Iterator<Map.Entry<String, JsonNode>> fields = agentsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String name = entry.getKey();
                JsonNode agentNode = entry.getValue();

                AgentConfig agentConfig = new AgentConfig();
                agentConfig.setName(name);
                if (agentNode.has("description")) agentConfig.setDescription(agentNode.get("description").asText());
                if (agentNode.has("system_prompt")) agentConfig.setSystemPrompt(agentNode.get("system_prompt").asText());
                if (agentNode.has("read_only")) agentConfig.setReadOnly(agentNode.get("read_only").asBoolean());
                if (agentNode.has("tools")) {
                    List<String> tools = new ArrayList<>();
                    agentNode.get("tools").forEach(t -> tools.add(t.asText()));
                    agentConfig.setTools(tools);
                }
                agents.put(name, agentConfig);
            }
        }

        if (root.has("working_directory")) {
            workingDirectory = root.get("working_directory").asText();
        }

        // Load user-defined commands
        if (root.has("commands")) {
            JsonNode cmdsNode = root.get("commands");
            Iterator<Map.Entry<String, JsonNode>> cmdFields = cmdsNode.fields();
            while (cmdFields.hasNext()) {
                Map.Entry<String, JsonNode> entry = cmdFields.next();
                Map<String, String> cmdDef = new LinkedHashMap<>();
                JsonNode cmdNode = entry.getValue();
                if (cmdNode.has("description")) cmdDef.put("description", cmdNode.get("description").asText());
                if (cmdNode.has("script")) cmdDef.put("script", cmdNode.get("script").asText());
                if (cmdNode.has("prompt")) cmdDef.put("prompt", cmdNode.get("prompt").asText());
                if (cmdNode.has("timeout")) cmdDef.put("timeout", cmdNode.get("timeout").asText());
                userCommands.put(entry.getKey(), cmdDef);
            }
        }

        // Load user-defined skills
        if (root.has("skills")) {
            JsonNode skillsNode = root.get("skills");
            Iterator<Map.Entry<String, JsonNode>> skillFields = skillsNode.fields();
            while (skillFields.hasNext()) {
                Map.Entry<String, JsonNode> entry = skillFields.next();
                Map<String, String> skillDef = new LinkedHashMap<>();
                JsonNode skillNode = entry.getValue();
                if (skillNode.has("description")) skillDef.put("description", skillNode.get("description").asText());
                if (skillNode.has("type")) skillDef.put("type", skillNode.get("type").asText());
                if (skillNode.has("script")) skillDef.put("script", skillNode.get("script").asText());
                if (skillNode.has("system_prompt")) skillDef.put("system_prompt", skillNode.get("system_prompt").asText());
                if (skillNode.has("timeout")) skillDef.put("timeout", skillNode.get("timeout").asText());
                userSkills.put(entry.getKey(), skillDef);
            }
        }

        // Load skills_dirs for OpenClaw-style SKILL.md loading
        if (root.has("skills_dirs")) {
            JsonNode dirsNode = root.get("skills_dirs");
            if (dirsNode.isArray()) {
                for (JsonNode dirNode : dirsNode) {
                    skillsDirs.add(dirNode.asText());
                }
            }
        }

        activeModel = providerConfig.getModel();
        activeProvider = providerConfig.getType();
    }

    public void loadFromProjectOrHome() {
        // Try project-local config first
        Path projectConfig = Path.of(workingDirectory, "opencode.json");
        if (Files.exists(projectConfig)) {
            try {
                loadFromFile(projectConfig);
                return;
            } catch (IOException e) {
                System.err.println("Warning: Failed to load project config: " + e.getMessage());
            }
        }

        // Try home directory config
        Path homeConfig = Path.of(System.getProperty("user.home"), ".opencode.json");
        if (Files.exists(homeConfig)) {
            try {
                loadFromFile(homeConfig);
            } catch (IOException e) {
                System.err.println("Warning: Failed to load home config: " + e.getMessage());
            }
        }
    }

    public ProviderConfig getProviderConfig() { return providerConfig; }
    public Map<String, AgentConfig> getAgents() { return agents; }
    public Map<String, Map<String, String>> getUserCommands() { return userCommands; }
    public Map<String, Map<String, String>> getUserSkills() { return userSkills; }
    public List<String> getSkillsDirs() { return skillsDirs; }
    public String getWorkingDirectory() { return workingDirectory; }
    public String getDataDirectory() { return dataDirectory; }
    public String getActiveProvider() { return activeProvider; }
    public void setActiveProvider(String provider) { this.activeProvider = provider; }
    public String getActiveModel() { return activeModel; }
    public void setActiveModel(String model) { this.activeModel = model; }

    public AgentConfig getAgentConfig(String name) {
        return agents.get(name);
    }

    /**
     * Get a formatted display of current configuration.
     */
    public String getConfigDisplay() {
        StringBuilder sb = new StringBuilder();
        sb.append("⚙️  Current Configuration:\n\n");
        sb.append("Provider: ").append(activeProvider).append("\n");
        sb.append("Model: ").append(activeModel).append("\n");
        sb.append("Base URL: ").append(providerConfig.getBaseUrl()).append("\n");
        sb.append("API Key: ").append(providerConfig.getApiKey() != null ? "****" + providerConfig.getApiKey().substring(Math.max(0, providerConfig.getApiKey().length() - 4)) : "(not set)").append("\n");
        sb.append("Temperature: ").append(providerConfig.getTemperature()).append("\n");
        sb.append("Max Tokens: ").append(providerConfig.getMaxTokens()).append("\n");
        sb.append("Working Dir: ").append(workingDirectory).append("\n");
        sb.append("Data Dir: ").append(dataDirectory).append("\n");
        sb.append("\nAgents: ").append(String.join(", ", agents.keySet())).append("\n");
        if (!userCommands.isEmpty()) {
            sb.append("User Commands: ").append(String.join(", ", userCommands.keySet())).append("\n");
        }
        return sb.toString();
    }

    public void ensureDataDirectory() {
        File dir = new File(dataDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File sessionsDir = new File(dataDirectory + "/sessions");
        if (!sessionsDir.exists()) {
            sessionsDir.mkdirs();
        }
    }
}
