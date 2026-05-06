package com.opencodejava.core;

import com.opencodejava.agent.AgentManager;
import com.opencodejava.command.CommandRegistry;
import com.opencodejava.core.Memory;
import com.opencodejava.provider.ProviderFactory;
import com.opencodejava.provider.LLMProvider;
import com.opencodejava.skill.SkillRegistry;
import com.opencodejava.tool.ToolRegistry;
import com.opencodejava.ui.TerminalUI;

public class App {
    private final Config config;
    private final SessionManager sessionManager;
    private final ToolRegistry toolRegistry;
    private final SkillRegistry skillRegistry;
    private final CommandRegistry commandRegistry;
    private final AgentManager agentManager;
    private final LLMProvider provider;
    private final TerminalUI ui;

    public App() {
        this.config = Config.getInstance();
        config.loadFromProjectOrHome();
        config.ensureDataDirectory();

        this.sessionManager = new SessionManager(config);
        this.toolRegistry = new ToolRegistry(config);
        this.skillRegistry = SkillRegistry.createDefault(
                config.getWorkingDirectory(), config.getUserSkills());
        this.provider = ProviderFactory.create(config.getProviderConfig());
        this.agentManager = new AgentManager(config, provider, toolRegistry, sessionManager, skillRegistry);
        this.commandRegistry = new CommandRegistry(this);
        this.ui = new TerminalUI(this);
    }

    public void run() {
        // Load memory system on startup
        Memory.getInstance().load();

        sessionManager.newConversation(null);
        ui.start();
    }

    public Config getConfig() { return config; }
    public SessionManager getSessionManager() { return sessionManager; }
    public ToolRegistry getToolRegistry() { return toolRegistry; }
    public SkillRegistry getSkillRegistry() { return skillRegistry; }
    public CommandRegistry getCommandRegistry() { return commandRegistry; }
    public AgentManager getAgentManager() { return agentManager; }
    public LLMProvider getProvider() { return provider; }
    public TerminalUI getUi() { return ui; }

    public static void main(String[] args) {
        System.out.println("OpenCode Java v1.0.0");

        // Check for API key
        Config cfg = Config.getInstance();
        cfg.loadFromProjectOrHome();

        if (cfg.getProviderConfig().getApiKey() == null || cfg.getProviderConfig().getApiKey().isEmpty()) {
            String envKey = System.getenv("OPENROUTER_API_KEY");
            if (envKey == null || envKey.isEmpty()) {
                envKey = System.getenv("LMSTUDIO_API_KEY");
            }
            if (envKey == null || envKey.isEmpty()) {
                System.err.println("Error: No API key configured.");
                System.err.println("Set OPENROUTER_API_KEY or LMSTUDIO_API_KEY environment variable,");
                System.err.println("or add 'api_key' to your opencode.json config file.");
                System.exit(1);
            }
        }

        App app = new App();
        app.run();
    }
}
