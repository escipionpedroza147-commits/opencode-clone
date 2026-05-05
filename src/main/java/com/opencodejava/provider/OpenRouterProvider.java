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
        try {
            String requestBody = buildRequestBody(messages, tools, systemPrompt, false);
            Request request = new Request.Builder()
                    .url(config.getBaseUrl() + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("HTTP-Referer", "https://github.com/opencode-java")
                    .addHeader("X-Title", "OpenCode Java")
                    .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    return new LLMResponse("API error (" + response.code() + "): " + errorBody,
                            List.of(), false, 0, 0);
                }

                String responseBody = response.body().string();
                return parseResponse(responseBody);
            }
        } catch (IOException e) {
            return new LLMResponse("Connection error: " + e.getMessage(), List.of(), false, 0, 0);
        }
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

            EventSource.Factory factory = EventSources.createFactory(client);
            factory.newEventSource(request, new EventSourceListener() {
                @Override
                public void onEvent(EventSource eventSource, String id, String type, String data) {
                    if ("[DONE]".equals(data)) {
                        List<ToolCall> finalToolCalls = buildToolCalls(toolCallAccumulator);
                        onComplete.accept(new LLMResponse(
                                fullContent.toString(),
                                finalToolCalls,
                                !finalToolCalls.isEmpty(),
                                0, 0
                        ));
                        latch.countDown();
                        return;
                    }

                    try {
                        JsonNode chunk = mapper.readTree(data);
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

                            // Check for finish_reason
                            JsonNode finishReason = choices.get(0).get("finish_reason");
                            if (finishReason != null && !finishReason.isNull()) {
                                String reason = finishReason.asText();
                                if ("stop".equals(reason) || "end_turn".equals(reason)) {
                                    if (toolCallAccumulator.isEmpty()) {
                                        // Text-only response complete
                                        onComplete.accept(new LLMResponse(
                                                fullContent.toString(), List.of(), false, 0, 0));
                                        latch.countDown();
                                    }
                                } else if ("tool_calls".equals(reason) || "tool_use".equals(reason)) {
                                    List<ToolCall> finalToolCalls = buildToolCalls(toolCallAccumulator);
                                    onComplete.accept(new LLMResponse(
                                            fullContent.toString(), finalToolCalls, true, 0, 0));
                                    latch.countDown();
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Skip malformed chunks
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
                    // If latch hasn't been counted down yet, do it now
                    if (latch.getCount() > 0) {
                        List<ToolCall> finalToolCalls = buildToolCalls(toolCallAccumulator);
                        onComplete.accept(new LLMResponse(
                                fullContent.toString(), finalToolCalls, !finalToolCalls.isEmpty(), 0, 0));
                        latch.countDown();
                    }
                }
            });

            latch.await(180, TimeUnit.SECONDS);
        } catch (Exception e) {
            onComplete.accept(new LLMResponse("Error: " + e.getMessage(), List.of(), false, 0, 0));
        }
    }

    private List<ToolCall> buildToolCalls(Map<Integer, Map<String, String>> accumulator) {
        List<ToolCall> calls = new ArrayList<>();
        for (Map<String, String> acc : accumulator.values()) {
            String id = acc.getOrDefault("id", UUID.randomUUID().toString());
            String name = acc.get("name");
            if (name == null || name.isEmpty()) continue; // Skip incomplete tool calls
            String argsJson = acc.getOrDefault("arguments", "{}");
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> args = mapper.readValue(argsJson, Map.class);
                calls.add(new ToolCall(id, name, args));
            } catch (Exception e) {
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

        // Add system prompt
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            ObjectNode sysMsg = messagesArray.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
        }

        // Add conversation messages
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
                    // Include tool_calls if present (required for tool result messages to work)
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

        // Add tools with proper OpenAI-compatible format
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

                        // Mark all params as required for clarity
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

            return new LLMResponse(content, toolCalls, !toolCalls.isEmpty(), 0, 0);
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
