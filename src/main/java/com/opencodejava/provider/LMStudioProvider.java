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
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LMStudioProvider implements LLMProvider {
    private static final Logger logger = Logger.getLogger(LMStudioProvider.class.getName());
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
            "<tool_call>\\s*(\\{.*?})\\s*</tool_call>", Pattern.DOTALL);
    private static final Pattern BARE_TOOL_CALL_PATTERN = Pattern.compile(
            "\\{\\s*\"name\"\\s*:\\s*\"(\\w+)\"\\s*,\\s*\"arguments\"\\s*:\\s*(\\{[^}]*\\})\\s*\\}");

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
            String enhancedSystemPrompt = buildToolAwareSystemPrompt(systemPrompt, tools);
            String requestBody = buildRequestBody(messages, enhancedSystemPrompt, false);
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
            String enhancedSystemPrompt = buildToolAwareSystemPrompt(systemPrompt, tools);
            String requestBody = buildRequestBody(messages, enhancedSystemPrompt, true);
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
                        // Stream complete — parse accumulated content for tool calls
                        String text = fullContent.toString();
                        List<ToolCall> toolCalls = extractToolCalls(text);
                        if (!toolCalls.isEmpty()) {
                            // Strip tool_call blocks and bare JSON tool calls from displayed content
                            String cleanContent = TOOL_CALL_PATTERN.matcher(text).replaceAll("");
                            cleanContent = BARE_TOOL_CALL_PATTERN.matcher(cleanContent).replaceAll("").trim();
                            onComplete.accept(new LLMResponse(cleanContent, toolCalls, true, 0, 0));
                        } else {
                            onComplete.accept(new LLMResponse(text, List.of(), false, 0, 0));
                        }
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

    /**
     * Builds an enhanced system prompt that includes tool descriptions and usage instructions.
     * Instead of passing tools via the API (which small models ignore), we inject them into the prompt.
     */
    private String buildToolAwareSystemPrompt(String systemPrompt, List<ToolDefinition> tools) {
        if (tools == null || tools.isEmpty()) {
            return systemPrompt != null ? systemPrompt : "";
        }

        StringBuilder sb = new StringBuilder();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            sb.append(systemPrompt);
            sb.append("\n\n");
        }

        sb.append("# Available Tools\n\n");
        sb.append("You have access to the following tools. To use a tool, output the following exact format:\n\n");
        sb.append("<tool_call>\n");
        sb.append("{\"name\": \"tool_name\", \"arguments\": {\"param1\": \"value1\"}}\n");
        sb.append("</tool_call>\n\n");
        sb.append("You may call multiple tools in a single response by using multiple <tool_call> blocks.\n");
        sb.append("IMPORTANT: Always use the exact format above. Do NOT add extra fields or formatting.\n\n");
        sb.append("## Tools:\n\n");

        for (ToolDefinition tool : tools) {
            sb.append("### ").append(tool.name()).append("\n");
            sb.append("Description: ").append(tool.description()).append("\n");
            sb.append("Parameters:\n");
            if (tool.parameters() != null && !tool.parameters().isEmpty()) {
                for (Map.Entry<String, Object> entry : tool.parameters().entrySet()) {
                    sb.append("  - ").append(entry.getKey());
                    if (entry.getValue() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> propDef = (Map<String, Object>) entry.getValue();
                        String type = propDef.getOrDefault("type", "string").toString();
                        String desc = propDef.getOrDefault("description", "").toString();
                        sb.append(" (").append(type).append("): ").append(desc);
                    } else {
                        sb.append(": ").append(entry.getValue());
                    }
                    sb.append("\n");
                }
            } else {
                sb.append("  (none)\n");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Builds the request body WITHOUT tools — tools are injected into the system prompt instead.
     */
    private String buildRequestBody(List<Message> messages, String systemPrompt, boolean stream) {
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
                    // For prompt-based tool calling, tool results come back as user messages
                    // since the model doesn't understand the "tool" role natively
                    msgNode.put("role", "user");
                    String toolContent = msg.getContent();
                    if (msg.getToolCallId() != null) {
                        toolContent = "[Tool Result for " + msg.getToolCallId() + "]: " + toolContent;
                    } else {
                        toolContent = "[Tool Result]: " + toolContent;
                    }
                    msgNode.put("content", toolContent);
                }
            }
        }

        // NOTE: No "tools" array is added to the request body.
        // Small models don't support the OpenAI tools API parameter.
        // Tool definitions are injected into the system prompt instead.

        return root.toString();
    }

    /**
     * Parses the API response and checks for prompt-based tool calls in the text content.
     */
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

            // Parse token usage
            int promptTokens = 0;
            int completionTokens = 0;
            if (root.has("usage")) {
                JsonNode usage = root.get("usage");
                if (usage.has("prompt_tokens")) promptTokens = usage.get("prompt_tokens").asInt();
                if (usage.has("completion_tokens")) completionTokens = usage.get("completion_tokens").asInt();
            }

            // Check for prompt-based tool calls in the text
            List<ToolCall> toolCalls = extractToolCalls(content);
            if (!toolCalls.isEmpty()) {
                // Strip tool_call blocks and bare JSON tool calls from the content
                String cleanContent = TOOL_CALL_PATTERN.matcher(content).replaceAll("");
                cleanContent = BARE_TOOL_CALL_PATTERN.matcher(cleanContent).replaceAll("").trim();
                return new LLMResponse(cleanContent, toolCalls, true, promptTokens, completionTokens);
            }

            return new LLMResponse(content, List.of(), false, promptTokens, completionTokens);
        } catch (Exception e) {
            return new LLMResponse("Error parsing response: " + e.getMessage(), List.of(), false, 0, 0);
        }
    }

    /**
     * Extracts tool calls from the model's text output by finding <tool_call>...</tool_call> blocks
     * or bare JSON objects with {"name": "...", "arguments": {...}} format.
     * Handles multiple tool calls and gracefully skips malformed JSON.
     */
    private List<ToolCall> extractToolCalls(String text) {
        List<ToolCall> toolCalls = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return toolCalls;
        }

        // First try: look for <tool_call> tags
        Matcher matcher = TOOL_CALL_PATTERN.matcher(text);
        while (matcher.find()) {
            String jsonStr = matcher.group(1).trim();
            ToolCall tc = parseToolCallJson(jsonStr);
            if (tc != null) toolCalls.add(tc);
        }

        // Second try: if no tagged tool calls found, look for bare JSON tool calls
        if (toolCalls.isEmpty()) {
            Matcher bareMatcher = BARE_TOOL_CALL_PATTERN.matcher(text);
            while (bareMatcher.find()) {
                String fullMatch = bareMatcher.group(0);
                ToolCall tc = parseToolCallJson(fullMatch);
                if (tc != null && !"none".equalsIgnoreCase(tc.getName())) {
                    toolCalls.add(tc);
                }
            }
        }

        // Third try: if still nothing, try parsing the entire trimmed text as a JSON tool call
        if (toolCalls.isEmpty()) {
            String trimmed = text.trim();
            // Check if the whole response looks like a JSON tool call
            if (trimmed.startsWith("{") && trimmed.contains("\"name\"") && trimmed.contains("\"arguments\"")) {
                ToolCall tc = parseToolCallJson(trimmed);
                if (tc != null && !"none".equalsIgnoreCase(tc.getName())) {
                    toolCalls.add(tc);
                }
            }
        }

        return toolCalls;
    }

    /**
     * Parse a single JSON string into a ToolCall object.
     * Returns null if parsing fails or name is missing.
     */
    private ToolCall parseToolCallJson(String jsonStr) {
        try {
            JsonNode node = mapper.readTree(jsonStr);
            String name = node.has("name") ? node.get("name").asText() : null;
            if (name == null || name.isEmpty()) {
                logger.warning("Skipping tool_call block with missing 'name': " + jsonStr);
                return null;
            }

            Map<String, Object> arguments = new HashMap<>();
            if (node.has("arguments") && !node.get("arguments").isNull()) {
                JsonNode argsNode = node.get("arguments");
                if (argsNode.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> fields = argsNode.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        JsonNode val = field.getValue();
                        if (val.isTextual()) {
                            arguments.put(field.getKey(), val.asText());
                        } else if (val.isNumber()) {
                            arguments.put(field.getKey(), val.numberValue());
                        } else if (val.isBoolean()) {
                            arguments.put(field.getKey(), val.asBoolean());
                        } else {
                            arguments.put(field.getKey(), val.toString());
                        }
                    }
                }
            }

            String id = "tc_" + UUID.randomUUID().toString().substring(0, 8);
            return new ToolCall(id, name, arguments);
        } catch (Exception e) {
            logger.warning("Skipping malformed tool_call JSON: " + jsonStr + " — " + e.getMessage());
            return null;
        }
    }

    @Override
    public String getProviderName() { return "LMStudio"; }

    @Override
    public String getModelName() { return model; }

    @Override
    public void setModel(String model) { this.model = model; }
}
