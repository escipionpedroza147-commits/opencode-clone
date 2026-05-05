package com.opencodejava.provider;

import com.opencodejava.model.Message;
import com.opencodejava.model.ToolCall;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface LLMProvider {

    /**
     * Send messages and get a complete response.
     */
    LLMResponse chat(List<Message> messages, List<ToolDefinition> tools, String systemPrompt);

    /**
     * Send messages and stream the response token by token.
     */
    void chatStream(List<Message> messages, List<ToolDefinition> tools, String systemPrompt,
                    Consumer<String> onToken, Consumer<LLMResponse> onComplete);

    /**
     * Get the provider name.
     */
    String getProviderName();

    /**
     * Get the current model name.
     */
    String getModelName();

    /**
     * Set the model to use.
     */
    void setModel(String model);

    /**
     * Represents a complete LLM response.
     */
    record LLMResponse(String content, List<ToolCall> toolCalls, boolean hasToolCalls,
                       int promptTokens, int completionTokens) {
    }

    /**
     * Represents a tool definition to send to the LLM.
     */
    record ToolDefinition(String name, String description, Map<String, Object> parameters) {
    }
}
