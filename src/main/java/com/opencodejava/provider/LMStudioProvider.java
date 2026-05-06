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

public class LMStudioProvider implements LLMProvider {
    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final ProviderConfig config;
    private String model;
    private final String baseUrl;

    public LMStudioProvider(ProviderConfig config) {
        this.config = config;
        this.model = config.getModel() != null ? config.getModel() : "local-model";
        this.baseUrl = config.getBaseUrl() != null ? config.getBaseUrl() : "http://localhost:1234/v1";
        this.mapper = new ObjectMapper();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public LLMResponse chat(List<Message> messages, List<ToolDefinition> tools, String systemPrompt) {
        try {
            String requestBody = buildRequestBody(messages, tools, systemPrompt, false);
            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    throw new RuntimeException("LMStudio API request failed (" + response.code() + "): " + errorBody);
                }

                String responseBody = response.body().string();
                return parseResponse(responseBody);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to communicate with LMStudio: " + e.getMessage(), e);
        }
    }

    @Override
    public void chatStream(List<Message> messages, List<ToolDefinition> tools, String systemPrompt,
                           Consumer<String> onToken, Consumer<LLMResponse> onComplete) {
        try {
            String requestBody = buildRequestBody(messages, tools, systemPrompt, true);
            Request request = new Request.Builder()
                    .url(baseUrl + "/chat/completions")
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            CountDownLatch latch = new CountDownLatch(1);
            StringBuilder fullContent = new StringBuilder();

            EventSource.Factory factory = EventSources.createFactory(client);
            factory.newEventSource(request, new EventSourceListener() {
                @Override
                public void onEvent(EventSource eventSource, String id, String type, String data) {
                    if ("[DONE]".equals(data)) {
                        onComplete.accept(new LLMResponse(fullContent.toString(), List.of(), false, 0, 0));
                        latch.countDown();
                        return;
                    }

                    try {
                        JsonNode chunk = mapper.readTree(data);
                        JsonNode choices = chunk.get("choices");
                        if (choices != null && choices.size() > 0) {
                            JsonNode delta = choices.get(0).get("delta");
                            if (delta != null && delta.has("content") && !delta.get("content").isNull()) {
                                String token = delta.get("content").asText();
                                fullContent.append(token);
                                onToken.accept(token);
                            }
                        }
                    } catch (Exception e) {
                        // Skip malformed chunks
                    }
                }

                @Override
                public void onFailure(EventSource eventSource, Throwable t, Response response) {
                    String errorMsg = "Stream failed";
                    if (t != null) errorMsg += ": " + t.getMessage();
                    onComplete.accept(new LLMResponse(errorMsg, List.of(), false, 0, 0));
                    latch.countDown();
                }

                @Override
                public void onClosed(EventSource eventSource) {
                    latch.countDown();
                }
            });

            latch.await(300, TimeUnit.SECONDS);
        } catch (Exception e) {
            onComplete.accept(new LLMResponse("Error: " + e.getMessage(), List.of(), false, 0, 0));
        }
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
                case USER -> { msgNode.put("role", "user"); msgNode.put("content", msg.getContent()); }
                case ASSISTANT -> { msgNode.put("role", "assistant"); msgNode.put("content", msg.getContent()); }
                case SYSTEM -> { msgNode.put("role", "system"); msgNode.put("content", msg.getContent()); }
                case TOOL -> {
                    msgNode.put("role", "tool");
                    msgNode.put("content", msg.getContent());
                    if (msg.getToolCallId() != null) msgNode.put("tool_call_id", msg.getToolCallId());
                }
            }
        }

        // LMStudio supports OpenAI-compatible tool format
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
                if (tool.parameters() != null) {
                    for (Map.Entry<String, Object> entry : tool.parameters().entrySet()) {
                        ObjectNode prop = properties.putObject(entry.getKey());
                        if (entry.getValue() instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
                            propDef.forEach((k, v) -> prop.put(k, v.toString()));
                        } else {
                            prop.put("type", "string");
                            prop.put("description", entry.getValue().toString());
                        }
                    }
                }
            }
        }

        return root.toString();
    }

    private LLMResponse parseResponse(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
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
                    @SuppressWarnings("unchecked")
                    Map<String, Object> args = mapper.readValue(argsJson, Map.class);
                    toolCalls.add(new ToolCall(id, name, args));
                }
            }

            // Parse token usage
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
    public String getProviderName() { return "LMStudio"; }

    @Override
    public String getModelName() { return model; }

    @Override
    public void setModel(String model) { this.model = model; }
}
