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

public class Config {
    private static Config instance;
    private final ObjectMapper mapper = new ObjectMapper();

    private ProviderConfig providerConfig;
    private Map<String, AgentConfig> agents;
    private String workingDirectory;
    private String dataDirectory;
    private String activeProvider;
    private String activeModel;

    private Config() {
        this.agents = new LinkedHashMap<>();
        this.workingDirectory = System.getProperty("user.dir");
        this.dataDirectory = System.getProperty("user.home") + "/.opencode-java";
        loadDefaults();
    }

    public static synchronized Config getInstance() {
        if (instance == null) {
            instance = new Config();
        }
        return instance;
    }

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
                List.of("file_read", "file_write", "file_edit", "bash", "search", "list_files", "git"),
                false
        ));

        agents.put("plan", new AgentConfig(
                "plan",
                "Read-only analysis and planning agent",
                loadPrompt("plan-agent.txt"),
                List.of("file_read", "search", "list_files"),
                true
        ));

        agents.put("general", new AgentConfig(
                "general",
                "General-purpose subagent for complex searches and multistep tasks",
                loadPrompt("general-agent.txt"),
                List.of("file_read", "bash", "search", "list_files", "git"),
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

    public void loadFromFile(Path configPath) throws IOException {
        if (!Files.exists(configPath)) return;

        JsonNode root = mapper.readTree(configPath.toFile());

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
    public String getWorkingDirectory() { return workingDirectory; }
    public String getDataDirectory() { return dataDirectory; }
    public String getActiveProvider() { return activeProvider; }
    public void setActiveProvider(String provider) { this.activeProvider = provider; }
    public String getActiveModel() { return activeModel; }
    public void setActiveModel(String model) { this.activeModel = model; }

    public AgentConfig getAgentConfig(String name) {
        return agents.get(name);
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
