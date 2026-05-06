package com.opencodejava.command;

import com.opencodejava.core.App;
import com.opencodejava.model.Conversation;
import com.opencodejava.model.Message;

import java.util.List;

public class UndoCommand implements Command {
    private final App app;

    public UndoCommand(App app) {
        this.app = app;
    }

    @Override
    public String getName() { return "undo"; }

    @Override
    public String getDescription() { return "Remove the last user/assistant message pair"; }

    @Override
    public String execute(String[] args) {
        Conversation conversation = app.getSessionManager().getCurrentConversation();
        if (conversation == null || conversation.getMessageCount() < 2) {
            return "Nothing to undo.";
        }

        List<Message> messages = conversation.getMessages();
        int removed = 0;

        // Remove messages from the end until we've removed one user + one assistant message
        // We need to work with the mutable internal list through the conversation
        while (conversation.getMessageCount() > 0 && removed < 2) {
            Message last = conversation.getMessages().get(conversation.getMessageCount() - 1);
            if (last.getRole() == Message.Role.USER || last.getRole() == Message.Role.ASSISTANT) {
                removed++;
            }
            conversation.removeLastMessage();
        }

        return "↩️  Removed last " + removed + " message(s). Conversation now has " +
                conversation.getMessageCount() + " messages.";
    }

    @Override
    public String getUsage() { return "/undo"; }
}
