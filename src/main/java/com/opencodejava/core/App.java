package com.opencodejava.core;

import com.opencodejava.agent.AgentManager;
import com.opencodejava.command.CommandRegistry;
import com.opencodejava.core.Memory;
import com.opencodejava.provider.ProviderFactory;
import com.opencodejava.provider.LLMProvider;
import com.opencodejava.skill.SkillRegistry;
import com.opencodejava.tool.ToolRegistry;
import com.opencodejava.ui.TerminalUI;

/**
 * OpenCode Java - A terminal-based AI coding agent application.
 * 
 * <p>This is the main application class that orchestrates all components of the OpenCode system,
 * including agent management, tool execution, skill loading, and terminal user interface.
 * The application provides a multi-agent system for AI-powered coding assistance with features
 * like file manipulation, bash execution, git operations, web search, and persistent memory.</p>
 * 
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Multi-agent system with build, plan, and general agents</li>
 *   <li>Built-in tools for file operations, bash execution, git, web search</li>
 *   <li>Streaming responses with real-time progress indicators</li>
 *   <li>Persistent memory system across sessions</li>
 *   <li>Support for multiple LLM providers (OpenRouter, LM Studio)</li>
 *   <li>Session management and conversation export</li>
 *   <li>Token usage tracking and destructive command protection</li>
 * </ul>
 * 
 * <h2>Architecture:</h2>
 * <p>The application follows a modular architecture with clear separation of concerns:
 * configuration management, session handling, tool registry, skill system, agent management,
 * and user interface components.</p>
 * 
 * @author OpenCode Java Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class App {
    /** Application configuration manager */
    private final Config config;
    
    /** Session and conversation management */
    private final SessionManager sessionManager;
    
    /** Registry for available tools (file ops, bash, git, etc.) */
    private final ToolRegistry toolRegistry;
    
    /** Registry for loaded skills and capabilities */
    private final SkillRegistry skillRegistry;
    
    /** Registry for available commands */
    private final CommandRegistry commandRegistry;
    
    /** Manager for AI agents (build, plan, general) */
    private final AgentManager agentManager;
    
    /** LLM provider interface (OpenRouter, LM Studio, etc.) */
    private final LLMProvider provider;
    
    /** Terminal user interface */
    private final TerminalUI ui;

    /**
     * Constructs a new OpenCode application instance.
     * 
     * <p>Initializes all core components in the correct order:
     * <ol>
     *   <li>Loads configuration from project or home directory</li>
     *   <li>Ensures data directory exists</li>
     *   <li>Initializes session manager</li>
     *   <li>Sets up tool and skill registries</li>
     *   <li>Creates LLM provider</li>
     *   <li>Initializes agent manager</li>
     *   <li>Sets up command registry and terminal UI</li>
     * </ol>
     * 
     * @throws RuntimeException if configuration loading or component initialization fails
     */
    public App() {
        this.config = Config.getInstance();
        config.loadFromProjectOrHome();
        config.ensureDataDirectory();

        this.sessionManager = new SessionManager(config);
        this.toolRegistry = new ToolRegistry(config);
        this.skillRegistry = SkillRegistry.createDefault(
                config.getWorkingDirectory(), config.getUserSkills(), config.getSkillsDirs());
        this.provider = ProviderFactory.create(config.getProviderConfig());
        this.agentManager = new AgentManager(config, provider, toolRegistry, sessionManager, skillRegistry);
        this.commandRegistry = new CommandRegistry(this);
        this.ui = new TerminalUI(this);
    }

    /**
     * Starts the OpenCode application.
     * 
     * <p>This method:
     * <ol>
     *   <li>Loads the persistent memory system</li>
     *   <li>Creates a new conversation session</li>
     *   <li>Starts the terminal user interface</li>
     * </ol>
     * 
     * <p>The method blocks until the user exits the application.
     * 
     * @throws RuntimeException if memory loading or UI startup fails
     */
    public void run() {
        // Load memory system on startup
        Memory.getInstance().load();

        sessionManager.newConversation(null);
        ui.start();
    }

    /**
     * Gets the application configuration.
     * @return the configuration instance
     */
    public Config getConfig() { return config; }
    
    /**
     * Gets the session manager.
     * @return the session manager instance
     */
    public SessionManager getSessionManager() { return sessionManager; }
    
    /**
     * Gets the tool registry.
     * @return the tool registry instance
     */
    public ToolRegistry getToolRegistry() { return toolRegistry; }
    
    /**
     * Gets the skill registry.
     * @return the skill registry instance
     */
    public SkillRegistry getSkillRegistry() { return skillRegistry; }
    
    /**
     * Gets the command registry.
     * @return the command registry instance
     */
    public CommandRegistry getCommandRegistry() { return commandRegistry; }
    
    /**
     * Gets the agent manager.
     * @return the agent manager instance
     */
    public AgentManager getAgentManager() { return agentManager; }
    
    /**
     * Gets the LLM provider.
     * @return the LLM provider instance
     */
    public LLMProvider getProvider() { return provider; }
    
    /**
     * Gets the terminal UI.
     * @return the terminal UI instance
     */
    public TerminalUI getUi() { return ui; }

    /**
     * Main entry point for the OpenCode Java application.
     * 
     * <p>Performs startup validation and launches the application:
     * <ol>
     *   <li>Displays application version information</li>
     *   <li>Validates API key configuration from config file or environment variables</li>
     *   <li>Creates and runs the application instance</li>
     * </ol>
     * 
     * <p>Supported environment variables:
     * <ul>
     *   <li>{@code OPENROUTER_API_KEY} - API key for OpenRouter service</li>
     *   <li>{@code LMSTUDIO_API_KEY} - API key for LM Studio service</li>
     * </ul>
     * 
     * @param args command line arguments (currently unused)
     * @throws SystemExit with code 1 if no API key is configured
     */
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
