package com.opencodejava.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void testUserMessage() {
        Message msg = new Message(Message.Role.USER, "Hello");
        assertEquals(Message.Role.USER, msg.getRole());
        assertEquals("Hello", msg.getContent());
        assertNotNull(msg.getId());
        assertNotNull(msg.getTimestamp());
        assertFalse(msg.hasToolCalls());
    }

    @Test
    void testAssistantMessage() {
        Message msg = new Message(Message.Role.ASSISTANT, "Hi there!");
        assertEquals(Message.Role.ASSISTANT, msg.getRole());
        assertEquals("Hi there!", msg.getContent());
    }

    @Test
    void testToolMessage() {
        Message msg = new Message(Message.Role.TOOL, "result output", "call_123", "bash");
        assertEquals(Message.Role.TOOL, msg.getRole());
        assertEquals("call_123", msg.getToolCallId());
        assertEquals("bash", msg.getToolName());
    }

    @Test
    void testAssistantMessageWithToolCalls() {
        ToolCall tc = new ToolCall("id_1", "bash", Map.of("command", "ls"));
        Message msg = new Message(Message.Role.ASSISTANT, "", List.of(tc));
        assertTrue(msg.hasToolCalls());
        assertEquals(1, msg.getToolCalls().size());
        assertEquals("bash", msg.getToolCalls().get(0).getName());
    }

    @Test
    void testMessageIds_areUnique() {
        Message m1 = new Message(Message.Role.USER, "a");
        Message m2 = new Message(Message.Role.USER, "b");
        assertNotEquals(m1.getId(), m2.getId());
    }

    @Test
    void testToString() {
        Message msg = new Message(Message.Role.USER, "test content");
        String str = msg.toString();
        assertTrue(str.contains("USER"));
        assertTrue(str.contains("test content"));
    }
}
