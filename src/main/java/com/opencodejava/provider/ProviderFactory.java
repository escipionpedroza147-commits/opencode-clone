package com.opencodejava.provider;

import com.opencodejava.model.ProviderConfig;

public class ProviderFactory {

    public static LLMProvider create(ProviderConfig config) {
        String type = config.getType() != null ? config.getType().toLowerCase() : "openrouter";
        return switch (type) {
            case "lmstudio", "lm_studio", "lm-studio" -> new LMStudioProvider(config);
            default -> new OpenRouterProvider(config);
        };
    }

    public static LLMProvider create(String type, ProviderConfig config) {
        config.setType(type);
        return create(config);
    }
}
