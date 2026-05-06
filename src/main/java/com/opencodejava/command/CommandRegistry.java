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
        registerUserCommands();
    }

    private void registerDefaults() {
        register(new HelpCommand(this));
        register(new ClearCommand(app));
        register(new CompactCommand(app));
        register(new QuitCommand());
        register(new ModelCommand(app));
        register(new AgentsCommand(app));
        register(new RememberCommand());
        register(new MemoryCommand());
        register(new UsageCommand());
        register(new ConfigCommand(app));
        register(new SessionCommand(app));
        register(new ExportCommand(app));
        register(new UndoCommand(app));
        register(new SearchCommand(app));
    }

    /**
     * Load user-defined commands from config.
     * These are defined in opencode.json under "commands": { ... }
     */
    private void registerUserCommands() {
        Map<String, Map<String, String>> userCmds = app.getConfig().getUserCommands();
        if (userCmds == null || userCmds.isEmpty()) return;

        for (Map.Entry<String, Map<String, String>> entry : userCmds.entrySet()) {
            String name = entry.getKey();
            Map<String, String> def = entry.getValue();
            String description = def.get("description");
            String script = def.get("script");
            String prompt = def.get("prompt");
            int timeout = 60;
            if (def.containsKey("timeout")) {
                try { timeout = Integer.parseInt(def.get("timeout")); } catch (NumberFormatException ignored) {}
            }
            register(new UserCommand(name, description, script, prompt,
                    app.getConfig().getWorkingDirectory(), timeout));
        }
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
