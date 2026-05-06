package com.opencodejava.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencodejava.model.Message;
import com.opencodejava.model.ProviderConfig;
import com.opencodejava.model.ToolCall;
import okhttp3.*;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class OpenRouterProvider implements LLMProvider {
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final ProviderConfig config;
    private String model;

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    public OpenRouterProvider(ProviderConfig config) {
        this.config = config;
        this.model = config.getModel();
        this.mapper = new ObjectMapper();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public LLMResponse chat(List<Message> messages, List<ToolDefinition> tools, String systemPrompt) {
        String requestBody = buildRequestBody(messages, tools, systemPrompt, false);

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                Request request = new Request.Builder()
                        .url(config.getBaseUrl() + "/chat/completions")
                        .addHeader("Authorization", "Bearer " + config.getApiKey())
                        .addHeader("Content-Type", "application/json")
                        .addHeader("HTTP-Referer", "https://github.com/opencode-java")
                        .addHeader("X-Title", "OpenCode Java")
                        .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    int code = response.code();

                    if (code == 429) {
                        // Rate limited - exponential backoff
                        long backoff = calculateBackoff(attempt, response);
                        if (attempt < MAX_RETRIES) {
                            Thread.sleep(backoff);
                            continue;
                        }
                        return new LLMResponse("Rate limited (429). Retry after backoff failed.", List.of(), false, 0, 0);
                    }

                    if (code >= 500 && attempt < MAX_RETRIES) {
                        // Server error - retry with backoff
                        Thread.sleep(calculateBackoff(attempt, null));
                        continue;
                    }

                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                        return new LLMResponse("API error (" + code + "): " + errorBody, List.of(), false, 0, 0);
                    }

                    String responseBody = response.body().string();
                    return parseResponse(responseBody);
                }
            } catch (IOException e) {
                if (attempt < MAX_RETRIES) {
                    try {
                        Thread.sleep(calculateBackoff(attempt, null));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return new LLMResponse("Interrupted during retry", List.of(), false, 0, 0);
                    }
                    continue;
                }
                return new LLMResponse("Connection error after " + (MAX_RETRIES + 1) + " attempts: " + e.getMessage(),
                        List.of(), false, 0, 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new LLMResponse("Interrupted during retry", List.of(), false, 0, 0);
            }
        }
        return new LLMResponse("Max retries exceeded", List.of(), false, 0, 0);
    }

    @Override
    public void chatStream(List<Message> messages, List<ToolDefinition> tools, String systemPrompt,
                           Consumer<String> onToken, Consumer<LLMResponse> onComplete) {
        try {
            String requestBody = buildRequestBody(messages, tools, systemPrompt, true);
            Request request = new Request.Builder()
                    .url(config.getBaseUrl() + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://github.com/opencode-java")
                    .addHeader("X-Title", "OpenCode Java")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            CountDownLatch latch = new CountDownLatch(1);
            StringBuilder fullContent = new StringBuilder();
            Map<Integer, Map<String, String>> toolCallAccumulator = new TreeMap<>();
            int[] usageTokens = {0, 0};

            EventSource.Factory factory = EventSources.createFactory(client);
            factory.newEventSource(request, new EventSourceListener() {
                @Override
                public void onEvent(EventSource eventSource, String id, String type, String data) {
                    if ("[DONE]".equals(data)) {
                        List<ToolCall> finalToolCalls = buildToolCalls(toolCallAccumulator);
                        onComplete.accept(new LLMResponse(
                                fullContent.toString(), finalToolCalls, !finalToolCalls.isEmpty(),
                                usageTokens[0], usageTokens[1]));
                        latch.countDown();
                        return;
                    }

                    try {
                        JsonNode chunk = mapper.readTree(data);

                        if (chunk.has("usage")) {
                            JsonNode usage = chunk.get("usage");
                            if (usage.has("prompt_tokens")) usageTokens[0] = usage.get("prompt_tokens").asInt();
                            if (usage.has("completion_tokens")) usageTokens[1] = usage.get("completion_tokens").asInt();
                        }

                        JsonNode choices = chunk.get("choices");
                        if (choices != null && choices.size() > 0) {
                            JsonNode delta = choices.get(0).get("delta");
                            if (delta != null) {
                                if (delta.has("content") && !delta.get("content").isNull()) {
                                    String token = delta.get("content").asText();
                                    fullContent.append(token);
                                    onToken.accept(token);
                                }
                                if (delta.has("tool_calls")) {
                                    JsonNode tcArray = delta.get("tool_calls");
                                    for (JsonNode tc : tcArray) {
                                        int index = tc.get("index").asInt();
                                        toolCallAccumulator.putIfAbsent(index, new HashMap<>());
                                        Map<String, String> acc = toolCallAccumulator.get(index);

                                        if (tc.has("id") && !tc.get("id").isNull()) {
                                            acc.put("id", tc.get("id").asText());
                                        }
                                        if (tc.has("function")) {
                                            JsonNode fn = tc.get("function");
                                            if (fn.has("name") && !fn.get("name").isNull()) {
                                                acc.put("name", fn.get("name").asText());
                                            }
                                            if (fn.has("arguments") && !fn.get("arguments").isNull()) {
                                                acc.merge("arguments", fn.get("arguments").asText(), String::concat);
                                            }
                                        }
                                    }
                                }
                            }

                            JsonNode finishReason = choices.get(0).get("finish_reason");
                            if (finishReason != null && !finishReason.isNull()) {
                                String reason = finishReason.asText();
                                if ("stop".equals(reason) || "end_turn".equals(reason)) {
                                    if (toolCallAccumulator.isEmpty()) {
                                        onComplete.accept(new LLMResponse(
                                                fullContent.toString(), List.of(), false,
                                                usageTokens[0], usageTokens[1]));
                                        latch.countDown();
                                    }
                                } else if ("tool_calls".equals(reason) || "tool_use".equals(reason)) {
                                    List<ToolCall> finalToolCalls = buildToolCalls(toolCallAccumulator);
                                    onComplete.accept(new LLMResponse(
                                            fullContent.toString(), finalToolCalls, true,
                                            usageTokens[0], usageTokens[1]));
                                    latch.countDown();
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Skip malformed chunks gracefully
                    }
                }

                @Override
                public void onFailure(EventSource eventSource, Throwable t, Response response) {
                    String errorMsg = "Stream failed: ";
                    if (response != null) {
                        try {
                            errorMsg += response.body().string();
                        } catch (Exception ignored) {
                            errorMsg += "status " + response.code();
                        }
                    } else if (t != null) {
                        errorMsg += t.getMessage();
                    }
                    onComplete.accept(new LLMResponse(errorMsg, List.of(), false, 0, 0));
                    latch.countDown();
                }

                @Override
                public void onClosed(EventSource eventSource) {
                    if (latch.getCount() > 0) {
                        List<ToolCall> finalToolCalls = buildToolCalls(toolCallAccumulator);
                        onComplete.accept(new LLMResponse(
                                fullContent.toString(), finalToolCalls, !finalToolCalls.isEmpty(),
                                usageTokens[0], usageTokens[1]));
                        latch.countDown();
                    }
                }
            });

            latch.await(180, TimeUnit.SECONDS);
        } catch (Exception e) {
            onComplete.accept(new LLMResponse("Error: " + e.getMessage(), List.of(), false, 0, 0));
        }
    }

    /**
     * Calculate exponential backoff with jitter.
     */
    private long calculateBackoff(int attempt, Response response) {
        // Check for Retry-After header
        if (response != null) {
            String retryAfter = response.header("Retry-After");
            if (retryAfter != null) {
                try {
                    return Long.parseLong(retryAfter) * 1000;
                } catch (NumberFormatException ignored) {}
            }
        }
        // Exponential backoff: 1s, 2s, 4s + jitter
        long backoff = INITIAL_BACKOFF_MS * (1L << attempt);
        long jitter = (long) (Math.random() * backoff * 0.1);
        return backoff + jitter;
    }

    private List<ToolCall> buildToolCalls(Map<Integer, Map<String, String>> accumulator) {
        List<ToolCall> calls = new ArrayList<>();
        for (Map<String, String> acc : accumulator.values()) {
            String id = acc.getOrDefault("id", UUID.randomUUID().toString());
            String name = acc.get("name");
            if (name == null || name.isEmpty()) continue;
            String argsJson = acc.getOrDefault("arguments", "{}");
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> args = mapper.readValue(argsJson, Map.class);
                calls.add(new ToolCall(id, name, args));
            } catch (Exception e) {
                // Graceful handling of malformed JSON - wrap raw as argument
                calls.add(new ToolCall(id, name, Map.of("raw", argsJson)));
            }
        }
        return calls;
    }

    private String buildRequestBody(List<Message> messages, List<ToolDefinition> tools,
                                    String systemPrompt, boolean stream) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("stream", stream);
        root.put("temperature", config.getTemperature());
        root.put("max_tokens", config.getMaxTokens());

        ArrayNode messagesArray = root.putArray("messages");

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            ObjectNode sysMsg = messagesArray.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
        }

        for (Message msg : messages) {
            ObjectNode msgNode = messagesArray.addObject();
            switch (msg.getRole()) {
                case USER -> {
                    msgNode.put("role", "user");
                    msgNode.put("content", msg.getContent());
                }
                case ASSISTANT -> {
                    msgNode.put("role", "assistant");
                    if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                        msgNode.put("content", msg.getContent());
                    } else {
                        msgNode.putNull("content");
                    }
                    if (msg.hasToolCalls()) {
                        ArrayNode tcArray = msgNode.putArray("tool_calls");
                        for (var tc : msg.getToolCalls()) {
                            ObjectNode tcNode = tcArray.addObject();
                            tcNode.put("id", tc.getId());
                            tcNode.put("type", "function");
                            ObjectNode fnNode = tcNode.putObject("function");
                            fnNode.put("name", tc.getName());
                            try {
                                fnNode.put("arguments", mapper.writeValueAsString(tc.getArguments()));
                            } catch (Exception e) {
                                fnNode.put("arguments", "{}");
                            }
                        }
                    }
                }
                case SYSTEM -> {
                    msgNode.put("role", "system");
                    msgNode.put("content", msg.getContent());
                }
                case TOOL -> {
                    msgNode.put("role", "tool");
                    msgNode.put("content", msg.getContent());
                    if (msg.getToolCallId() != null) {
                        msgNode.put("tool_call_id", msg.getToolCallId());
                    }
                }
            }
        }

        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = root.putArray("tools");
            for (ToolDefinition tool : tools) {
                ObjectNode toolNode = toolsArray.addObject();
                toolNode.put("type", "function");
                ObjectNode fn = toolNode.putObject("function");
                fn.put("name", tool.name());
                fn.put("description", tool.description());

                ObjectNode params = fn.putObject("parameters");
                params.put("type", "object");
                ObjectNode properties = params.putObject("properties");
                ArrayNode required = params.putArray("required");

                if (tool.parameters() != null) {
                    for (Map.Entry<String, Object> entry : tool.parameters().entrySet()) {
                        String paramName = entry.getKey();
                        ObjectNode prop = properties.putObject(paramName);

                        if (entry.getValue() instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
                            prop.put("type", propDef.getOrDefault("type", "string").toString());
                            if (propDef.containsKey("description")) {
                                prop.put("description", propDef.get("description").toString());
                            }
                        } else {
                            prop.put("type", "string");
                            prop.put("description", entry.getValue().toString());
                        }
                        required.add(paramName);
                    }
                }
            }
        }

        return root.toString();
    }

    private LLMResponse parseResponse(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);

            // Check for error response format
            if (root.has("error")) {
                JsonNode error = root.get("error");
                String msg = error.has("message") ? error.get("message").asText() : "Unknown API error";
                return new LLMResponse("API Error: " + msg, List.of(), false, 0, 0);
            }

            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                return new LLMResponse("No response from model", List.of(), false, 0, 0);
            }

            JsonNode message = choices.get(0).get("message");
            String content = message.has("content") && !message.get("content").isNull()
                    ? message.get("content").asText() : "";

            List<ToolCall> toolCalls = new ArrayList<>();
            if (message.has("tool_calls") && !message.get("tool_calls").isNull()) {
                for (JsonNode tc : message.get("tool_calls")) {
                    String id = tc.has("id") ? tc.get("id").asText() : UUID.randomUUID().toString();
                    String name = tc.get("function").get("name").asText();
                    String argsJson = tc.get("function").get("arguments").asText();
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> args = mapper.readValue(argsJson, Map.class);
                        toolCalls.add(new ToolCall(id, name, args));
                    } catch (Exception e) {
                        toolCalls.add(new ToolCall(id, name, Map.of("raw", argsJson)));
                    }
                }
            }

            int promptTokens = 0;
            int completionTokens = 0;
            if (root.has("usage")) {
                JsonNode usage = root.get("usage");
                if (usage.has("prompt_tokens")) promptTokens = usage.get("prompt_tokens").asInt();
                if (usage.has("completion_tokens")) completionTokens = usage.get("completion_tokens").asInt();
            }

            return new LLMResponse(content, toolCalls, !toolCalls.isEmpty(), promptTokens, completionTokens);
        } catch (Exception e) {
            return new LLMResponse("Error parsing response: " + e.getMessage(), List.of(), false, 0, 0);
        }
    }

    @Override
    public String getProviderName() { return "OpenRouter"; }

    @Override
    public String getModelName() { return model; }

    @Override
    public void setModel(String model) { this.model = model; }
}
