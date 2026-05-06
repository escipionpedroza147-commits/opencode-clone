package com.opencodejava.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConversationTest {

    @Test
    void testNewConversation() {
        Conversation conv = new Conversation("Test Session");
        assertEquals("Test Session", conv.getTitle());
        assertNotNull(conv.getId());
        assertNotNull(conv.getCreatedAt());
        assertEquals(0, conv.getMessageCount());
    }

    @Test
    void testAddMessage() {
        Conversation conv = new Conversation("Test");
        conv.addMessage(new Message(Message.Role.USER, "Hello"));
        assertEquals(1, conv.getMessageCount());
    }

    @Test
    void testGetMessages_isUnmodifiable() {
        Conversation conv = new Conversation("Test");
        conv.addMessage(new Message(Message.Role.USER, "Hello"));
        assertThrows(UnsupportedOperationException.class, () -> {
            conv.getMessages().add(new Message(Message.Role.USER, "hack"));
        });
    }

    @Test
    void testGetLastMessages() {
        Conversation conv = new Conversation("Test");
        conv.addMessage(new Message(Message.Role.USER, "msg1"));
        conv.addMessage(new Message(Message.Role.ASSISTANT, "msg2"));
        conv.addMessage(new Message(Message.Role.USER, "msg3"));

        var last2 = conv.getLastMessages(2);
        assertEquals(2, last2.size());
        assertEquals("msg2", last2.get(0).getContent());
        assertEquals("msg3", last2.get(1).getContent());
    }

    @Test
    void testRemoveLastMessage() {
        Conversation conv = new Conversation("Test");
        conv.addMessage(new Message(Message.Role.USER, "msg1"));
        conv.addMessage(new Message(Message.Role.ASSISTANT, "msg2"));
        assertEquals(2, conv.getMessageCount());

        conv.removeLastMessage();
        assertEquals(1, conv.getMessageCount());
        assertEquals("msg1", conv.getMessages().get(0).getContent());
    }

    @Test
    void testRemoveLastMessage_empty() {
        Conversation conv = new Conversation("Test");
        conv.removeLastMessage(); // should not throw
        assertEquals(0, conv.getMessageCount());
    }

    @Test
    void testCompact_fewMessages() {
        Conversation conv = new Conversation("Test");
        conv.addMessage(new Message(Message.Role.USER, "a"));
        conv.addMessage(new Message(Message.Role.ASSISTANT, "b"));
        conv.compact(); // Should do nothing for <= 4 messages
        assertEquals(2, conv.getMessageCount());
    }

    @Test
    void testCompact_manyMessages() {
        Conversation conv = new Conversation("Test");
        for (int i = 0; i < 10; i++) {
            conv.addMessage(new Message(Message.Role.USER, "user msg " + i));
            conv.addMessage(new Message(Message.Role.ASSISTANT, "assistant msg " + i));
        }
        assertEquals(20, conv.getMessageCount());
        conv.compact();
        assertTrue(conv.getMessageCount() < 20);
        // Should have summary + last 2 messages
        assertEquals(3, conv.getMessageCount());
    }

    @Test
    void testActiveAgent() {
        Conversation conv = new Conversation("Test");
        assertEquals("build", conv.getActiveAgentName());
        conv.setActiveAgentName("plan");
        assertEquals("plan", conv.getActiveAgentName());
    }
}
