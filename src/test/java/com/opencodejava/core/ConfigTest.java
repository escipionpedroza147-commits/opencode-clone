package com.opencodejava.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @Test
    void testInterpolateEnvVars_noVars() {
        String input = "hello world";
        assertEquals("hello world", Config.interpolateEnvVars(input));
    }

    @Test
    void testInterpolateEnvVars_withExistingVar() {
        // HOME should exist on any Unix/macOS system
        String input = "home is ${HOME}";
        String result = Config.interpolateEnvVars(input);
        assertFalse(result.contains("${HOME}"));
        assertTrue(result.startsWith("home is /"));
    }

    @Test
    void testInterpolateEnvVars_withMissingVar() {
        String input = "key=${NONEXISTENT_VAR_12345}";
        String result = Config.interpolateEnvVars(input);
        assertEquals("key=", result);
    }

    @Test
    void testInterpolateEnvVars_nullInput() {
        assertNull(Config.interpolateEnvVars(null));
    }

    @Test
    void testInterpolateEnvVars_multipleVars() {
        String input = "${HOME}:${PATH}";
        String result = Config.interpolateEnvVars(input);
        assertFalse(result.contains("${HOME}"));
        assertFalse(result.contains("${PATH}"));
    }

    @Test
    void testGetInstance_returnsSameInstance() {
        Config c1 = Config.getInstance();
        Config c2 = Config.getInstance();
        assertSame(c1, c2);
    }

    @Test
    void testDefaultConfig_hasProvider() {
        Config config = Config.getInstance();
        assertNotNull(config.getProviderConfig());
        // Provider type may be altered by config file loading in other tests
        assertNotNull(config.getProviderConfig().getType());
        assertTrue(config.getProviderConfig().getType().equals("openrouter") ||
                config.getProviderConfig().getType().equals("lmstudio"));
    }

    @Test
    void testDefaultConfig_hasAgents() {
        Config config = Config.getInstance();
        assertNotNull(config.getAgents());
        assertTrue(config.getAgents().containsKey("build"));
        assertTrue(config.getAgents().containsKey("plan"));
    }

    @Test
    void testLoadFromFile_validJson(@TempDir Path tempDir) throws IOException {
        String json = """
                {
                    "provider": {
                        "type": "lmstudio",
                        "base_url": "http://localhost:1234/v1",
                        "model": "test-model",
                        "temperature": 0.5,
                        "max_tokens": 8192
                    }
                }
                """;
        Path configFile = tempDir.resolve("opencode.json");
        Files.writeString(configFile, json);

        Config config = Config.getInstance();
        config.loadFromFile(configFile);

        assertEquals("lmstudio", config.getProviderConfig().getType());
        assertEquals("http://localhost:1234/v1", config.getProviderConfig().getBaseUrl());
        assertEquals("test-model", config.getProviderConfig().getModel());
        assertEquals(0.5, config.getProviderConfig().getTemperature());
        assertEquals(8192, config.getProviderConfig().getMaxTokens());
    }

    @Test
    void testGetConfigDisplay() {
        Config config = Config.getInstance();
        String display = config.getConfigDisplay();
        assertNotNull(display);
        assertTrue(display.contains("Provider:"));
        assertTrue(display.contains("Model:"));
        assertTrue(display.contains("Temperature:"));
    }
}
