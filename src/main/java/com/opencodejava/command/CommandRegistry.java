package com.opencodejava.command;

import com.opencodejava.core.App;

import java.util.*;

public class CommandRegistry {
    private final Map<String, Command> commands;
    private final App app;

    public CommandRegistry(App app) {
        this.app = app;
        this.commands = new LinkedHashMap<>();
        registerDefaults();
    }

    private void registerDefaults() {
        register(new HelpCommand(this));
        register(new ClearCommand(app));
        register(new CompactCommand(app));
        register(new QuitCommand());
        register(new ModelCommand(app));
        register(new AgentsCommand(app));
    }

    public void register(Command command) {
        commands.put(command.getName().toLowerCase(), command);
    }

    public boolean isCommand(String input) {
        return input != null && input.startsWith("/");
    }

    public String executeCommand(String input) {
        if (!isCommand(input)) return null;

        String[] parts = input.substring(1).split("\\s+", 2);
        String cmdName = parts[0].toLowerCase();
        String[] args = parts.length > 1 ? parts[1].split("\\s+") : new String[0];

        Command cmd = commands.get(cmdName);
        if (cmd == null) {
            return "Unknown command: /" + cmdName + ". Type /help for available commands.";
        }

        return cmd.execute(args);
    }

    public Collection<Command> getAllCommands() {
        return commands.values();
    }

    public Command getCommand(String name) {
        return commands.get(name.toLowerCase());
    }
}
