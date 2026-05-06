package com.opencodejava.command;

import com.opencodejava.core.App;
import com.opencodejava.model.Conversation;
import com.opencodejava.model.Message;

import java.util.ArrayList;
import java.util.List;

public class SearchCommand implements Command {
    private final App app;

    public SearchCommand(App app) {
        this.app = app;
    }

    @Override
    public String getName() { return "search"; }

    @Override
    public String getDescription() { return "Search conversation messages for a query"; }

    @Override
    public String execute(String[] args) {
        if (args.length == 0) {
            return "Usage: /search <query>";
        }

        String query = String.join(" ", args).toLowerCase();
        Conversation conversation = app.getSessionManager().getCurrentConversation();
        if (conversation == null || conversation.getMessageCount() == 0) {
            return "No messages to search.";
        }

        List<String> results = new ArrayList<>();
        int index = 0;
        for (Message msg : conversation.getMessages()) {
            index++;
            if (msg.getContent() != null && msg.getContent().toLowerCase().contains(query)) {
                String preview = msg.getContent();
                if (preview.length() > 120) {
                    int pos = preview.toLowerCase().indexOf(query);
                    int start = Math.max(0, pos - 40);
                    int end = Math.min(preview.length(), pos + query.length() + 40);
                    preview = (start > 0 ? "..." : "") + preview.substring(start, end) + (end < preview.length() ? "..." : "");
                }
                results.add(String.format("  #%d [%s]: %s", index, msg.getRole().name().toLowerCase(), preview));
            }
        }

        if (results.isEmpty()) {
            return "🔍 No results found for: " + query;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🔍 Found ").append(results.size()).append(" result(s) for \"").append(query).append("\":\n\n");
        for (String r : results) {
            sb.append(r).append("\n");
        }
        return sb.toString();
    }

    @Override
    public String getUsage() { return "/search <query>"; }
}
