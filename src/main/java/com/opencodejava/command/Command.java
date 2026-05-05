package com.opencodejava.command;

public interface Command {
    /**
     * Get the command name (without the / prefix).
     */
    String getName();

    /**
     * Get the command description.
     */
    String getDescription();

    /**
     * Execute the command with the given arguments.
     * Returns a message to display, or null for no output.
     */
    String execute(String[] args);

    /**
     * Get usage string.
     */
    default String getUsage() {
        return "/" + getName();
    }
}
