package com.opencodejava.model;

public class ProviderConfig {
    private String type; // "openrouter" or "lmstudio"
    private String apiKey;
    private String baseUrl;
    private String model;
    private double temperature;
    private int maxTokens;

    public ProviderConfig() {
        this.temperature = 0.7;
        this.maxTokens = 4096;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
}
