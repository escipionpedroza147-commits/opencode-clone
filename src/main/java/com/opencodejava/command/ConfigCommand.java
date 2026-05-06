package com.opencodejava.command;

import com.opencodejava.core.App;

public class ConfigCommand implements Command {
    private final App app;

    public ConfigCommand(App app) {
        this.app = app;
    }

    @Override
    public String getName() { return "config"; }

    @Override
    public String getDescription() { return "Show current configuration"; }

    @Override
    public String execute(String[] args) {
        return app.getConfig().getConfigDisplay();
    }

    @Override
    public String getUsage() { return "/config"; }
}
