package com.opencodejava.command;

import com.opencodejava.core.App;
import com.opencodejava.model.Conversation;
import com.opencodejava.model.Message;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ExportCommand implements Command {
    private final App app;

    public ExportCommand(App app) {
        this.app = app;
    }

    @Override
    public String getName() { return "export"; }

    @Override
    public String getDescription() { return "Export current conversation to markdown file"; }

    @Override
    public String execute(String[] args) {
        Conversation conversation = app.getSessionManager().getCurrentConversation();
        if (conversation == null || conversation.getMessageCount() == 0) {
            return "No messages to export.";
        }

        String filename = args.length > 0 ? args[0] : "conversation-" + System.currentTimeMillis() + ".md";
        if (!filename.endsWith(".md")) {
            filename += ".md";
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());

        StringBuilder md = new StringBuilder();
        md.append("# Conversation Export\n\n");
        md.append("**Title:** ").append(conversation.getTitle()).append("\n");
        md.append("**Date:** ").append(formatter.format(conversation.getCreatedAt())).append("\n");
        md.append("**Messages:** ").append(conversation.getMessageCount()).append("\n\n");
        md.append("---\n\n");

        for (Message msg : conversation.getMessages()) {
            String role = switch (msg.getRole()) {
                case USER -> "👤 **User**";
                case ASSISTANT -> "🤖 **Assistant**";
                case SYSTEM -> "⚙️ **System**";
                case TOOL -> "🔧 **Tool** (`" + (msg.getToolName() != null ? msg.getToolName() : "unknown") + "`)";
            };

            md.append("### ").append(role).append("\n");
            md.append("*").append(formatter.format(msg.getTimestamp())).append("*\n\n");
            if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                md.append(msg.getContent()).append("\n\n");
            }
            md.append("---\n\n");
        }

        try {
            Path outputPath = Path.of(app.getConfig().getWorkingDirectory(), filename);
            Files.writeString(outputPath, md.toString());
            return "✅ Exported " + conversation.getMessageCount() + " messages to " + outputPath;
        } catch (IOException e) {
            return "❌ Failed to export: " + e.getMessage();
        }
    }

    @Override
    public String getUsage() { return "/export [filename]"; }
}
