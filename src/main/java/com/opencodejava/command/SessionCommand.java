package com.opencodejava.command;

import com.opencodejava.core.App;
import com.opencodejava.model.Conversation;

import java.util.List;

public class SessionCommand implements Command {
    private final App app;

    public SessionCommand(App app) {
        this.app = app;
    }

    @Override
    public String getName() { return "session"; }

    @Override
    public String getDescription() { return "List sessions or start a new session"; }

    @Override
    public String execute(String[] args) {
        if (args.length == 0) {
            return listSessions();
        }

        String subCommand = args[0].toLowerCase();
        return switch (subCommand) {
            case "new" -> {
                String title = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null;
                app.getSessionManager().newConversation(title);
                yield "✨ New session started" + (title != null ? ": " + title : "");
            }
            case "list" -> listSessions();
            default -> "Usage: /session [new <title>|list]";
        };
    }

    private String listSessions() {
        Conversation current = app.getSessionManager().getCurrentConversation();
        List<Conversation> history = app.getSessionManager().getHistory();

        StringBuilder sb = new StringBuilder();
        sb.append("📋 Sessions:\n\n");

        if (current != null) {
            sb.append("  → [active] ").append(current.getTitle())
                    .append(" (").append(current.getMessageCount()).append(" messages)\n");
        }

        if (history.isEmpty()) {
            sb.append("  (no previous sessions)\n");
        } else {
            for (int i = history.size() - 1; i >= Math.max(0, history.size() - 10); i--) {
                Conversation c = history.get(i);
                sb.append("  • ").append(c.getTitle())
                        .append(" (").append(c.getMessageCount()).append(" messages)\n");
            }
        }
        return sb.toString();
    }

    @Override
    public String getUsage() { return "/session [new <title>|list]"; }
}
