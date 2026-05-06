package com.opencodejava.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProviderConfigTest {

    @Test
    void testDefaultValues() {
        ProviderConfig config = new ProviderConfig();
        assertEquals(0.7, config.getTemperature());
        assertEquals(4096, config.getMaxTokens());
    }

    @Test
    void testSettersAndGetters() {
        ProviderConfig config = new ProviderConfig();
        config.setType("openrouter");
        config.setApiKey("sk-test-key");
        config.setBaseUrl("https://api.example.com");
        config.setModel("gpt-4");
        config.setTemperature(0.9);
        config.setMaxTokens(8192);

        assertEquals("openrouter", config.getType());
        assertEquals("sk-test-key", config.getApiKey());
        assertEquals("https://api.example.com", config.getBaseUrl());
        assertEquals("gpt-4", config.getModel());
        assertEquals(0.9, config.getTemperature());
        assertEquals(8192, config.getMaxTokens());
    }
}
