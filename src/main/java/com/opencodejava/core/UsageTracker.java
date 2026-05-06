package com.opencodejava.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks token usage and estimated costs per session.
 * Supports hardcoded pricing for common models.
 */
public class UsageTracker {
    private static UsageTracker instance;

    private long totalPromptTokens;
    private long totalCompletionTokens;
    private int requestCount;
    private final Map<String, ModelPricing> modelPricing;
    private String currentModel;

    private UsageTracker() {
        this.totalPromptTokens = 0;
        this.totalCompletionTokens = 0;
        this.requestCount = 0;
        this.modelPricing = new LinkedHashMap<>();
        this.currentModel = "";
        initializePricing();
    }

    public static synchronized UsageTracker getInstance() {
        if (instance == null) {
            instance = new UsageTracker();
        }
        return instance;
    }

    private void initializePricing() {
        // Prices per 1M tokens: (input, output)
        modelPricing.put("claude-sonnet", new ModelPricing(3.0, 15.0));
        modelPricing.put("claude-sonnet-4", new ModelPricing(3.0, 15.0));
        modelPricing.put("claude-3.5-sonnet", new ModelPricing(3.0, 15.0));
        modelPricing.put("claude-opus", new ModelPricing(15.0, 75.0));
        modelPricing.put("claude-haiku", new ModelPricing(0.25, 1.25));
        modelPricing.put("gpt-4o", new ModelPricing(2.5, 10.0));
        modelPricing.put("gpt-4o-mini", new ModelPricing(0.15, 0.6));
        modelPricing.put("gpt-4-turbo", new ModelPricing(10.0, 30.0));
        modelPricing.put("deepseek-chat", new ModelPricing(0.14, 0.28));
        modelPricing.put("deepseek-coder", new ModelPricing(0.14, 0.28));
        modelPricing.put("llama", new ModelPricing(0.0, 0.0));
        modelPricing.put("gemma", new ModelPricing(0.0, 0.0));
        modelPricing.put("mistral", new ModelPricing(0.2, 0.6));
        modelPricing.put("mixtral", new ModelPricing(0.6, 0.6));
        modelPricing.put("qwen", new ModelPricing(0.0, 0.0));
    }

    /**
     * Add usage from a response.
     */
    public void addUsage(String model, int promptTokens, int completionTokens) {
        this.currentModel = model;
        this.totalPromptTokens += promptTokens;
        this.totalCompletionTokens += completionTokens;
        this.requestCount++;
    }

    /**
     * Get total tokens (prompt + completion).
     */
    public long getTotalTokens() {
        return totalPromptTokens + totalCompletionTokens;
    }

    /**
     * Get estimated cost in USD based on model pricing.
     */
    public double getEstimatedCost() {
        ModelPricing pricing = findPricing(currentModel);
        if (pricing == null) return 0.0;

        double inputCost = (totalPromptTokens / 1_000_000.0) * pricing.inputPricePerMillion;
        double outputCost = (totalCompletionTokens / 1_000_000.0) * pricing.outputPricePerMillion;
        return inputCost + outputCost;
    }

    /**
     * Get a formatted session summary.
     */
    public String getSessionSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 Session Usage:\n");
        sb.append("─────────────────────────────\n");
        sb.append("  Model:        ").append(currentModel.isEmpty() ? "unknown" : currentModel).append("\n");
        sb.append("  Requests:     ").append(requestCount).append("\n");
        sb.append("  Prompt:       ").append(formatTokens(totalPromptTokens)).append("\n");
        sb.append("  Completion:   ").append(formatTokens(totalCompletionTokens)).append("\n");
        sb.append("  Total:        ").append(formatTokens(getTotalTokens())).append("\n");
        sb.append("  Est. Cost:    $").append(String.format("%.4f", getEstimatedCost())).append("\n");
        sb.append("─────────────────────────────");
        return sb.toString();
    }

    /**
     * Get a short status string for the prompt.
     */
    public String getPromptStatus() {
        long total = getTotalTokens();
        if (total == 0) return "";
        return formatTokens(total);
    }

    /**
     * Reset session tracking.
     */
    public void reset() {
        totalPromptTokens = 0;
        totalCompletionTokens = 0;
        requestCount = 0;
    }

    public long getPromptTokens() { return totalPromptTokens; }
    public long getCompletionTokens() { return totalCompletionTokens; }
    public int getRequestCount() { return requestCount; }

    private ModelPricing findPricing(String model) {
        if (model == null || model.isEmpty()) return null;
        String lower = model.toLowerCase();

        // Try exact match first
        for (Map.Entry<String, ModelPricing> entry : modelPricing.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String formatTokens(long tokens) {
        if (tokens >= 1_000_000) {
            return String.format("%.1fM", tokens / 1_000_000.0);
        } else if (tokens >= 1_000) {
            return String.format("%.1fK", tokens / 1_000.0);
        }
        return String.valueOf(tokens);
    }

    private record ModelPricing(double inputPricePerMillion, double outputPricePerMillion) {}
}
