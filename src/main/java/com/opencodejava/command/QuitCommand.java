package com.opencodejava.command;

public class QuitCommand implements Command {

    @Override
    public String getName() { return "quit"; }

    @Override
    public String getDescription() { return "Exit OpenCode"; }

    @Override
    public String execute(String[] args) {
        System.out.println("Goodbye! 👋");
        System.exit(0);
        return null; // unreachable
    }
}
