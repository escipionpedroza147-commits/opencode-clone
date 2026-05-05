package com.opencodejava.command;

public class HelpCommand implements Command {
    private final CommandRegistry registry;

    public HelpCommand(CommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String getName() { return "help"; }

    @Override
    public String getDescription() { return "Show available commands"; }

    @Override
    public String execute(String[] args) {
        StringBuilder sb = new StringBuilder();
        sb.append("Available commands:\n\n");
        for (Command cmd : registry.getAllCommands()) {
            sb.append(String.format("  %-12s %s\n", "/" + cmd.getName(), cmd.getDescription()));
        }
        sb.append("\nKeyboard shortcuts:\n");
        sb.append("  Tab          Switch between agents\n");
        sb.append("  Ctrl+C       Cancel current operation\n");
        sb.append("  Ctrl+D       Quit\n");
        return sb.toString();
    }
}
