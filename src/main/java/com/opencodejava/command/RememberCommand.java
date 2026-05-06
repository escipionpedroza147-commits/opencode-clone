package com.opencodejava.command;

import com.opencodejava.core.Memory;

/**
 * Command to save a note to memory.
 * Usage: /remember <note> or /remember <key> = <value>
 */
public class RememberCommand implements Command {

    @Override
    public String getName() { return "remember"; }

    @Override
    public String getDescription() {
        return "Save a note to memory. Usage: /remember <note> or /remember <key> = <value>";
    }

    @Override
    public String execute(String[] args) {
        if (args.length == 0) {
            return "Usage: /remember <note> or /remember <key> = <value>";
        }

        String input = String.join(" ", args);
        Memory memory = Memory.getInstance();

        // Check for key=value syntax
        int eqIndex = input.indexOf('=');
        if (eqIndex > 0 && eqIndex < input.length() - 1) {
            String key = input.substring(0, eqIndex).trim();
            String value = input.substring(eqIndex + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                memory.remember(key, value);
                return "📝 Remembered: [" + key + "] = " + value;
            }
        }

        // No key — just store the note
        memory.remember(input);
        return "📝 Remembered: " + input;
    }

    @Override
    public String getUsage() {
        return "/remember <note> or /remember <key> = <value>";
    }
}
