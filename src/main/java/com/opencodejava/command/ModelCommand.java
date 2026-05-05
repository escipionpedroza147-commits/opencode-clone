package com.opencodejava.command;

import com.opencodejava.core.App;

public class ModelCommand implements Command {
    private final App app;

    public ModelCommand(App app) {
        this.app = app;
    }

    @Override
    public String getName() { return "model"; }

    @Override
    public String getDescription() { return "Show or change the active model"; }

    @Override
    public String getUsage() { return "/model [model-name]"; }

    @Override
    public String execute(String[] args) {
        if (args.length == 0) {
            return String.format("Current model: %s (provider: %s)",
                    app.getProvider().getModelName(),
                    app.getProvider().getProviderName());
        }

        String newModel = args[0];
        app.getProvider().setModel(newModel);
        app.getConfig().setActiveModel(newModel);
        return "Model changed to: " + newModel;
    }
}
