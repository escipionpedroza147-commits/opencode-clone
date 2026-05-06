package com.opencodejava.ui;

import com.opencodejava.core.App;
import com.opencodejava.core.UsageTracker;
import com.opencodejava.command.CommandRegistry;
import com.opencodejava.agent.AgentManager;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;

import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Terminal-based UI using JLine for input and ANSI escape codes for styling.
 * Provides a chat interface similar to OpenCode's TUI.
 */
public class TerminalUI {
    private final App app;
    private final CommandRegistry commandRegistry;
    private final AgentManager agentManager;
    private final MarkdownRenderer mdRenderer;
    private Terminal terminal;
    private LineReader lineReader;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean processing = new AtomicBoolean(false);

    public TerminalUI(App app) {
        this.app = app;
        this.commandRegistry = app.getCommandRegistry();
        this.agentManager = app.getAgentManager();
        this.mdRenderer = new MarkdownRenderer();
    }

    public void start() {
        try {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .dumb(false)
                    .jansi(true)
                    .build();

            lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .parser(new DefaultParser())
                    .completer(buildCompleter())
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .build();

            // Register Tab key binding for agent switching
            lineReader.getKeyMaps().get("main").bind(
                    new Reference("cycle-agent"), "\t");
            lineReader.getWidgets().put("cycle-agent", () -> {
                agentManager.cycleAgent();
                lineReader.callWidget(LineReader.REDRAW_LINE);
                return true;
            });

            running.set(true);
            printWelcome();
            mainLoop();
        } catch (IOException e) {
            System.err.println("Failed to initialize terminal: " + e.getMessage());
            System.exit(1);
        }
    }

    private void mainLoop() {
        while (running.get()) {
            try {
                String prompt = buildPrompt();
                String input = lineReader.readLine(prompt);

                if (input == null) {
                    // Ctrl+D
                    shutdown();
                    break;
                }

                input = input.trim();
                if (input.isEmpty()) continue;

                if (commandRegistry.isCommand(input)) {
                    String result = commandRegistry.executeCommand(input);
                    if (result != null) {
                        if (result.startsWith("__PROMPT__")) {
                            // Prompt-based command — send to active agent
                            String promptText = result.substring("__PROMPT__".length());
                            processUserMessage(promptText);
                        } else {
                            printSystem(result);
                        }
                    }
                } else {
                    processUserMessage(input);
                }
            } catch (UserInterruptException e) {
                if (processing.get()) {
                    printSystem("⚠️  Interrupted.");
                    processing.set(false);
                } else {
                    printSystem("Type /quit or Ctrl+D to exit.");
                }
            } catch (EndOfFileException e) {
                shutdown();
                break;
            }
        }
    }

    private void processUserMessage(String message) {
        processing.set(true);
        String agentName = agentManager.getActiveAgentName();
        printAgentHeader(agentName);

        // Show thinking indicator
        System.out.print(colorize("⏳ Thinking...", AttributedStyle.YELLOW));
        System.out.flush();
        final boolean[] firstToken = {true};

        try {
            String response = agentManager.processMessage(message, token -> {
                if (processing.get()) {
                    if (firstToken[0]) {
                        // Clear the "Thinking..." message
                        System.out.print("\r" + " ".repeat(40) + "\r");
                        firstToken[0] = false;
                    }
                    System.out.print(token);
                    System.out.flush();
                }
            });

            if (firstToken[0]) {
                // No tokens were streamed — print the full response directly
                System.out.print("\r" + " ".repeat(40) + "\r");
                if (response != null && !response.isEmpty()) {
                    System.out.println(response);
                } else {
                    printError("(No response received)");
                }
            } else {
                System.out.println(); // Final newline after streamed response
            }
        } catch (Exception e) {
            System.out.print("\r" + " ".repeat(40) + "\r");
            printError("Error: " + e.getMessage());
            e.printStackTrace(System.err);
        } finally {
            processing.set(false);
            printSeparator();
        }
    }

    private String buildPrompt() {
        String agent = agentManager.getActiveAgentName();
        String model = app.getProvider().getModelName();
        String shortModel = model.contains("/") ? model.substring(model.lastIndexOf('/') + 1) : model;
        String tokenStatus = UsageTracker.getInstance().getPromptStatus();

        AttributedStringBuilder builder = new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append("[")
                .append(agent)
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT))
                .append(":")
                .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW))
                .append(shortModel);

        if (!tokenStatus.isEmpty()) {
            builder.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.BRIGHT))
                    .append(":")
                    .style(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN))
                    .append(tokenStatus);
        }

        builder.style(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN))
                .append("]")
                .style(AttributedStyle.DEFAULT)
                .append(" > ");

        return builder.toAnsi();
    }

    private void printWelcome() {
        System.out.println();
        System.out.println(colorize("  ╔══════════════════════════════════════╗", AttributedStyle.CYAN));
        System.out.println(colorize("  ║     OpenCode Java v1.0.0            ║", AttributedStyle.CYAN));
        System.out.println(colorize("  ║     AI Coding Agent                 ║", AttributedStyle.CYAN));
        System.out.println(colorize("  ╚══════════════════════════════════════╝", AttributedStyle.CYAN));
        System.out.println();
        System.out.println("  Provider: " + colorize(app.getProvider().getProviderName(), AttributedStyle.GREEN));
        System.out.println("  Model:    " + colorize(app.getProvider().getModelName(), AttributedStyle.YELLOW));
        System.out.println("  Agent:    " + colorize(agentManager.getActiveAgentName(), AttributedStyle.MAGENTA));
        System.out.println("  Dir:      " + colorize(app.getConfig().getWorkingDirectory(), AttributedStyle.WHITE));
        System.out.println();
        System.out.println("  Type /help for commands, Tab to switch agents, Ctrl+D to quit.");
        printSeparator();
    }

    private void printAgentHeader(String agentName) {
        System.out.println();
        System.out.print(colorize("● " + agentName, AttributedStyle.MAGENTA));
        System.out.println(colorize(":", AttributedStyle.BRIGHT));
    }

    private void printSystem(String message) {
        System.out.println(colorize(message, AttributedStyle.BLUE));
    }

    private void printError(String message) {
        System.out.println(colorize(message, AttributedStyle.RED));
    }

    private void printSeparator() {
        System.out.println(colorize("─".repeat(Math.min(terminal.getWidth(), 60)), AttributedStyle.BRIGHT));
    }

    private String colorize(String text, int color) {
        return new AttributedStringBuilder()
                .style(AttributedStyle.DEFAULT.foreground(color))
                .append(text)
                .style(AttributedStyle.DEFAULT)
                .toAnsi();
    }

    public void shutdown() {
        running.set(false);
        app.getSessionManager().saveCurrentSession();
        System.out.println();
        printSystem("Session saved. Goodbye! 👋");
        try {
            if (terminal != null) terminal.close();
        } catch (IOException ignored) {}
    }

    public void displayMessage(String message) {
        System.out.println(message);
    }

    public boolean isRunning() {
        return running.get();
    }

    /**
     * Build a completer for tab completion supporting:
     * - Commands (starting with /)
     * - @agent names (starting with @)
     * - File paths from working directory
     */
    private Completer buildCompleter() {
        // Command completer
        List<String> commands = new ArrayList<>();
        commands.add("/help");
        commands.add("/clear");
        commands.add("/compact");
        commands.add("/quit");
        commands.add("/model");
        commands.add("/agents");
        commands.add("/remember");
        commands.add("/memory");
        commands.add("/usage");

        // Agent mention completer
        List<String> agentMentions = new ArrayList<>();
        for (String name : agentManager.getAgentNames()) {
            agentMentions.add("@" + name);
        }

        // File path completer
        Completer fileCompleter = (reader, line, candidates) -> {
            String word = line.word();
            if (word == null) word = "";

            File dir;
            String prefix;
            if (word.contains("/") || word.contains(File.separator)) {
                int lastSep = Math.max(word.lastIndexOf('/'), word.lastIndexOf(File.separatorChar));
                String dirPath = word.substring(0, lastSep + 1);
                prefix = word.substring(lastSep + 1);
                dir = new File(app.getConfig().getWorkingDirectory(), dirPath);
            } else {
                dir = new File(app.getConfig().getWorkingDirectory());
                prefix = word;
            }

            if (dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().startsWith(prefix) && !f.getName().startsWith(".")) {
                            String value = f.isDirectory() ? f.getName() + "/" : f.getName();
                            candidates.add(new Candidate(value));
                        }
                    }
                }
            }
        };

        return new AggregateCompleter(
                new StringsCompleter(commands),
                new StringsCompleter(agentMentions),
                fileCompleter
        );
    }
}
