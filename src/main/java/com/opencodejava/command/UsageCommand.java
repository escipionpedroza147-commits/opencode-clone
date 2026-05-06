package com.opencodejava.command;

import com.opencodejava.core.UsageTracker;

/**
 * Command to show session token usage and cost stats.
 * Usage: /usage [reset]
 */
public class UsageCommand implements Command {

    @Override
    public String getName() { return "usage"; }

    @Override
    public String getDescription() {
        return "Show session token usage and estimated cost. Usage: /usage [reset]";
    }

    @Override
    public String execute(String[] args) {
        UsageTracker tracker = UsageTracker.getInstance();

        if (args.length > 0 && "reset".equalsIgnoreCase(args[0])) {
            tracker.reset();
            return "📊 Usage stats reset.";
        }

        return tracker.getSessionSummary();
    }

    @Override
    public String getUsage() {
        return "/usage [reset]";
    }
}
