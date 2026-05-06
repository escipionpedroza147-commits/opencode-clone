package com.opencodejava.command;

import com.opencodejava.core.Memory;

/**
 * Command to show stored memories.
 * Usage: /memory [clear|delete <key>]
 */
public class MemoryCommand implements Command {

    @Override
    public String getName() { return "memory"; }

    @Override
    public String getDescription() {
        return "Show stored memories. Usage: /memory [clear|delete <key>]";
    }

    @Override
    public String execute(String[] args) {
        Memory memory = Memory.getInstance();

        if (args.length > 0) {
            String sub = args[0].toLowerCase();
            if ("clear".equals(sub)) {
                int count = memory.size();
                // Reload empty
                memory.getAll().keySet().stream().toList().forEach(memory::remove);
                return "🗑️ Cleared " + count + " memories.";
            } else if ("delete".equals(sub) && args.length > 1) {
                String key = args[1];
                if (memory.remove(key)) {
                    return "🗑️ Deleted memory: " + key;
                } else {
                    return "Memory not found: " + key;
                }
            }
        }

        return memory.formatAll();
    }

    @Override
    public String getUsage() {
        return "/memory [clear|delete <key>]";
    }
}
