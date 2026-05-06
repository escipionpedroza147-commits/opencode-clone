package com.opencodejava.agent;

import com.opencodejava.model.AgentConfig;
import com.opencodejava.provider.LLMProvider;
import com.opencodejava.tool.ToolRegistry;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Executes multiple agent tasks in parallel using CompletableFuture.
 * Each task runs on its own agent instance with its own conversation history.
 */
public class ParallelExecutor {
    private final LLMProvider provider;
    private final ToolRegistry toolRegistry;
    private final ExecutorService executor;

    public ParallelExecutor(LLMProvider provider, ToolRegistry toolRegistry) {
        this.provider = provider;
        this.toolRegistry = toolRegistry;
        this.executor = Executors.newFixedThreadPool(
                Math.min(Runtime.getRuntime().availableProcessors(), 4));
    }

    /**
     * Execute multiple agent tasks in parallel.
     *
     * @param tasks Map of agent config to task description
     * @param onProgress Optional callback for progress updates (thread-safe)
     * @return Map of agent name to result
     */
    public Map<String, String> executeParallel(Map<AgentConfig, String> tasks, Consumer<String> onProgress) {
        Map<String, String> results = new ConcurrentHashMap<>();

        List<CompletableFuture<Void>> futures = tasks.entrySet().stream()
                .map(entry -> CompletableFuture.runAsync(() -> {
                    AgentConfig config = entry.getKey();
                    String task = entry.getValue();
                    String agentName = config.getName();

                    if (onProgress != null) {
                        onProgress.accept("🚀 Starting parallel task: @" + agentName + "\n");
                    }

                    try {
                        Agent agent = new Agent(config, provider, toolRegistry, 1);
                        String result = agent.processMessage(task, null);
                        results.put(agentName, result);

                        if (onProgress != null) {
                            onProgress.accept("✅ @" + agentName + " completed\n");
                        }
                    } catch (Exception e) {
                        results.put(agentName, "Error: " + e.getMessage());
                        if (onProgress != null) {
                            onProgress.accept("❌ @" + agentName + " failed: " + e.getMessage() + "\n");
                        }
                    }
                }, executor))
                .collect(Collectors.toList());

        // Wait for all tasks to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        return results;
    }

    /**
     * Execute tasks and format results as a combined response.
     */
    public String executeAndFormat(Map<AgentConfig, String> tasks, Consumer<String> onProgress) {
        Map<String, String> results = executeParallel(tasks, onProgress);

        StringBuilder sb = new StringBuilder();
        sb.append("═══ Parallel Execution Results ═══\n\n");
        for (Map.Entry<String, String> entry : results.entrySet()) {
            sb.append("── @").append(entry.getKey()).append(" ──\n");
            sb.append(entry.getValue()).append("\n\n");
        }
        sb.append("═══ End Results ═══");
        return sb.toString();
    }

    /**
     * Shutdown the executor service.
     */
    public void shutdown() {
        executor.shutdown();
    }
}
