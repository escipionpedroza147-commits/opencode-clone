package com.opencodejava.command;

/**
 * Interface for user commands that can be executed in the terminal interface.
 * 
 * <p>Commands provide administrative and utility functions for the OpenCode application,
 * such as configuration management, session control, memory operations, and help display.
 * All commands are prefixed with '/' when typed by the user.
 * 
 * <h2>Built-in Commands:</h2>
 * <ul>
 *   <li><strong>/help</strong> - Display available commands and usage</li>
 *   <li><strong>/config</strong> - View and modify configuration settings</li>
 *   <li><strong>/agents</strong> - Switch between different AI agents</li>
 *   <li><strong>/session</strong> - Manage conversation sessions</li>
 *   <li><strong>/memory</strong> - View and manage persistent memory</li>
 *   <li><strong>/usage</strong> - Display token usage statistics</li>
 *   <li><strong>/export</strong> - Export conversations to files</li>
 *   <li><strong>/clear</strong> - Clear conversation history</li>
 *   <li><strong>/quit</strong> - Exit the application</li>
 * </ul>
 * 
 * <p>Commands are registered in the {@link CommandRegistry} and executed
 * when the user types them in the terminal interface.
 * 
 * @author OpenCode Java Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface Command {
    /**
     * Gets the command name without the '/' prefix.
     * 
     * <p>This name is used to identify and execute the command when
     * the user types '/' followed by this name.
     * 
     * @return the command name (e.g., "help", "config", "quit")
     */
    String getName();

    /**
     * Gets a human-readable description of what this command does.
     * 
     * <p>This description is displayed in help output and command listings
     * to help users understand the command's purpose.
     * 
     * @return the command description
     */
    String getDescription();

    /**
     * Executes the command with the provided arguments.
     * 
     * <p>The arguments array contains the command-line arguments passed
     * after the command name, split by whitespace. The command name itself
     * is not included in the arguments.
     * 
     * @param args the command arguments (excluding the command name)
     * @return a message to display to the user, or {@code null} for no output
     * @throws RuntimeException if command execution fails
     */
    String execute(String[] args);

    /**
     * Gets the usage string showing how to use this command.
     * 
     * <p>The default implementation returns the command name with the '/' prefix.
     * Commands with complex argument structures should override this method
     * to provide more detailed usage information.
     * 
     * @return the usage string (e.g., "/help", "/config set key value")
     */
    default String getUsage() {
        return "/" + getName();
    }
}
