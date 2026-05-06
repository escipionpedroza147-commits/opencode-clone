package com.opencodejava.provider;

import com.opencodejava.model.ProviderConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenRouterProviderTest {

    @Test
    void testProviderName() {
        ProviderConfig config = new ProviderConfig();
        config.setType("openrouter");
        config.setBaseUrl("https://openrouter.ai/api/v1");
        config.setModel("test/model");
        config.setApiKey("test-key");

        OpenRouterProvider provider = new OpenRouterProvider(config);
        assertEquals("OpenRouter", provider.getProviderName());
    }

    @Test
    void testModelName() {
        ProviderConfig config = new ProviderConfig();
        config.setModel("anthropic/claude-sonnet-4");
        config.setBaseUrl("https://openrouter.ai/api/v1");
        config.setApiKey("test-key");

        OpenRouterProvider provider = new OpenRouterProvider(config);
        assertEquals("anthropic/claude-sonnet-4", provider.getModelName());
    }

    @Test
    void testSetModel() {
        ProviderConfig config = new ProviderConfig();
        config.setModel("model-a");
        config.setBaseUrl("https://openrouter.ai/api/v1");
        config.setApiKey("test-key");

        OpenRouterProvider provider = new OpenRouterProvider(config);
        provider.setModel("model-b");
        assertEquals("model-b", provider.getModelName());
    }
}
